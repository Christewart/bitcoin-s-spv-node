package org.bitcoins.spvnode.utxo

/**
  * Created by chris on 10/3/16.
  */
sealed trait UTXOState {
  override def toString: String = this match {
    case Spent => "Spent"
    case Spendable => "Spendable"
    case ReceivedUnconfirmed(_) => "ReceivedUnconfirmed"
    case SpentUnconfirmed(_) => "SpentUnconfirmed"
  }
}
case object Spent extends UTXOState
case object Spendable extends UTXOState
case class ReceivedUnconfirmed(numConfsRequired: Int = 6) extends UTXOState
case class SpentUnconfirmed(numConfsRequired: Int = 6) extends UTXOState

object UTXOState {

  def apply(str: String): UTXOState = str match {
    case "Spent" => Spent
    case "Spendable" => Spendable
    case "ReceivedUnconfirmed" => ReceivedUnconfirmed()
    case "SpentUnconfirmed" => SpentUnconfirmed()
  }
}