package org.bitcoins.spvnode.networking

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorContext, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.bitcoins.core.config.NetworkParameters
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.constant.{Constants, DbConfig}
import org.bitcoins.spvnode.messages._
import org.bitcoins.spvnode.messages.control.{PingMessage, PongMessage, VersionMessage}
import org.bitcoins.spvnode.models.BlockHeaderDAO
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
/**
  * Created by chris on 6/6/16.
  * This actor is responsible for creating a connection,
  * relaying messages and closing a connection to our peer on
  * the p2p network
  */
sealed trait Client extends Actor with BitcoinSLogger {


  def dbConfig: DbConfig

  /**
    * The manager is an actor that handles the underlying low level I/O resources (selectors, channels)
    * and instantiates workers for specific tasks, such as listening to incoming connections.
    */
  def manager : ActorRef = IO(Tcp)(context.system)

  /**
    * The parameters for the network we are connected to
    * i.e. [[org.bitcoins.core.config.MainNet]] or [[org.bitcoins.core.config.TestNet3]]
    *
    * @return
    */
  def network : NetworkParameters = Constants.networkParameters

  var localAddress: Option[InetSocketAddress] = None

  /** This context is responsible for initializing a tcp connection with a peer on the bitcoin p2p network */
  def receive = LoggingReceive {
    case connect: Tcp.Connect =>
      handleCommand(connect,None)
    case Tcp.Connected(remote,local) =>
      logger.debug("Tcp connection to: " + remote)
      logger.debug("Local: " + local)
      sender ! Tcp.Register(self)
      localAddress = Some(local)
      context.parent ! Tcp.Connected(remote,local)

      val versionMsg = VersionMessage(Constants.networkParameters,local.getAddress)
      val networkMsg = NetworkMessage(Constants.networkParameters, versionMsg)
      sendNetworkMessage(networkMsg,sender)
      context.become(awaitNetworkMessage(sender,Nil))

  }

  /**
    * This actor signifies the node we are connected to on the p2p network
    * This is the context we are in after we received a [[Tcp.Connected]] message
    */
  private def awaitNetworkMessage(peer: ActorRef, unAlignedBytes: Seq[Byte]): Receive = LoggingReceive {
    case payload: NetworkPayload =>
      handlePayload(payload, sender)
    case message: NetworkMessage =>
      self ! message.payload
    case message: Tcp.Message =>
      val (msgs, newUnalignedBytes) = handleTcpMessage(message,unAlignedBytes,Some(peer),sender)
      //send all parsed messages to myself
      context.become(awaitNetworkMessage(peer, newUnalignedBytes))
      msgs.map(m => self ! m)
  }

  /** Handles messages that we received from a peer, determines what we need to do with them based on if
    * they are a [[DataPayload]] or a [[ControlPayload]] message
    *
    * @param payload
    */
  private def handlePayload(payload: NetworkPayload, peer: ActorRef) : Unit =  payload match {
    case dataPayload: DataPayload => handleDataPayload(dataPayload,peer)
    case controlPayload: ControlPayload => handleControlPayload(controlPayload, peer)
  }

  private def handleDataPayload(payload: DataPayload, peer: ActorRef): Unit = payload match {
    case getHeadersMsg: GetHeadersMessage =>
      val networkMsg = NetworkMessage(Constants.networkParameters, getHeadersMsg)
      sendNetworkMessage(networkMsg,peer)
    case headersMsg: HeadersMessage =>
      val blockHeaderDAO = BlockHeaderDAO(context,dbConfig)
      blockHeaderDAO ! BlockHeaderDAO.CreateAll(headersMsg.headers)

  }


  private def handleControlPayload(payload: ControlPayload, peer: ActorRef): Unit = payload match {
    case _ : VersionMessage =>
      peer ! VerAckMessage
    case VerAckMessage =>
      logger.info("Connection process was successful, can now send message back and forth on the p2p network")
      val peerConnectionPoolActor = PeerConnectionPoolActor(context,dbConfig)
      peerConnectionPoolActor ! PeerConnectionPoolActor.AddPeer(peer)
    case addrMsg: AddrMessage =>
      val createMsg = AddressManagerActor.Create(addrMsg.addresses)
      val addressManagerActor = AddressManagerActor(context)
      addressManagerActor ! createMsg
      addressManagerActor ! PoisonPill
    case p : PingMessage =>
      val pongMsg = PongMessage(p.nonce)
      val networkMsg = NetworkMessage(Constants.networkParameters, pongMsg)
      sendNetworkMessage(networkMsg,peer)
  }


