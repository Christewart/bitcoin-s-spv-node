package org.bitcoins.spvnode.networking

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorRefFactory, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.io.Tcp
import akka.util.ByteString
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.messages.{NetworkPayload, PingMessage, VerAckMessage, VersionMessage}
import org.bitcoins.spvnode.messages.control.{PongMessage, VersionMessage}
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.constant.Constants
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil

/**
  * Created by chris on 10/13/16.
  * It exchanges [[VersionMessage]], [[VerAckMessage]] and [[PingMessage]]/[[PongMessage]] message
  * with our peer on the network. When the Client finally responds to the [[NetworkMessage]] we originally
  * sent it sends that [[NetworkMessage]] back to the actor that requested it.
  */
sealed trait PeerConnectionFSMActor extends Actor with BitcoinSLogger {
  import PeerConnectionFSMActor._
  def receive: Receive = LoggingReceive {
    case request: PeerConnectionFSMActorRequest =>
      handleRequest(request,sender)
  }

  def handleRequest(request: PeerConnectionFSMActorRequest, sender: ActorRef): Unit = request match {
    case connectToPeer: ConnectToPeer =>
      val peer: ActorRef = Client(context)
      peer ! Tcp.Connect(connectToPeer.socket)
      context.become(awaitConnected(peer,Nil))
  }

  /** Waits for us to receive a [[Tcp.Connected]] message from our [[Client]] */
  def awaitConnected(peer: ActorRef, unalignedBytes: Seq[Byte]): Receive = LoggingReceive {
    case Tcp.Connected(remote,local) =>
      val versionMsg = VersionMessage(Constants.networkParameters,local.getAddress)
      peer ! versionMsg
      logger.info("Switching to awaitVersionMessage from awaitConnected")
      context.become(awaitVersionMessage(peer,unalignedBytes))
    case msg: Tcp.Message =>
      val newUnalignedBytes = handleTcpMessage(msg, unalignedBytes,sender, peer)
      context.become(awaitConnected(peer,newUnalignedBytes))
  }

  /** Waits for a peer on the network to send us a [[VersionMessage]] */
  private def awaitVersionMessage(peer: ActorRef, unalignedBytes: Seq[Byte]): Receive = LoggingReceive {
    case networkMessage : NetworkMessage => networkMessage.payload match {
      case _ : VersionMessage =>
        peer ! VerAckMessage
        //need to wait for the peer to send back a verack message
        logger.debug("Switching to awaitVerack from awaitVersionMessage")
        context.become(awaitVerack(peer,unalignedBytes))
      case msg : NetworkPayload =>
        context.become(awaitVersionMessage(peer, unalignedBytes))
    }
    case msg: Tcp.Message =>
      val newUnalignedBytes = handleTcpMessage(msg, unalignedBytes,sender, peer)
      context.become(awaitVersionMessage(peer,newUnalignedBytes))
    case payload: NetworkPayload =>
      self ! NetworkMessage(Constants.networkParameters,payload)
  }

  /** Waits for our peer on the network to send us a [[VerAckMessage]] */
  private def awaitVerack(peer: ActorRef, unalignedBytes: Seq[Byte]): Receive = LoggingReceive {
    case networkMessage : NetworkMessage => networkMessage.payload match {
      case VerAckMessage =>
/*
        val controlPayloads = findControlPayloads(requests)
        context.become(peerMessageHandler(controlPayloads, unalignedBytes))*/
        //we have finished establishing the connection to our peer, we can now send normal messages
        context.parent ! ConnectToPeerReply(Some(peer))
        sender ! PoisonPill
      case p : NetworkPayload =>
        logger.info("Receiving unexpected network paylaod when waiting for verack message: " + p)
    }
    case msg: Tcp.Message =>
      val newUnalignedBytes = handleTcpMessage(msg,unalignedBytes,sender, peer)
      context.become(awaitVerack(peer,newUnalignedBytes))
    case payload: NetworkPayload =>
      self ! NetworkMessage(Constants.networkParameters,payload)
  }

  /**
    * This function is responsible for handling a [[Tcp.Command]] algebraic data type
    * @param command
    */
  private def handleCommand(command : Tcp.Command, peer: ActorRef) = command match {
    case close @ (Tcp.ConfirmedClose | Tcp.Close | Tcp.Abort) =>
      peer ! close
  }

  private def handleTcpMessage(message: Tcp.Message, unalignedBytes: Seq[Byte], sender: ActorRef, peer: ActorRef): Seq[Byte] = message match {
    case event: Tcp.Event => handleEvent(event, unalignedBytes,sender)
    case command: Tcp.Command =>
      handleCommand(command,peer)
      Nil
  }

  /**
    * This function is responsible for handling a [[Tcp.Event]] algebraic data type
    * @param event the event that needs to be handled
    * @param unalignedBytes the unaligned bytes from previous tcp frames
    *                       These can be used to construct a full message, since the last frame could
    *                       have transmitted the first half of the message, and this frame transmits the
    *                       rest of the message
    * @return the new unaligned bytes, if there are any
    */
  private def handleEvent(event : Tcp.Event, unalignedBytes: Seq[Byte], sender: ActorRef): Seq[Byte] = event match {
    case Tcp.Received(byteString: ByteString) =>
      //logger.debug("Received byte string in peerMessageHandler " + BitcoinSUtil.encodeHex(byteString.toArray))
      //logger.debug("Unaligned bytes: " + BitcoinSUtil.encodeHex(unalignedBytes))
      //this means that we receive a bunch of messages bundled into one [[ByteString]]
      //need to parse out the individual message
      val bytes: Seq[Byte] = unalignedBytes ++ byteString.toArray.toSeq
      //logger.debug("Bytes for message parsing: " + BitcoinSUtil.encodeHex(bytes))
      val (messages,remainingBytes) = BitcoinSpvNodeUtil.parseIndividualMessages(bytes)
      for {m <- messages} yield self.tell(m,sender)
      remainingBytes
    case x @ (_ : Tcp.CommandFailed | _ : Tcp.Received | _ : Tcp.Connected) =>
      logger.debug("Received TCP event the peer message handler actor should not have received: " + x)
      unalignedBytes
    case Tcp.PeerClosed =>
      self ! PoisonPill
      unalignedBytes
    case closed @ (Tcp.ConfirmedClosed | Tcp.Closed | Tcp.Aborted | Tcp.PeerClosed) =>
      context.parent ! closed
      self ! PoisonPill
      unalignedBytes
  }

}


object PeerConnectionFSMActor {

  private case class PeerConnectionFSMActorImpl() extends PeerConnectionFSMActor

  def props: Props = Props(classOf[PeerConnectionFSMActorImpl])

  def apply(context: ActorRefFactory): ActorRef = context.actorOf(props, BitcoinSpvNodeUtil.createActorName(this.getClass))

  sealed trait PeerConnectionFSMActorMessage
  sealed trait PeerConnectionFSMActorRequest
  sealed trait PeerConnectionFSMActorReply

  case class ConnectToPeer(socket: InetSocketAddress) extends PeerConnectionFSMActorRequest

  case class ConnectToPeerReply(peer: Option[ActorRef]) extends PeerConnectionFSMActorReply


}
