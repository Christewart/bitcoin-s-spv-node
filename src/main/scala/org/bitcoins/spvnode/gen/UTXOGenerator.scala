package org.bitcoins.spvnode.gen

import org.bitcoins.core.gen.{CryptoGenerators, NumberGenerator, TransactionGenerators}
import org.bitcoins.spvnode.utxo.UTXOState
import org.scalacheck.Gen

/**
  * Created by chris on 9/26/16.
  */
trait UTXOGenerator {

  def utxoState: Gen[UTXOState] = for {
    i <- Gen.choose(0,1)
    u <- utxoState(i == 1)
  } yield u

  def utxoState(isSpent: Boolean): Gen[UTXOState] = for {
    output <- TransactionGenerators.outputs
    vout <- NumberGenerator.uInt32s
    txId <- CryptoGenerators.doubleSha256Digest
    blockHash <- CryptoGenerators.doubleSha256Digest
  } yield UTXOState(output, vout, txId, blockHash, isSpent)
}

object UTXOGenerator extends UTXOGenerator