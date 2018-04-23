package org.bitcoins.spvnode.models

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.core.protocol.transaction.Transaction
import slick.driver.PostgresDriver.api._

class TransactionTable(tag: Tag) extends Table[Transaction](tag,"transactions")  {
  import ColumnMappers._
  def txId = column[DoubleSha256Digest]("txid", O.PrimaryKey)

  def hex = column[String]("hex")

  def * = (txId, hex).<>[Transaction,
    (DoubleSha256Digest,String)](txApply,txUnapply)

  /** Creates a block header from a tuple */
  private val txApply : ((DoubleSha256Digest,String)) => Transaction = {
    case (txId,hex) =>
      val tx = Transaction(hex)
      require(tx.txId == txId)
      tx
  }

  /** Destructs a block header to a tuple */
  private val txUnapply: Transaction => Option[(DoubleSha256Digest,String)] = {
    tx: Transaction =>
      Some(tx.txId,tx.hex)
  }

}
