package org.bitcoins.spvnode.networking

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.event.LoggingReceive
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.constant.{Constants, DbConfig}
import org.bitcoins.spvnode.messages._
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
/**
  * Created by chris on 6/7/16.
  * This actor simply is a proxy between [[Client]] and higher level actors such as [[BlockActor]]
  * It hides the problem of requesting a peer from the connection pool to send the message to on the p2p network
  */
sealed trait PeerMessageHandler extends Actor with BitcoinSLogger {

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
      context.become(receive)
      //send all requests
      sendPeerRequests(requests,peer)
      context.become(receive)
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
}



object PeerMessageHandler {
  private case class PeerMessageHandlerImpl(dbConfig: DbConfig) extends PeerMessageHandler

  def props(dbConfig: DbConfig): Props = Props(classOf[PeerMessageHandlerImpl], dbConfig)

  def apply(context : ActorRefFactory,dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }

}

