package org.bitcoins.spvnode.networking

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.event.LoggingReceive
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil

/**
  * Created by chris on 10/13/16.
  * This actor contains a pool of connections to peers on the bitcoin peer to peer network.
  */
sealed trait PeerConnectionPoolActor extends Actor with BitcoinSLogger {
  import PeerConnectionPoolActor._

  def addressManagerActor: ActorRef = AddressManagerActor(context)

  def peerConnectionFSMActor: ActorRef = PeerConnectionFSMActor(context)

  def receive: Receive = LoggingReceive {
    case GetPeer =>
      //since we don't have a pool of peers yet, we need to create one.
      //first ask for a peer's address to connect to
      addressManagerActor ! AddressManagerActor.GetRandomAddress
      context.become(awaitGetRandomAddressReply(sender))
  }

  def awaitGetRandomAddressReply(originalSender: ActorRef): Receive = LoggingReceive {
    case addressReply : AddressManagerActor.GetRandomAddressReply =>
      //now connect to that peer with our PeerConnectionFSMActor
      peerConnectionFSMActor ! PeerConnectionFSMActor.ConnectToPeer(addressReply.socket)
      context.become(awaitConnectToPeerReply(originalSender))
  }

  def awaitConnectToPeerReply(originalSender: ActorRef): Receive = LoggingReceive {
    case connectToPeerReply: PeerConnectionFSMActor.ConnectToPeerReply =>
      //we now have a valid connection, send it back to the original sender and add it to the connection pool
      if (connectToPeerReply.peer.isDefined) {
        val p = connectToPeerReply.peer.get
        //means we successfully connection, send it back to the sender
        originalSender ! GetPeerReply(p)
        context.become(awaitConnectionPoolRequest(Seq(p)))
      }
  }

  def awaitConnectionPoolRequest(pool: Seq[ActorRef]): Receive = LoggingReceive {
    case GetPeer =>
      sender ! GetPeerReply(pool.head)
  }
}


object PeerConnectionPoolActor {


  private case class PeerConnectionPoolActorImpl() extends PeerConnectionPoolActor

  def props: Props = Props(classOf[PeerConnectionPoolActorImpl])

  //TODO: Figure out of there is a better way to cache a reference to this actor
  private var cachedConnectionPool: Option[ActorRef] = None

  def apply(context: ActorRefFactory): ActorRef = {
    if (cachedConnectionPool.isDefined) cachedConnectionPool.get
    else {
      val newPool = context.actorOf(props, BitcoinSpvNodeUtil.createActorName(this.getClass))
      cachedConnectionPool = Some(newPool)
      newPool
    }
  }



  sealed trait PeerConnectionPoolActorMessage
  sealed trait PeerConnectionPoolActorRequest
  sealed trait PeerConnectionPoolActorReply

  case object GetPeer extends PeerConnectionPoolActorRequest

  case class GetPeerReply(peer: ActorRef) extends PeerConnectionPoolActorReply
}