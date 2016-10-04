package org.bitcoins.spvnode.utxo

/**
  * Created by chris on 10/3/16.
  */
sealed trait UTXOState {
  override def toString: String = this match {
    case Spent => "Spent"
    case Spendable => "Spendable"
    case _ : ReceivedUnconfirmed => "ReceivedUnconfirmed"
    case _ : SpentUnconfirmed => "SpentUnconfirmed"
  }
}

sealed trait ConfirmedUTXO extends UTXOState

case object Spent extends ConfirmedUTXO
case object Spendable extends ConfirmedUTXO

sealed trait UnconfirmedUTXO extends UTXOState

case class ReceivedUnconfirmed() extends UnconfirmedUTXO
case class SpentUnconfirmed() extends UnconfirmedUTXO

object UTXOState {

  def apply(str: String): UTXOState = str match {
    case "Spent" => Spent
    case "Spendable" => Spendable
    case "ReceivedUnconfirmed" => ReceivedUnconfirmed()
    case "SpentUnconfirmed" => SpentUnconfirmed()
  }
}