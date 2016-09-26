package org.bitcoins.spvnode.utxo

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import org.bitcoins.core.protocol.transaction.{Transaction, TransactionOutPoint}
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
      //check to see if any of our outputs part of the outpoints in this tx
      utxoStateDAO ! UTXOStateDAO.FindTxIds(outPointTxIds)
      context.become(awaitTxIds(tx))
  }

  /** Waits for [[UTXOStateDAO]] to send back the [[UTXOState]] spent by the outpoints */
  def awaitTxIds(transaction: Transaction): Receive = {
    case txIdsReply: UTXOStateDAO.FindTxIdsReply =>
      val utxos = txIdsReply.utxoStates
      //check if the transaction spends any of these utxos

  }

  /** Updates the [[UTXOState]] if the transaction modifies it */
  private def updateUTXOState(utxo: UTXOState, transaction: Transaction): Unit = {
    for {
      input <- transaction.inputs
    } yield {
      if (input.previousOutput == TransactionOutPoint(utxo.txId,utxo.vout)) {
        //update the UTXOState to be spent
        val updatedUtxo = UTXOState(utxo.id,utxo.output, utxo.vout,utxo.txId,utxo.blockHash,true)
      } else None
    }
    ???
  }
}

object UTXOStateHandler {
  private case class UTXOStateHandlerImpl(dbConfig: DbConfig) extends UTXOStateHandlerActor


  def props(dbConfig: DbConfig): Props = Props(classOf[UTXOStateHandlerImpl], dbConfig)

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }
}
