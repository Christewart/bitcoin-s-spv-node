package org.bitcoins.spvnode.gen

import java.net.{InetAddress, InetSocketAddress}

import org.bitcoins.core.gen.{NumberGenerator, StringGenerators}
import org.bitcoins.core.number.Int64
import org.bitcoins.spvnode.messages.VersionMessage
import org.bitcoins.spvnode.messages.control.{ServiceIdentifier, VersionMessage}
import org.bitcoins.spvnode.versions.ProtocolVersion
import org.scalacheck.Gen

/**
  * Created by chris on 6/27/16.
  */
trait ControlMessageGenerator {

  def versionMessage : Gen[VersionMessage] = for {
    version <- protocolVersion
    identifier <- serviceIdentifier
    timestamp <- NumberGenerator.int64s
    addressReceiveServices <- serviceIdentifier
    addressReceiveIpAddress <- inetAddress
    addressReceivePort <- portNumber
    addressTransServices <- serviceIdentifier
    addressTransIpAddress <- inetAddress
    addressTransPort <- portNumber
    nonce <- NumberGenerator.uInt64s
    userAgent <- StringGenerators.genString
    startHeight <- NumberGenerator.int32s
    relay = scala.util.Random.nextInt() % 2 == 0
  } yield VersionMessage(version, identifier, timestamp, addressReceiveServices, addressReceiveIpAddress, addressReceivePort,
    addressTransServices, addressTransIpAddress, addressTransPort, nonce, userAgent, startHeight, relay)


  def protocolVersion : Gen[ProtocolVersion] = for {
    randomNum <- Gen.choose(0,ProtocolVersion.versions.length-1)
  } yield ProtocolVersion.versions(randomNum)

  def serviceIdentifier: Gen[ServiceIdentifier] = for {
    //service identifiers can only be NodeNetwork or UnnamedService
    randomNum <- Gen.choose(0,1)
  } yield ServiceIdentifier(randomNum)


  def inetAddress : Gen[InetAddress] = for {
    socketAddress <- inetSocketAddress
  } yield socketAddress.getAddress


  def inetSocketAddress : Gen[InetSocketAddress] = for {
    p <- portNumber
  } yield new InetSocketAddress(p)

  def portNumber : Gen[Int] = Gen.choose(0,65535)

}

object ControlMessageGenerator extends ControlMessageGenerator


