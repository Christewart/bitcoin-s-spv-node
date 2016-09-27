package org.bitcoins.spvnode.models

import akka.actor.{ActorRef, ActorRefFactory, Props}
import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.spvnode.constant.DbConfig
import org.bitcoins.spvnode.models.UTXOStateDAO.UTXOStateDAORequest
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
import org.bitcoins.spvnode.utxo.UTXOState

import scala.concurrent.Future
import slick.driver.PostgresDriver.api._

/**
  * Created by chris on 9/24/16.
  */
sealed trait UTXOStateDAO extends CRUDActor[UTXOState, Long] {

  val table = TableQuery[UTXOStateTable]

  def receive = {
    case request: UTXOStateDAORequest => handleUTXOStateDAORequest(request)
  }

  /** Handles a [[UTXOStateDAORequest]] that our actor received */
  def handleUTXOStateDAORequest(request: UTXOStateDAORequest): Unit = request match {
    case UTXOStateDAO.Create(utxo) =>
      val created = create(utxo)
      val response = created.map(UTXOStateDAO.Created(_))(context.dispatcher)
      sendToParent(response)
    case UTXOStateDAO.Read(id) =>
      val readReply = read(id)
      val response = readReply.map(UTXOStateDAO.ReadReply(_))(context.dispatcher)
      sendToParent(response)
    case UTXOStateDAO.Update(utxo) =>
      val updateReply = update(utxo).map(UTXOStateDAO.UpdateReply(_))(context.dispatcher)
      sendToParent(updateReply)
    case UTXOStateDAO.FindTxIds(txids) =>
      val reply = findTxIds(txids).map(UTXOStateDAO.FindTxIdsReply(_))(context.dispatcher)
      sendToParent(reply)
    case UTXOStateDAO.UpdateAll(utxos) =>
      val reply = updateAll(utxos).map(UTXOStateDAO.UpdateAllReply(_))(context.dispatcher)
      sendToParent(reply)
  }


  def create(utxo: UTXOState): Future[UTXOState] = {
    val query = (table returning table.map(_.id)
      into ((u,id) => UTXOState(Some(id),utxo.output, utxo.vout, utxo.txId,utxo.blockHash,utxo.isSpent))
      ) += utxo
    database.run(query)
  }

  def find(uTXOState: UTXOState): Query[Table[_], UTXOState, Seq] = findByPrimaryKey(uTXOState.id.get)

  def findByPrimaryKey(id: Long): Query[Table[_], UTXOState, Seq] = {
    table.filter(_.id === id)
  }

  /** Returns all [[UTXOState]] objects that match the given set of txids */
  def findTxIds(txIds: Seq[DoubleSha256Digest]): Future[Seq[UTXOState]] = {
    //hack to get around for using Future.sequence, usually I avoid implicits but this
    //is the easiest way to use Future.sequence since it takes two implicit values
    implicit val c = context.dispatcher
    val result: Seq[Future[Seq[UTXOState]]] = txIds.map(findTxId(_))
    val nestedSeqs : Future[Seq[Seq[UTXOState]]] = Future.sequence(result)
    nestedSeqs.map(_.flatten)

  }

  /** Finds all the outputs that were contained in a specific transaction */
  def findTxId(txId: DoubleSha256Digest): Future[Seq[UTXOState]] = {
    import ColumnMappers._
    val query = table.filter(_.txId === txId).result
    database.run(query)
  }
}

object UTXOStateDAO {
  private case class UTXOStateDAOImpl(dbConfig: DbConfig) extends UTXOStateDAO

  def props(dbConfig: DbConfig): Props = Props(classOf[UTXOStateDAOImpl], dbConfig)

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }

  sealed trait UTXOStateDAOMessage

  sealed trait UTXOStateDAORequest extends UTXOStateDAOMessage
  sealed trait UTXOStateDAOReply extends UTXOStateDAOMessage

  case class Create(uTXOState: UTXOState) extends UTXOStateDAORequest
  case class Created(uTXOState: UTXOState) extends UTXOStateDAOReply

  case class Read(id: Long) extends UTXOStateDAORequest
  case class ReadReply(utxoState: Option[UTXOState]) extends UTXOStateDAOReply

  case class FindTxIds(txIds: Seq[DoubleSha256Digest]) extends UTXOStateDAORequest
  case class FindTxIdsReply(utxoStates: Seq[UTXOState]) extends UTXOStateDAOReply

  case class Update(utxo: UTXOState) extends UTXOStateDAORequest
  case class UpdateReply(utxo: Option[UTXOState]) extends UTXOStateDAOReply

  case class UpdateAll(utxos: Seq[UTXOState]) extends UTXOStateDAORequest
  case class UpdateAllReply(utxos: Seq[UTXOState]) extends UTXOStateDAOReply

}
