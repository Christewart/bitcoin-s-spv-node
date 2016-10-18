package org.bitcoins.spvnode.networking

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.event.LoggingReceive
import akka.io.Tcp
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.DbConfig
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil

/**
  * Created by chris on 10/13/16.
  * This actor contains a pool of connections to peers on the bitcoin peer to peer network.
  */
sealed trait PeerConnectionPoolActor extends Actor with BitcoinSLogger {
  import PeerConnectionPoolActor._

  def addressManagerActor: ActorRef = AddressManagerActor(context)

  def dbConfig: DbConfig

  def receive: Receive = LoggingReceive {
    case x =>
      context.become(pooledReceived(Nil,Nil))
      self.forward(x)
  }

  def pooledReceived(peerPool: Seq[ActorRef], outstandingRequest: Seq[ActorRef]): Receive = LoggingReceive {
    case GetPeer =>
      if (peerPool.isEmpty) {
        logger.info("We don't have any connections to peers in our pool, creating one now")
        //since we don't have a pool of peers yet, we need to create one.
        //first ask for a peer's address to connect to
        addressManagerActor ! AddressManagerActor.GetRandomAddress
        context.become(pooledReceived(Nil, sender +: outstandingRequest))
      } else {
        sender ! GetPeerReply(peerPool.head)
      }
    case addressReply : AddressManagerActor.GetRandomAddressReply =>
      val client = Client(context,dbConfig)
      client ! Tcp.Connect(addressReply.socket)
    case addPeer: AddPeer =>
      logger.debug("Outstanding requests: " + outstandingRequest)
      outstandingRequest.map{ o =>
        logger.debug("Fulfilling peer request")
        o ! GetPeerReply(addPeer.peer)
      }
      logger.info("Switching contexts to poolReceived with no outstanding requests")
      context.become(pooledReceived(addPeer.peer +: peerPool, Nil))
  }
}


object PeerConnectionPoolActor extends BitcoinSLogger {


  private case class PeerConnectionPoolActorImpl(dbConfig: DbConfig) extends PeerConnectionPoolActor

  def props(dbConfig: DbConfig): Props = Props(classOf[PeerConnectionPoolActorImpl], dbConfig)

  //TODO: Figure out of there is a better way to cache a reference to this actor
  private var cachedConnectionPool: Option[ActorRef] = None

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    if (cachedConnectionPool.isDefined) {
      logger.info("Returning cached connection pool")
      cachedConnectionPool.get
    }
    else {
      logger.info("Creating new connection pool")
      val newPool = context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
      cachedConnectionPool = Some(newPool)
      newPool
    }
  }



  sealed trait PeerConnectionPoolActorMessage
  sealed trait PeerConnectionPoolActorRequest
  sealed trait PeerConnectionPoolActorReply

  case object GetPeer extends PeerConnectionPoolActorRequest

  case class GetPeerReply(peer: ActorRef) extends PeerConnectionPoolActorReply

  /** Adds the given peer to the connection pool */
  case class AddPeer(peer: ActorRef) extends PeerConnectionPoolActorRequest
}