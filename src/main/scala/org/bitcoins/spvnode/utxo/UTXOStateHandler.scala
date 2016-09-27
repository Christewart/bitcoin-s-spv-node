package org.bitcoins.spvnode.utxo

import akka.actor.{Actor, ActorRef, ActorRefFactory, PoisonPill, Props}
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
sealed trait UTXOStateHandler extends Actor with BitcoinSLogger {

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
      logger.info("Handling txMsg: " + txMsg)
      val tx = txMsg.transaction
      val outPointTxIds = tx.inputs.map(_.previousOutput.txId)
      val utxoStateDAO = UTXOStateDAO(context,dbConfig)
      //check to see if any of our outputs part of the outpoints in this tx
      utxoStateDAO ! UTXOStateDAO.FindTxIds(outPointTxIds)
      context.become(awaitTxIds(tx, txMsg))
  }

  /** Waits for [[UTXOStateDAO]] to send back the [[UTXOState]] spent by the outpoints */
  def awaitTxIds(transaction: Transaction, originalMsg: DataPayload): Receive = {
    case txIdsReply: UTXOStateDAO.FindTxIdsReply =>
      logger.info("Found utxos that matched txid: " + txIdsReply.utxoStates)
      val utxos = txIdsReply.utxoStates
      //check if the transaction spends any of these utxos
      val spentUTXOs = findSpentUTXOs(utxos,transaction)
      updateUTXOStates(spentUTXOs,transaction,originalMsg)
  }


  /** Returns all the utxos in the given set that is spent by the [[Transaction]] */
  private def findSpentUTXOs(utxos: Seq[UTXOState], transaction: Transaction): Seq[UTXOState] = {
    val spentUTXOs: Seq[Seq[UTXOState]] = for {
      input <- transaction.inputs
    } yield for {
      u <- utxos
      if (input.previousOutput == TransactionOutPoint(u.txId,u.vout))
      spentUTXO = UTXOState(u.id,u.output,u.vout,u.txId,u.blockHash,true)
    } yield spentUTXO
    spentUTXOs.flatten
  }

  /** Sends our parent actor the [[org.bitcoins.spvnode.utxo.UTXOStateHandler.Processed]]
    * message when we are finished processing our [[DataPayload]] */
  def awaitUpdatedUTXOs(originalMsg: DataPayload): Receive = {
    case x @ (_: UTXOStateDAO.UpdateAllReply | _: UTXOStateDAO.UpdateReply) =>
      context.parent ! UTXOStateHandler.Processed(originalMsg)
      sender ! PoisonPill
  }

  /** Updates all of the the state of all of the utxos the transaction spends */
  private def updateUTXOStates(utxos: Seq[UTXOState], transaction: Transaction, originalMsg: DataPayload): Unit = {
    logger.info("Updating utxo states: " + utxos)
    val uTXOStateDAO = UTXOStateDAO(context,dbConfig)
    context.become(awaitUpdatedUTXOs(originalMsg))
    uTXOStateDAO ! UTXOStateDAO.UpdateAll(utxos)
  }
}

object UTXOStateHandler {
  private case class UTXOStateHandlerImpl(dbConfig: DbConfig) extends UTXOStateHandler

  def props(dbConfig: DbConfig): Props = Props(classOf[UTXOStateHandlerImpl], dbConfig)

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }


  sealed trait UTXOStateHandlerMessage

  sealed trait UTXOStateHandlerMessageReply extends UTXOStateHandlerMessage

  case class Processed(dataPayload: DataPayload) extends UTXOStateHandlerMessageReply
}
