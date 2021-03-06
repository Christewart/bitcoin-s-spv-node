package org.bitcoins.spvnode.serializers.messages.data

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.number.UInt64
import org.bitcoins.core.protocol.CompactSizeUInt
import org.bitcoins.core.util.BitcoinSUtil
import org.bitcoins.spvnode.messages.GetBlocksMessage
import org.bitcoins.spvnode.versions.ProtocolVersion70001
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 6/1/16.
  */
class RawGetBlocksMessageSerializerTest extends FlatSpec with MustMatchers {
  val hex = "7111010002d39f608a7775b537729884d4e6633bb2105e55a16a14d31b0000000000000000" +
    "5c3e6403d40837110a2e8afb602b1c01714bda7ce23bea0a0000000000000000" +
    "0000000000000000000000000000000000000000000000000000000000000000"
  "RawGetBlocksMessageSerializer" must "read a getblocks message from a hex string" in {
    val getBlocksMessage : GetBlocksMessage = RawGetBlocksMessageSerializer.read(hex)

    getBlocksMessage.protocolVersion must be (ProtocolVersion70001)

    getBlocksMessage.hashCount must be (CompactSizeUInt(UInt64(2),1))

    getBlocksMessage.blockHeaderHashes.head must be
    (DoubleSha256Digest(BitcoinSUtil.decodeHex("d39f608a7775b537729884d4e6633bb2105e55a16a14d31b0000000000000000")))

    getBlocksMessage.blockHeaderHashes.tail.head must be
    (DoubleSha256Digest(BitcoinSUtil.decodeHex("5c3e6403d40837110a2e8afb602b1c01714bda7ce23bea0a0000000000000000")))

    getBlocksMessage.stopHash must be (DoubleSha256Digest(BitcoinSUtil.decodeHex("0000000000000000000000000000000000000000000000000000000000000000")))

  }

  it must "write a getblocks message and get the original hex back" in {
    val getBlocksMessage : GetBlocksMessage = RawGetBlocksMessageSerializer.read(hex)

    BitcoinSUtil.encodeHex(RawGetBlocksMessageSerializer.write(getBlocksMessage)) must be (hex)
  }

}

