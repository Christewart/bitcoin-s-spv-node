package org.bitcoins.spvnode.models

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.number.UInt32
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

  def vout = column[UInt32]("vout")

  def txId = column[DoubleSha256Digest]("txid")

  def blockHash = column[DoubleSha256Digest]("block_hash")

  def isSpent = column[Boolean]("is_spent")

  def * = (id.?, output, vout, txId, blockHash, isSpent).<>[UTXOState, (Option[Long],TransactionOutput, UInt32,
    DoubleSha256Digest, DoubleSha256Digest, Boolean)](uTXOStateApply, utxoStateUnapply)

  /** Transforms a tuple to a [[UTXOState]] */
  private val uTXOStateApply : ((Option[Long], TransactionOutput, UInt32, DoubleSha256Digest, DoubleSha256Digest,
    Boolean)) => UTXOState  = {
    case (id: Option[Long], output: TransactionOutput, vout: UInt32, txId: DoubleSha256Digest, blockHash: DoubleSha256Digest, isSpent: Boolean) =>
      UTXOState(id,output, vout, txId,blockHash,isSpent)
  }

  /** Transforms a [[UTXOState]] to a tuple */
  private val utxoStateUnapply: UTXOState => Option[(Option[Long], TransactionOutput, UInt32, DoubleSha256Digest, DoubleSha256Digest,
    Boolean)] = { utxo =>
    Some(utxo.id, utxo.output, utxo.vout, utxo.txId, utxo.blockHash, utxo.isSpent)
  }
}
