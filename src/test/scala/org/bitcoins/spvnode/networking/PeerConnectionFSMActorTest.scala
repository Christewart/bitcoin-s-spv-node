package org.bitcoins.spvnode.networking

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.Tcp
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.constant.Constants
import org.bitcoins.spvnode.messages.VersionMessage
import org.bitcoins.spvnode.messages.control.VersionMessage
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.duration.DurationInt
/**
  * Created by chris on 10/13/16.
  */
class PeerConnectionFSMActorTest  extends TestKit(ActorSystem("PeerConnectionFSMActorTest")) with FlatSpecLike
  with MustMatchers with ImplicitSender
  with BeforeAndAfter with BeforeAndAfterAll with BitcoinSLogger  {


  val local = new InetSocketAddress(18333)

/*  "PeerConnectionFSMActor" must "send the sender a version message if we send it a version message" in {
    val (peerConnectionFSM, probe) = TestUtil.peerConnectFSMActorRef(system,local)
    val transmittingIpAddress = new InetSocketAddress(Constants.networkParameters.dnsSeeds(2),
      Constants.networkParameters.port)
    val versionMsg = VersionMessage(Constants.networkParameters,local.getAddress,transmittingIpAddress.getAddress)
    peerConnectionFSM ! versionMsg
    val networkMsg = expectMsgType[ByteString]
    //networkMsg.payload.isInstanceOf[VersionMessage] must be (true)
  }*/




}
