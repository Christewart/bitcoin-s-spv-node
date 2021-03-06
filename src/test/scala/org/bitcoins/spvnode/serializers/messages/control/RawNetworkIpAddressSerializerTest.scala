package org.bitcoins.spvnode.serializers.messages.control

import org.bitcoins.core.number.UInt32
import org.bitcoins.core.util.BitcoinSUtil
import org.bitcoins.spvnode.messages.control.NodeNetwork
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 6/2/16.
  */
class RawNetworkIpAddressSerializerTest extends FlatSpec with MustMatchers {

  //from this bitcoin developer guide example
  //https://bitcoin.org/en/developer-reference#addr
  val time = "d91f4854"
  val services = "0100000000000000"
  val address = "00000000000000000000ffffc0000233"
  val port = "208d"
  val hex = time + services + address + port
  "RawNetworkIpAddressSerializer" must "read a network ip address from a hex string" in {
    val ipAddress = RawNetworkIpAddressSerializer.read(hex)
    ipAddress.time must be (UInt32(1414012889))
    ipAddress.services must be (NodeNetwork)
    ipAddress.address.toString must be ("/192.0.2.51")
    ipAddress.port must be (8333)
  }

  it must "write a network ip address from and get its original hex back" in {
    val ipAddress = RawNetworkIpAddressSerializer.read(hex)
    BitcoinSUtil.encodeHex(RawNetworkIpAddressSerializer.write(ipAddress)) must be (hex)
  }
}
