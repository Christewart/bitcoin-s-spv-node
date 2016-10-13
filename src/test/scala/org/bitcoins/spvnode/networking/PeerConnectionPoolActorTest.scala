package org.bitcoins.spvnode.networking

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import scala.concurrent.duration.DurationInt
/**
  * Created by chris on 10/13/16.
  */
class PeerConnectionPoolActorTest  extends TestKit(ActorSystem("AddressManagerActorTest")) with FlatSpecLike
  with MustMatchers with ImplicitSender
  with BeforeAndAfter with BeforeAndAfterAll with BitcoinSLogger {


  "PeerConnectionPoolActor" must "return a connection to a peer on the p2p network"  in {
    val (peerConnectionPoolActor,probe) = TestUtil.peerConnectionPoolRef(system)
    peerConnectionPoolActor ! PeerConnectionPoolActor.GetPeer
    val peer = expectMsgType[PeerConnectionPoolActor.GetPeerReply](10.seconds)

  }
}
