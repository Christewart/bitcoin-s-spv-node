package org.bitcoins.spvnode.networking

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.event.LoggingReceive
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.Constants
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil

/**
  * Created by chris on 10/12/16.
  * Responsible for managing the addresses we have saved. We use these addresses
  * to connect to peers when we start up the p2p network. This actor also saves addresses
  * when we receive a [[org.bitcoins.spvnode.messages.AddrMessage]], we can forward it to
  * this actor which will save the new addresses in persistent storage. We can those use those
  * new addresses to connect to the p2p network on next restart. This trait is meant to ressemble
  * bitcoin core's AddrMan class
  * [[https://github.com/bitcoin/bitcoin/blob/2f71490d21796594ca6f55e375558944de9db5a0/src/addrman.h#L177]]
  */
sealed trait AddressManagerActor extends Actor with BitcoinSLogger {

  def receive: Receive = LoggingReceive {
    case request: AddressManagerActor.AddressManagerActorRequest => handleRequests(request, sender)
  }

  def handleRequests(request: AddressManagerActor.AddressManagerActorRequest, sender: ActorRef): Unit = request match {
    case AddressManagerActor.GetRandomAddress =>
      val seed = new InetSocketAddress(Constants.networkParameters.dnsSeeds(2), Constants.networkParameters.port)
      sender ! AddressManagerActor.GetRandomAddressReply(seed)

  }
}


object AddressManagerActor {

  private case class AddressManagerActorImpl() extends AddressManagerActor

  def props = Props(classOf[AddressManagerActorImpl])

  def apply(context: ActorRefFactory): ActorRef = context.actorOf(props, BitcoinSpvNodeUtil.createActorName(this.getClass))

  sealed trait AddressManagerActorMessage
  sealed trait AddressManagerActorRequest extends AddressManagerActorMessage
  sealed trait AddressManagerActorReply extends AddressManagerActorMessage

  case object GetRandomAddress extends AddressManagerActorRequest
  case class GetRandomAddressReply(socket: InetSocketAddress) extends AddressManagerActorMessage
}
