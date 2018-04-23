package org.bitcoins.spvnode.serializers.messages.data

import org.bitcoins.core.protocol.blockchain.MerkleBlock
import org.bitcoins.core.serializers.RawBitcoinSerializer
import org.bitcoins.spvnode.messages.MerkleBlockMessage
import org.bitcoins.spvnode.messages.data.MerkleBlockMessage

/**
  * Created by chris on 6/2/16.
  * Responsible for serialization and deserialization of MerkleBlockMessages
  * https://bitcoin.org/en/developer-reference#merkleblock
  */
trait RawMerkleBlockMessageSerializer extends RawBitcoinSerializer[MerkleBlockMessage] {

  def read(bytes : List[Byte]) : MerkleBlockMessage = {
    val merkleBlock = MerkleBlock(bytes)
    MerkleBlockMessage(merkleBlock)
  }

  def write(merkleBlockMessage: MerkleBlockMessage) : Seq[Byte] = merkleBlockMessage.merkleBlock.bytes

}

object RawMerkleBlockMessageSerializer extends RawMerkleBlockMessageSerializer
