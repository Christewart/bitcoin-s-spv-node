package org.bitcoins.spvnode.versions

import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by chris on 6/6/16.
  */
class ProtocolVersionTest extends FlatSpec with MustMatchers {

  "ProtocolVersion" must "give us the correct protocol version back from its hex format" in {
    ProtocolVersion("72110100") must be (ProtocolVersion70002)
  }

  it must "determine a common protocol version between two nodes" in {
    ProtocolVersion.commonProtocolVersion(ProtocolVersion106) must be (ProtocolVersion106)
  }

  it must "determine that the lowest protocol version is the newest protocol version" in {
    ProtocolVersion.commonProtocolVersion(ProtocolVersion70014, ProtocolVersion70014) must be (ProtocolVersion70014)
  }

  it must "determine the correct protocol version for versions that are off by one" in {
    ProtocolVersion.commonProtocolVersion(ProtocolVersion70014, ProtocolVersion70012) must be (ProtocolVersion70012)
  }


}

