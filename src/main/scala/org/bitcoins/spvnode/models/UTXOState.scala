package org.bitcoins.spvnode.models

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.{BitcoinAddress, P2PKHAddress, P2SHAddress}
import org.bitcoins.core.protocol.script._
import org.bitcoins.core.protocol.transaction.TransactionOutput
import org.bitcoins.spvnode.constant.Constants

/**
  * Created by chris on 9/23/16.
  */
sealed trait UTXOState {

  def id : Option[Long]
  /** The output we are tracking the state of */
  def output: TransactionOutput
  /** The transaction's id from which this [[output]] is included in */
  def txId: DoubleSha256Digest

  /** The block that contains the transaction that includes this output */
  def blockHash: DoubleSha256Digest

  /** The address for the output */
  def address: BitcoinAddress = output.scriptPubKey match {
    case p2pkh: P2PKHScriptPubKey => P2PKHAddress(p2pkh.pubKeyHash, Constants.networkParameters)
    case p2sh: P2SHScriptPubKey => P2SHAddress(p2sh.scriptHash,Constants.networkParameters)
    case x @ (_ : P2PKScriptPubKey | _ : MultiSignatureScriptPubKey | _ : CSVScriptPubKey | _ : CLTVScriptPubKey |
      _ :  NonStandardScriptPubKey | EmptyScriptPubKey) =>
      //there isn't an obvious way to transform these script pubkeys into addresses
      throw new IllegalArgumentException("We cannot transform " + x + " into an address for UTXOState")
  }

  /** If the output has been spent or not */
  def isSpent: Boolean
}

object UTXOState {
  private case class UTXOStateImpl(id : Option[Long], output: TransactionOutput, txId: DoubleSha256Digest,
                                   blockHash: DoubleSha256Digest, isSpent: Boolean) extends UTXOState

  def apply(output: TransactionOutput, txId: DoubleSha256Digest, blockHash: DoubleSha256Digest, isSpent: Boolean): UTXOState = {
    UTXOState(None, output, txId, blockHash, isSpent)
  }

  def apply(id : Option[Long], output: TransactionOutput, txId: DoubleSha256Digest, blockHash: DoubleSha256Digest, isSpent: Boolean): UTXOState = {
    UTXOStateImpl(id, output,txId,blockHash,isSpent)
  }
}
