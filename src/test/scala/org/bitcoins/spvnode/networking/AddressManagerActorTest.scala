package org.bitcoins.spvnode.networking

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}

/**
  * Created by chris on 10/12/16.
  */
class AddressManagerActorTest  extends TestKit(ActorSystem("AddressManagerActorTest")) with FlatSpecLike
  with MustMatchers with ImplicitSender
  with BeforeAndAfter with BeforeAndAfterAll with BitcoinSLogger  {

  "AddressManagerActor" must "get a address from persistent storage" in {
    val (addressManagerActor, probe) = TestUtil.addressManagerActorRef(system)
    addressManagerActor ! AddressManagerActor.GetRandomAddress
    expectMsgType[AddressManagerActor.GetRandomAddressReply]
  }

}
