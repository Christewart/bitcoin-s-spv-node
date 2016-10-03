package org.bitcoins.spvnode.models

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.transaction.TransactionOutput
import org.bitcoins.spvnode.utxo.{UTXO, UTXOState}
import slick.driver.PostgresDriver.api._
/**
  * Created by chris on 9/23/16.
  */
class UTXOTable(tag : Tag) extends Table[UTXO](tag, "utxo_state") {
  import ColumnMappers._
  def id = column[Long]("id",O.PrimaryKey,O.AutoInc)

  def output = column[TransactionOutput]("output")

  def vout = column[UInt32]("vout")

  def txId = column[DoubleSha256Digest]("txid")

  def blockHash = column[DoubleSha256Digest]("block_hash")

  def state = column[UTXOState]("state")

  def * = (id.?, output, vout, txId, blockHash, state).<>[UTXO, (Option[Long],TransactionOutput, UInt32,
    DoubleSha256Digest, DoubleSha256Digest, UTXOState)](utxoApply, utxoUnapply)

  /** Transforms a tuple to a [[UTXO]] */
  private val utxoApply : ((Option[Long], TransactionOutput, UInt32, DoubleSha256Digest, DoubleSha256Digest,
    UTXOState)) => UTXO  = {
    case (id: Option[Long], output: TransactionOutput, vout: UInt32, txId: DoubleSha256Digest,
    blockHash: DoubleSha256Digest, state: UTXOState) =>
      UTXO(id,output, vout, txId,blockHash,state)
  }

  /** Transforms a [[UTXO]] to a tuple */
  private val utxoUnapply: UTXO => Option[(Option[Long], TransactionOutput, UInt32, DoubleSha256Digest, DoubleSha256Digest,
    UTXOState)] = { utxo =>
    Some(utxo.id, utxo.output, utxo.vout, utxo.txId, utxo.blockHash, utxo.state)
  }
}
