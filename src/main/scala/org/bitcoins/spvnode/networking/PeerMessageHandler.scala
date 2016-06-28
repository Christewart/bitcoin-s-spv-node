package org.bitcoins.spvnode.networking

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp
import org.bitcoins.core.util.BitcoinSLogger

/**
  * Created by chris on 6/7/16.
  */
trait PeerMessageHandler extends Actor with BitcoinSLogger {
  def receive = {
    case message : Tcp.Message => message match {
      case event : Tcp.Event => handleEvent(event)
      case command : Tcp.Command => handleCommand(command)
    }
    case msg => throw new IllegalArgumentException("Unknown message inside of PeerMessageHandler: " + msg)
  }



  /**
    * This function is responsible for handling a [[Tcp.Event]] algebraic data type
    * @param event
    */
  private def handleEvent(event : Tcp.Event) = event match {
    case Tcp.CommandFailed(w: Tcp.Write) =>
      logger.debug("Peer message Handler command failed: " + Tcp.CommandFailed(w))
      logger.debug("O/S buffer was full")
      // O/S buffer was full
      //listener ! "write failed"
    case Tcp.CommandFailed(command) =>
      logger.debug("PeerMessageHandler command failed: " + command)
    case Tcp.Received(data) =>
      logger.debug("Received data from our peer on the network: " + Tcp.Received(data))
      //listener ! data
    case Tcp.Connected(remote, local) =>
      logger.debug("Tcp connection to: " + remote)
      logger.debug("Local: " + local)
      //listener ! Tcp.Connected(remote,local)
      //peer = Some(sender)
      //peer.get ! Tcp.Register(listener)
    case Tcp.ConfirmedClosed =>
      logger.debug("Peer Message Handler received confirmed closed msg: " + Tcp.ConfirmedClosed)
      //peer = None
      //context stop self
    case Tcp.PeerClosed =>
      logger.debug("Peer closed on network")
      context stop self
  }
  /**
    * This function is responsible for handling a [[Tcp.Command]] algebraic data type
    * @param command
    */
  private def handleCommand(command : Tcp.Command) = command match {
    case Tcp.ConfirmedClose =>
      logger.debug("Peer Message Handler received connection closed msg: " + Tcp.ConfirmedClose)
      //listener ! Tcp.ConfirmedClose
      //peer.get ! Tcp.ConfirmedClose
  }
}




object PeerMessageHandler {
  private case class PeerMessageHandlerImpl() extends PeerMessageHandler

  def apply(actorSystem : ActorSystem) : ActorRef = actorSystem.actorOf(Props(PeerMessageHandlerImpl()))
}