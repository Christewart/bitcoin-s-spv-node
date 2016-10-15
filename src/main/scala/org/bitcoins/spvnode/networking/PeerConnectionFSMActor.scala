package org.bitcoins.spvnode.networking

/**
  * Created by chris on 10/13/16.
  * It exchanges [[VersionMessage]], [[VerAckMessage]] and [[PingMessage]]/[[PongMessage]] message
  * with our peer on the network. When the Client finally responds to the [[NetworkMessage]] we originally
  * sent it sends that [[NetworkMessage]] back to the actor that requested it.
  */
/*
sealed trait PeerConnectionFSMActor extends Actor with BitcoinSLogger {
  import PeerConnectionFSMActor._

  /** The address that we are bound to locally, this is where our peers send messages to */
  def localAddress: InetSocketAddress

  def receive: Receive = LoggingReceive {
    case payload: ControlPayload =>
      val networkMsg = payload match {
        case versionMsg: VersionMessage =>
          ???
        case VerAckMessage =>
          //add peer to the connection pool
          val peerConnectionPoolActor = PeerConnectionPoolActor(context)
          peerConnectionPoolActor ! PeerConnectionPoolActor.AddPeer(sender)
          NetworkMessage(Constants.networkParameters,VerAckMessage)
      }

      //assumes the sender is the original sender of the message aka it is forwarded from Client
      //preserving the original sender
      sender ! networkMsg
  }
}


object PeerConnectionFSMActor {

  private case class PeerConnectionFSMActorImpl(localAddress: InetSocketAddress) extends PeerConnectionFSMActor

  def props(localAddress: InetSocketAddress): Props = Props(classOf[PeerConnectionFSMActorImpl], localAddress)

  def apply(context: ActorRefFactory, localAddress: InetSocketAddress): ActorRef = context.actorOf(props(localAddress),
    BitcoinSpvNodeUtil.createActorName("PeerConnectionFSMActor"))

  sealed trait PeerConnectionFSMActorMessage
  sealed trait PeerConnectionFSMActorRequest
  sealed trait PeerConnectionFSMActorReply

  case class ConnectToPeer(socket: InetSocketAddress) extends PeerConnectionFSMActorRequest

  case class ConnectToPeerReply(peer: Option[ActorRef]) extends PeerConnectionFSMActorReply


}
*/
