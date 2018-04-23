package org.bitcoins.spvnode.serializers.messages.control

import java.net.InetAddress

import org.bitcoins.core.number.{Int32, Int64, UInt32, UInt64}
import org.bitcoins.core.protocol.CompactSizeUInt
import org.bitcoins.core.serializers.RawBitcoinSerializer
import org.bitcoins.core.util.{BitcoinSLogger, BitcoinSUtil}
import org.bitcoins.spvnode.messages.VersionMessage
import org.bitcoins.spvnode.messages.control.{ServiceIdentifier, VersionMessage}
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
import org.bitcoins.spvnode.versions.ProtocolVersion

/**
  * Created by chris on 6/2/16.
  * Responsible for serialization and deserialization of VersionMessages on the p2p network
  * https://bitcoin.org/en/developer-reference#version
  */
trait RawVersionMessageSerializer extends RawBitcoinSerializer[VersionMessage] {

  def read(bytes : List[Byte]) : VersionMessage = {
    val version = ProtocolVersion(bytes.take(4))
    val services = ServiceIdentifier(bytes.slice(4,12))
    val timestamp = Int64(bytes.slice(12,20).reverse)
    val addressReceiveServices = ServiceIdentifier(bytes.slice(20,28))
    val addressReceiveIpAddress = InetAddress.getByAddress(bytes.slice(28,44).toArray)
    val addressReceivePort = UInt32(bytes.slice(44,46)).toInt
    val addressTransServices = ServiceIdentifier(bytes.slice(46,54))
    val addressTransIpAddress = InetAddress.getByAddress(bytes.slice(54,70).toArray)
    val addressTransPort = UInt32(bytes.slice(70,72)).toInt
    val nonce = UInt64(bytes.slice(72,80))
    val userAgentSize = CompactSizeUInt.parseCompactSizeUInt(bytes.slice(80,bytes.size))
    val userAgentBytesStartIndex = 80 + userAgentSize.size.toInt
    val userAgentBytes = bytes.slice(userAgentBytesStartIndex, userAgentBytesStartIndex + userAgentSize.num.toInt)
    val userAgent = userAgentBytes.map(_.toChar).mkString
    val startHeightStartIndex = (userAgentBytesStartIndex + userAgentSize.num.toInt).toInt
    val startHeight = Int32(bytes.slice(startHeightStartIndex, startHeightStartIndex + 4).reverse)
    val relay = bytes(startHeightStartIndex + 4) != 0

    VersionMessage(version,services,timestamp, addressReceiveServices, addressReceiveIpAddress,
      addressReceivePort, addressTransServices, addressTransIpAddress, addressTransPort,
      nonce, userAgent, startHeight, relay)
  }

  def write(versionMessage: VersionMessage) : Seq[Byte] = {
    versionMessage.version.bytes ++ versionMessage.services.bytes ++
      versionMessage.timestamp.bytes.reverse ++
      versionMessage.addressReceiveServices.bytes ++
      BitcoinSpvNodeUtil.writeAddress(versionMessage.addressReceiveIpAddress) ++
      //encode hex returns 8 characters, but we only need the last 4 since port number is a uint16
      BitcoinSUtil.decodeHex(BitcoinSUtil.encodeHex(versionMessage.addressReceivePort).slice(4,8)) ++
      versionMessage.addressTransServices.bytes ++
      BitcoinSpvNodeUtil.writeAddress(versionMessage.addressTransIpAddress) ++
      //encode hex returns 8 characters, but we only need the last 4 since port number is a uint16
      BitcoinSUtil.decodeHex(BitcoinSUtil.encodeHex(versionMessage.addressTransPort).slice(4,8)) ++
      versionMessage.nonce.bytes ++
      versionMessage.userAgentSize.bytes ++
      versionMessage.userAgent.getBytes ++
      versionMessage.startHeight.bytes.reverse ++
      (if (versionMessage.relay) Seq(1.toByte) else Seq(0.toByte))
  }

}

object RawVersionMessageSerializer extends RawVersionMessageSerializer
