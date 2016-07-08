package org.bitcoins.spvnode.constant

import akka.actor.ActorSystem
import org.bitcoins.core.config.TestNet3
import org.bitcoins.spvnode.messages.control.VersionMessage
import org.bitcoins.spvnode.versions.ProtocolVersion70012

/**
  * Created by chris on 7/1/16.
  */
trait Constants {
  lazy val actorSystem = ActorSystem("BitcoinSpvNode")
  def networkParameters = TestNet3
  def version = ProtocolVersion70012

  def versionMessage = VersionMessage(networkParameters)
}

object Constants extends Constants