package org.bitcoins.spvnode.gen

import org.bitcoins.core.gen.{CryptoGenerators, NumberGenerator, TransactionGenerators}
import org.bitcoins.spvnode.utxo._
import org.scalacheck.Gen

/**
  * Created by chris on 9/26/16.
  */
trait UTXOGenerator {

  def utxo: Gen[UTXO] = for {
    i <- Gen.choose(0,1)
    state <- utxoState
    u <- utxo(state)
  } yield u

  def utxo(state: UTXOState): Gen[UTXO] = for {
    output <- TransactionGenerators.outputs
    vout <- NumberGenerator.uInt32s
    txId <- CryptoGenerators.doubleSha256Digest
    blockHash <- CryptoGenerators.doubleSha256Digest
  } yield UTXO(output, vout, txId, blockHash, state)

  def utxoState: Gen[UTXOState] = for {
    i <- Gen.choose(0,3)
  } yield Seq(ReceivedUnconfirmed(6), SpentUnconfirmed(6), Spent, Spendable)(i)
}

object UTXOGenerator extends UTXOGenerator