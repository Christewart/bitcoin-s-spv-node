package org.bitcoins.spvnode.messages

import org.bitcoins.spvnode.headers.NetworkHeader
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 6/10/16.
  */
class NetworkPayloadTest extends FlatSpec with MustMatchers {

  "NetworkMessage" must "create a payload object from it's network header and the payload bytes" in {
    val rawNetworkMessage = TestUtil.rawNetworkMessage
    val header = NetworkHeader(rawNetworkMessage.take(48))
    val payloadHex = rawNetworkMessage.slice(48,rawNetworkMessage.length)
    val payload = NetworkPayload(header,payloadHex)
    payload.isInstanceOf[VersionMessage] must be (true)
    payload.commandName must be (NetworkPayload.versionCommandName)
  }
}
