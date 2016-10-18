package org.bitcoins.spvnode.networking

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.CompactSizeUInt
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.constant.{Constants, DbConfig}
import org.bitcoins.spvnode.messages.{BlockMessage, GetBlocksMessage, InventoryMessage, MsgBlock}
import org.bitcoins.spvnode.messages.data.{GetBlocksMessage, GetDataMessage, Inventory, InventoryMessage}
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil

/**
  * Created by chris on 7/10/16.
  */
sealed trait BlockActor extends Actor with BitcoinSLogger {

  def dbConfig: DbConfig

  def peerMsgHandler = PeerMessageHandler(context,dbConfig)

  def receive: Receive = LoggingReceive {
    case hash: DoubleSha256Digest =>
      val inv = Inventory(MsgBlock,hash)
      val getDataMessage = GetDataMessage(inv)
      val networkMessage = NetworkMessage(Constants.networkParameters, getDataMessage)
      peerMsgHandler ! networkMessage
      context.become(awaitBlockMsg)
    case blockHeader: BlockHeader =>
      self.forward(blockHeader.hash)
  }

  def awaitBlockMsg: Receive = LoggingReceive {
    case blockMsg: BlockMessage =>
      context.parent ! blockMsg
      context.stop(self)
  }
}


object BlockActor {
  private case class BlockActorImpl(dbConfig: DbConfig) extends BlockActor
  def props(dbConfig: DbConfig) = Props(classOf[BlockActorImpl], dbConfig)

  def apply(context: ActorContext, dbConfig: DbConfig): ActorRef = context.actorOf(props(dbConfig),
    BitcoinSpvNodeUtil.createActorName("BlockActor"))

}