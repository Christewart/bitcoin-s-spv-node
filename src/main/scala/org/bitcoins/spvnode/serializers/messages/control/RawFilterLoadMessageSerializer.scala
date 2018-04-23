package org.bitcoins.spvnode.serializers.messages.control

import org.bitcoins.core.serializers.RawBitcoinSerializer
import org.bitcoins.core.serializers.bloom.RawBloomFilterSerializer
import org.bitcoins.spvnode.messages.FilterLoadMessage
import org.bitcoins.spvnode.messages.control.FilterLoadMessage

/**
  * Created by chris on 7/19/16.
  * Serializes and deserializes a [[FilterLoadMessage]]
  * https://bitcoin.org/en/developer-reference#filterload
  */
trait RawFilterLoadMessageSerializer extends RawBitcoinSerializer[FilterLoadMessage] {

  override def read(bytes: List[Byte]): FilterLoadMessage = {
    val filter = RawBloomFilterSerializer.read(bytes)
    FilterLoadMessage(filter)
  }

  override def write(filterLoadMessage: FilterLoadMessage): Seq[Byte] = {
    filterLoadMessage.bloomFilter.bytes
  }
}

object RawFilterLoadMessageSerializer extends RawFilterLoadMessageSerializer