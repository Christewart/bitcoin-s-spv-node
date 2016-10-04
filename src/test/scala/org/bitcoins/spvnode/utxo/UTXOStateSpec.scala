package org.bitcoins.spvnode.utxo

import org.bitcoins.spvnode.gen.UTXOGenerator
import org.scalacheck.{Prop, Properties}

/**
  * Created by chris on 10/3/16.
  */
class UTXOStateSpec extends Properties("UTXOStateSpec") {

  property("Serialization symmetry") =
    Prop.forAll(UTXOGenerator.utxoState) { case state =>
      UTXOState(state.toString) == state
    }
}
