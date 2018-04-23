package org.bitcoins.spvnode.serializers.messages.control

import org.bitcoins.core.number.UInt64
import org.bitcoins.core.protocol.CompactSizeUInt
import org.bitcoins.core.util.BitcoinSUtil
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 6/3/16.
  */
class RawAddrMessageSerializerTest extends FlatSpec with MustMatchers {

  //from this bitcoin developer guide example
  //https://bitcoin.org/en/developer-reference#addr
  val addressCount = "01"
  val time = "d91f4854"
  val services = "0100000000000000"
  val address = "00000000000000000000ffffc0000233"
  val port = "208d"
  val hex = addressCount + time + services + address + port
  "RawAddrMessageSerializer" must "read a AddrMessage from a hex string" in {
    val addrMessage = RawAddrMessageSerializer.read(hex)
    addrMessage.ipCount must be (CompactSizeUInt(UInt64.one,1))
    addrMessage.addresses.size must be (1)
  }

  it must "write a Addr message and get its original hex back" in {
    val addrMessage = RawAddrMessageSerializer.read(hex)
    BitcoinSUtil.encodeHex(RawAddrMessageSerializer.write(addrMessage)) must be (hex)
  }
}
