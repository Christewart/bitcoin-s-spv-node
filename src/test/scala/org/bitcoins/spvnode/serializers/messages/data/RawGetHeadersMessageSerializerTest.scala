package org.bitcoins.spvnode.serializers.messages.data

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.number.UInt64
import org.bitcoins.core.protocol.CompactSizeUInt
import org.bitcoins.core.util.BitcoinSUtil
import org.bitcoins.spvnode.util.TestUtil
import org.bitcoins.spvnode.versions.ProtocolVersion70002
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 6/29/16.
  */
class RawGetHeadersMessageSerializerTest extends FlatSpec with MustMatchers {
  val hex = TestUtil.rawGetHeadersMsg

  "RawGetHeadersMessageSerializer" must "read a hex string representing a GetHeaderMessage" in {
    val getHeadersMessage = RawGetHeadersMessageSerializer.read(hex)
    getHeadersMessage.version must be (ProtocolVersion70002)
    getHeadersMessage.hashCount must be (CompactSizeUInt(UInt64(31),1))
    getHeadersMessage.hashes.length must be (31)

    getHeadersMessage.hashStop must be (DoubleSha256Digest("0000000000000000000000000000000000000000000000000000000000000000"))
  }

  it must "write a GetHeaderMessage" in {
    val getHeadersMessage = RawGetHeadersMessageSerializer.read(hex)
    BitcoinSUtil.encodeHex(RawGetHeadersMessageSerializer.write(getHeadersMessage)) must be (hex)
  }


}
