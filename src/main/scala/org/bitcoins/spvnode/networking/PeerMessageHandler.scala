package org.bitcoins.spvnode.networking

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorRefFactory, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.io.Tcp
import akka.util.ByteString
import org.bitcoins.core.util.{BitcoinSLogger, BitcoinSUtil}
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.constant.{Constants, DbConfig}
import org.bitcoins.spvnode.messages.control.{PongMessage, VersionMessage}
import org.bitcoins.spvnode.messages.{GetAddrMessage, VerAckMessage, _}
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
import org.bitcoins.spvnode.networking.PeerConnectionPoolActor

/**
  * Created by chris on 6/7/16.
  * This actor is the middle man between our [[Client]] and higher level actors such as
  * [[BlockActor]]. When it receives a message, it tells [[Client]] to create connection to a peer,
  *
  */
sealed trait PeerMessageHandler extends Actor with BitcoinSLogger {

  //lazy val peer: ActorRef = context.actorOf(Client.props,BitcoinSpvNodeUtil.createActorName(this.getClass))

  def dbConfig: DbConfig

  def peerConnectionPoolActor: ActorRef = PeerConnectionPoolActor(context,dbConfig)

  def receive = LoggingReceive {
    case msg: NetworkMessage =>
      logger.info("Switching to awaitConnected from default receive")
      peerConnectionPoolActor ! PeerConnectionPoolActor.GetPeer
      context.become(awaitGetPeerReply(Seq((sender,msg)), Nil))
    case networkPayload: NetworkPayload =>
      self.forward(NetworkMessage(Constants.networkParameters,networkPayload))
  }

  /** Awaits for a [[PeerConnectionPoolActor.GetPeerReply]] from [[PeerConnectionPoolActor]] */
  def awaitGetPeerReply(requests: Seq[(ActorRef,NetworkMessage)], unalignedBytes: Seq[Byte]): Receive = LoggingReceive {
    case peerReply: PeerConnectionPoolActor.GetPeerReply =>
      val peer = peerReply.peer
      context.become(peerMessageHandler(peer,Nil,Nil))
      //send all requests
      sendPeerRequests(requests,peer)
    case msg: NetworkMessage =>
      logger.debug("Received another peer request while waiting for Tcp.Connected: " + msg)
      context.become(awaitGetPeerReply((sender,msg) +: requests, unalignedBytes))
    case payload: NetworkPayload =>
      self ! NetworkMessage(Constants.networkParameters,payload)
  }
  /**
    * Sends all of the given [[NetworkMessage]] to our peer on the p2p network
    * @param requests
    * @return
    */
  private def sendPeerRequests(requests: Seq[(ActorRef,NetworkMessage)], peer: ActorRef) = for {
    (sender,peerRequest) <- requests
  } yield {
    logger.info("Sending queued peer request " + peerRequest + " to " + peer)
    peer ! peerRequest
  }

  /**
    * This is the main receive function inside of [[PeerMessageHandler]]
    * This will receive peer requests, then send the payload to the the corresponding
    * actor responsible for handling that specific message
    * @return
    */
  def peerMessageHandler(peer: ActorRef, controlMessages: Seq[(ActorRef,ControlPayload)], unalignedBytes: Seq[Byte]) : Receive = LoggingReceive {
    case networkMessage: NetworkMessage =>
      peer ! networkMessage
    case payload: NetworkPayload =>
      val networkMsg = NetworkMessage(Constants.networkParameters, payload)
      self ! networkMsg
  }

  /**
    * Finds all control payloads inside of a given sequence of requests
    * @param requests
    * @return
    */
  private def findControlPayloads(requests: Seq[(ActorRef,NetworkMessage)]): Seq[(ActorRef,ControlPayload)] = {
    val controlPayloads = requests.filter { case (sender,msg) => msg.payload.isInstanceOf[ControlPayload] }
    controlPayloads.map { case (sender, msg) => (sender, msg.payload.asInstanceOf[ControlPayload]) }
  }
}



object PeerMessageHandler {
  private case class PeerMessageHandlerImpl(dbConfig: DbConfig) extends PeerMessageHandler

  def props(dbConfig: DbConfig): Props = Props(classOf[PeerMessageHandlerImpl], dbConfig)

  def apply(context : ActorRefFactory,dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }

}

