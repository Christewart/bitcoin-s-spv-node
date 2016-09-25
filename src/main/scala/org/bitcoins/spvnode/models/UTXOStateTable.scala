package org.bitcoins.spvnode.models

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.transaction.TransactionOutput
import org.bitcoins.spvnode.utxo.UTXOState
import slick.driver.PostgresDriver.api._
/**
  * Created by chris on 9/23/16.
  */
class UTXOStateTable(tag : Tag) extends Table[UTXOState](tag, "utxo_state") {
  import ColumnMappers._
  def id = column[Long]("id",O.PrimaryKey,O.AutoInc)

  def output = column[TransactionOutput]("output")

  def txId = column[DoubleSha256Digest]("txid")

  def blockHash = column[DoubleSha256Digest]("block_hash")

  def isSpent = column[Boolean]("is_spent")

  def * = (id.?, output, txId, blockHash, isSpent).<>[UTXOState, (Option[Long],TransactionOutput,
    DoubleSha256Digest, DoubleSha256Digest, Boolean)](uTXOStateApply, utxoStateUnapply)

  /** Transforms a tuple to a [[UTXOState]] */
  private val uTXOStateApply : ((Option[Long], TransactionOutput, DoubleSha256Digest, DoubleSha256Digest,
    Boolean)) => UTXOState  = {
    case (id: Option[Long], output: TransactionOutput, txId: DoubleSha256Digest, blockHash: DoubleSha256Digest, isSpent: Boolean) =>
      UTXOState(id,output,txId,blockHash,isSpent)
  }

  /** Transforms a [[UTXOState]] to a tuple */
  private val utxoStateUnapply: UTXOState => Option[(Option[Long], TransactionOutput, DoubleSha256Digest, DoubleSha256Digest,
    Boolean)] = { utxo =>
    Some(utxo.id, utxo.output, utxo.txId, utxo.blockHash, utxo.isSpent)
  }
}
