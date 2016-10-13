package org.bitcoins.spvnode.networking

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.bitcoins.core.config.TestNet3
import org.bitcoins.core.protocol.blockchain.TestNetChainParams
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import scala.concurrent.duration.DurationInt
/**
  * Created by chris on 10/13/16.
  */
class PeerConnectionFSMActorTest  extends TestKit(ActorSystem("PeerConnectionFSMActorTest")) with FlatSpecLike
  with MustMatchers with ImplicitSender
  with BeforeAndAfter with BeforeAndAfterAll with BitcoinSLogger  {


  val socket = TestUtil.dnsSeed

  "PeerConnectionFSMActor" must "connect to a peer on the bitcoin network" in {
    val (peerConnectionFSMActor,probe) = TestUtil.peerConnectFSMActorRef(system)
    peerConnectionFSMActor ! PeerConnectionFSMActor.ConnectToPeer(socket)
    val connectToPeerReply = probe.expectMsgType[PeerConnectionFSMActor.ConnectToPeerReply](10.seconds)

    connectToPeerReply.peer.isDefined must be (true)
  }

}
