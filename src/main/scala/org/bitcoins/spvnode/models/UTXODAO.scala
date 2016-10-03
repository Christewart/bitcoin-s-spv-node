package org.bitcoins.spvnode.models

import akka.actor.{ActorRef, ActorRefFactory, Props}
import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.spvnode.constant.DbConfig
import org.bitcoins.spvnode.models.UTXODAO.UTXODAORequest
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
import org.bitcoins.spvnode.utxo.UTXO

import scala.concurrent.Future
import slick.driver.PostgresDriver.api._

/**
  * Created by chris on 9/24/16.
  */
sealed trait UTXODAO extends CRUDActor[UTXO, Long] {

  val table = TableQuery[UTXOTable]

  def receive = {
    case request: UTXODAORequest => handleUTXODAORequest(request)
  }

  /** Handles a [[UTXODAORequest]] that our actor received */
  def handleUTXODAORequest(request: UTXODAORequest): Unit = request match {
    case UTXODAO.Create(utxo) =>
      val created = create(utxo)
      val response = created.map(UTXODAO.Created(_))(context.dispatcher)
      sendToParent(response)
    case UTXODAO.Read(id) =>
      val readReply = read(id)
      val response = readReply.map(UTXODAO.ReadReply(_))(context.dispatcher)
      sendToParent(response)
    case UTXODAO.Update(utxo) =>
      val updateReply = update(utxo).map(UTXODAO.UpdateReply(_))(context.dispatcher)
      sendToParent(updateReply)
    case UTXODAO.FindTxIds(txids) =>
      val reply = findTxIds(txids).map(UTXODAO.FindTxIdsReply(_))(context.dispatcher)
      sendToParent(reply)
    case UTXODAO.UpdateAll(utxos) =>
      val reply = updateAll(utxos).map(UTXODAO.UpdateAllReply(_))(context.dispatcher)
      sendToParent(reply)
  }


  def create(utxo: UTXO): Future[UTXO] = {
    val query = (table returning table.map(_.id)
      into ((u,id) => UTXO(Some(id),utxo.output, utxo.vout, utxo.txId,utxo.blockHash,utxo.state))
      ) += utxo
    database.run(query)
  }

  def find(utxo: UTXO): Query[Table[_], UTXO, Seq] = findByPrimaryKey(utxo.id.get)

  def findByPrimaryKey(id: Long): Query[Table[_], UTXO, Seq] = {
    table.filter(_.id === id)
  }

  /** Returns all [[UTXO]] objects that match the given set of txids */
  def findTxIds(txIds: Seq[DoubleSha256Digest]): Future[Seq[UTXO]] = {
    //hack to get around for using Future.sequence, usually I avoid implicits but this
    //is the easiest way to use Future.sequence since it takes two implicit values
    implicit val c = context.dispatcher
    val result: Seq[Future[Seq[UTXO]]] = txIds.map(findTxId(_))
    val nestedSeqs : Future[Seq[Seq[UTXO]]] = Future.sequence(result)
    nestedSeqs.map(_.flatten)

  }

  /** Finds all the outputs that were contained in a specific transaction */
  def findTxId(txId: DoubleSha256Digest): Future[Seq[UTXO]] = {
    import ColumnMappers._
    val query = table.filter(_.txId === txId).result
    database.run(query)
  }
}

object UTXODAO {
  private case class UTXODAOImpl(dbConfig: DbConfig) extends UTXODAO

  def props(dbConfig: DbConfig): Props = Props(classOf[UTXODAOImpl], dbConfig)

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig), BitcoinSpvNodeUtil.createActorName(this.getClass))
  }

  sealed trait UTXODAOMessage

  sealed trait UTXODAORequest extends UTXODAOMessage
  sealed trait UTXODAOReply extends UTXODAOMessage

  case class Create(utxo: UTXO) extends UTXODAORequest
  case class Created(utxo: UTXO) extends UTXODAOReply

  case class Read(id: Long) extends UTXODAORequest
  case class ReadReply(utxo: Option[UTXO]) extends UTXODAOReply

  case class FindTxIds(txIds: Seq[DoubleSha256Digest]) extends UTXODAORequest
  case class FindTxIdsReply(utxos: Seq[UTXO]) extends UTXODAOReply

  case class Update(utxo: UTXO) extends UTXODAORequest
  case class UpdateReply(utxo: Option[UTXO]) extends UTXODAOReply

  case class UpdateAll(utxos: Seq[UTXO]) extends UTXODAORequest
  case class UpdateAllReply(utxos: Seq[UTXO]) extends UTXODAOReply

}
