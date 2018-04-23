package org.bitcoins.spvnode.serializers.messages

import org.bitcoins.core.util.BitcoinSUtil
import org.bitcoins.spvnode.messages.{MsgBlock, MsgFilteredBlock, MsgTx}
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 5/31/16.
  */
class RawTypeIdentifierSerializerTest extends FlatSpec with MustMatchers {
  val msgTxHex = "01000000"
  val msgBlockHex = "02000000"
  val msgFilteredBlockHex = "03000000"
  val encode = BitcoinSUtil.encodeHex(_: Seq[Byte])
  "RawTypeIdentifier" must "read/write a MsgTx" in {
    val msg = RawTypeIdentifierSerializer.read(msgTxHex)
    msg must be (MsgTx)
    encode(RawTypeIdentifierSerializer.write(msg)) must be (msgTxHex)
  }

  it must "read/write a MsgBlock" in {
    val msg = RawTypeIdentifierSerializer.read(msgBlockHex)
    msg must be (MsgBlock)
    encode(RawTypeIdentifierSerializer.write(msg)) must be (msgBlockHex)
  }

  it must "read/write a MsgFilteredBlock" in {
    val msg = RawTypeIdentifierSerializer.read(msgFilteredBlockHex)
    msg must be (MsgFilteredBlock)
    encode(RawTypeIdentifierSerializer.write(msg)) must be (msgFilteredBlockHex)
  }
}
