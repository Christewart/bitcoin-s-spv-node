package org.bitcoins.spvnode.utxo

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.DbConfig
import org.bitcoins.spvnode.messages.{DataPayload, TransactionMessage}
import org.bitcoins.spvnode.models.UTXOStateDAO
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
/**
  * Created by chris on 9/25/16.
  * Responsible for updating the state of our utxos when an event happens on the
  * bitcoin network. For instance, if a new block is published and it spends a utxo
  * in our wallet, we must update the [[UTXOState]] of to indicate the UTXO was spent
  */
sealed trait UTXOStateHandlerActor extends Actor with BitcoinSLogger {

  def dbConfig: DbConfig

  def receive = {
    case dataPayload: DataPayload => handleDataPayload(dataPayload)
  }
  /** Handles a [[DataPayload]] message from the p2p network and updates
    * our utxo store accordingly
    * @param dataPayload - the algebraic data type for payloads on the bitcoin protocol [[DataPayload]]
    * @return the utxo state objects modified
    */
  def handleDataPayload(dataPayload: DataPayload): Unit = dataPayload match {
    case txMsg: TransactionMessage =>
      val tx = txMsg.transaction
      val outPointTxIds = tx.inputs.map(_.previousOutput.txId)
      val utxoStateDAO = UTXOStateDAO(context,dbConfig)
      utxoStateDAO ! UTXOStateDAO.FindTxIds(outPointTxIds)
  }
}

object UTXOStateHandler {
  private case class UTXOStateHandlerImpl(dbConfig: DbConfig) extends UTXOStateHandlerActor


  def props(dbConfig: DbConfig): Props = Props(classOf[UTXOStateHandlerImpl], dbConfig)

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }
}