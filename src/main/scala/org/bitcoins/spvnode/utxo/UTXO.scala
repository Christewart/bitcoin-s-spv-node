package org.bitcoins.spvnode.utxo

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.script._
import org.bitcoins.core.protocol.transaction.TransactionOutput
import org.bitcoins.core.protocol.{BitcoinAddress, P2PKHAddress, P2SHAddress}
import org.bitcoins.spvnode.constant.Constants

/**
  * Created by chris on 9/23/16.
  * This trait is meant to track the state of a utxo on the bitcoin network.
  * It is intended for wallet software to create this trait, then insert it into
  * persistent storage where it can be updated depending on what happens on the
  * bitcoin network
  *
  * [[https://github.com/bitcoin/bitcoin/blob/master/src/coins.h]]
  */
sealed trait UTXO {

  def id : Option[Long]
  /** The output we are tracking the state of */
  def output: TransactionOutput

  /** The index of the output in the transaction */
  def vout: UInt32

  /** The transaction's id from which this [[output]] is included in */
  def txId: DoubleSha256Digest

  /** The block that contains the transaction that includes this output */
  def blockHash: DoubleSha256Digest

  /** The address for the output */
  def address: Option[BitcoinAddress] = output.scriptPubKey match {
    case p2pkh: P2PKHScriptPubKey => Some(P2PKHAddress(p2pkh.pubKeyHash, Constants.networkParameters))
    case p2sh: P2SHScriptPubKey => Some(P2SHAddress(p2sh.scriptHash, Constants.networkParameters))
    case x @ (_ : P2PKScriptPubKey | _ : MultiSignatureScriptPubKey | _ : CSVScriptPubKey | _ : CLTVScriptPubKey |
      _ :  NonStandardScriptPubKey | EmptyScriptPubKey) => None
  }

  /** The [[UTXOState]] of the given output, for instance this can be [[Spent]], [[Spendable]] etc */
  def state: UTXOState

}

object UTXO {
  private case class UTXOImpl(id : Option[Long], output: TransactionOutput, vout: UInt32, txId: DoubleSha256Digest,
                                   blockHash: DoubleSha256Digest, state: UTXOState) extends UTXO

  def apply(output: TransactionOutput, vout: UInt32, txId: DoubleSha256Digest, blockHash: DoubleSha256Digest,
            state: UTXOState): UTXO = {
    UTXO(None, output, vout, txId, blockHash, state)
  }

  def apply(id : Option[Long], output: TransactionOutput, vout: UInt32, txId: DoubleSha256Digest,
            blockHash: DoubleSha256Digest, state: UTXOState): UTXO = {
    UTXOImpl(id, output, vout, txId,blockHash,state)
  }



}
