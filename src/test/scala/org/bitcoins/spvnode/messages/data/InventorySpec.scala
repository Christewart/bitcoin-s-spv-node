package org.bitcoins.spvnode.messages.data

import org.bitcoins.spvnode.gen.DataMessageGenerator
import org.scalacheck.{Prop, Properties}

/**
  * Created by chris on 7/8/16.
  */
class InventorySpec extends Properties("InventorySpec") {

  property("Serialization symmetry") =
    Prop.forAll(DataMessageGenerator.inventory) { inventory =>
      Inventory(inventory.hex) == inventory

    }
}