  /**
    * Handles boiler plate [[Tcp.Message]] types
    *
    * @param message
    * @return
    */
  private def handleTcpMessage(message: Tcp.Message, unAlignedBytes: Seq[Byte], peer: Option[ActorRef],
                               originalSender: ActorRef): (Seq[NetworkMessage], Seq[Byte]) = message match {
    case event: Tcp.Event => handleEvent(event, unAlignedBytes, originalSender)
    case command: Tcp.Command =>
      handleCommand(command,peer)
      (Nil,Nil)
  }

  /**
    * This function is responsible for handling a [[Tcp.Event]] algebraic data type
    *
    * @param event the event that needs to be handled
    * @param unalignedBytes the unaligned bytes from previous tcp frames
    *                       These can be used to construct a full message, since the last frame could
    *                       have transmitted the first half of the message, and this frame transmits the
    *                       rest of the message
    * @return the new unaligned bytes, if there are any
    */
  private def handleEvent(event : Tcp.Event, unalignedBytes: Seq[Byte], originalSender: ActorRef): (Seq[NetworkMessage], Seq[Byte]) = event match {
    case Tcp.Received(byteString: ByteString) =>
      //logger.debug("Received byte string in peerMessageHandler " + BitcoinSUtil.encodeHex(byteString.toArray))
      //logger.debug("Unaligned bytes: " + BitcoinSUtil.encodeHex(unalignedBytes))
      //this means that we receive a bunch of messages bundled into one [[ByteString]]
      //need to parse out the individual message
      val bytes: Seq[Byte] = unalignedBytes ++ byteString.toArray.toSeq
      //logger.debug("Bytes for message parsing: " + BitcoinSUtil.encodeHex(bytes))
      val (messages,remainingBytes) = BitcoinSpvNodeUtil.parseIndividualMessages(bytes)
      (messages,remainingBytes)
    case Tcp.Connected(remote,local) =>
      (Nil,unalignedBytes)
    case x : Tcp.CommandFailed =>
      logger.debug("Received TCP event the peer message handler actor should not have received: " + x)
      (Nil,unalignedBytes)
    case Tcp.PeerClosed =>
      self ! PoisonPill
      (Nil, unalignedBytes)
    case closed @ (Tcp.ConfirmedClosed | Tcp.Closed | Tcp.Aborted | Tcp.PeerClosed) =>
      context.parent ! closed
      self ! PoisonPill
      (Nil, unalignedBytes)
  }


  /**
    * This function is responsible for handling a [[Tcp.Command]] algebraic data type
    *
    * @param command
    */
  private def handleCommand(command : Tcp.Command, peer: Option[ActorRef]) = command match {
    case closeCmd @ (Tcp.ConfirmedClose | Tcp.Close | Tcp.Abort) =>
      peer.map(p => p ! closeCmd)
    case connectCmd : Tcp.Connect =>
      manager ! connectCmd
    case bind: Tcp.Bind =>
      manager ! bind
    case write: Tcp.Write =>
      peer.get ! write
  }

  /**
    * Sends a network request to our peer on the network
    *
    * @param message
    * @return
    */
  private def sendNetworkMessage(message : NetworkMessage, peer: ActorRef) = {
    val byteMessage = BitcoinSpvNodeUtil.buildByteString(message.bytes)
    logger.debug("Network message: " + message)
    peer ! Tcp.Write(byteMessage)
  }

}




object Client {
  private case class ClientImpl(dbConfig: DbConfig) extends Client

  def props(dbConfig: DbConfig): Props = Props(classOf[ClientImpl], dbConfig)

  def apply(context: ActorContext,dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName("Client"))
  }
}

