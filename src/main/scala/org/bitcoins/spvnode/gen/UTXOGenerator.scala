package org.bitcoins.spvnode.gen

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.gen.{CryptoGenerators, NumberGenerator, TransactionGenerators}
import org.bitcoins.spvnode.utxo._
import org.scalacheck.Gen

/**
  * Created by chris on 9/26/16.
  */
trait UTXOGenerator {

  def utxo: Gen[UTXO] = for {
    state <- utxoState
    u <- utxo(state)
  } yield u

  def utxo(state: UTXOState): Gen[UTXO] = for {
    blockHash <- CryptoGenerators.doubleSha256Digest
    u <- utxoBlockHash(state,blockHash)
  } yield u

  /** Generates a utxo with the given blockhash and state */
  def utxoBlockHash(state: UTXOState, blockHash: DoubleSha256Digest): Gen[UTXO] = for {
    output <- TransactionGenerators.outputs
    vout <- NumberGenerator.uInt32s
    txId <- CryptoGenerators.doubleSha256Digest
  } yield UTXO(output, vout, txId, blockHash, state)

  def utxoBlockHash(blockHash: DoubleSha256Digest): Gen[UTXO] = for {
    state <- utxoState
    u <- utxoBlockHash(state, blockHash)
  } yield u

  def utxoState: Gen[UTXOState] = for {
    state <- Gen.oneOf(ReceivedUnconfirmed(), SpentUnconfirmed(), Spent, Spendable)
  } yield state
}

object UTXOGenerator extends UTXOGenerator