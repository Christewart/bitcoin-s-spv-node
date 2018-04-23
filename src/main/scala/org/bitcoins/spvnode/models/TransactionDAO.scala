package org.bitcoins.spvnode.models

import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.event.LoggingReceive
import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.DbConfig
import org.bitcoins.spvnode.models.TransactionDAO.TransactionDAOMsg
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

trait TransactionDAO extends CRUDActor[Transaction,DoubleSha256Digest] {
  private val logger = BitcoinSLogger.logger
  override val table = TableQuery[TransactionTable]
  implicit val ec = context.system.dispatcher
  override def receive = LoggingReceive {
    case msg: TransactionDAOMsg => handleMsg(msg,sender())

  }

  private def handleMsg(msg: TransactionDAOMsg,sender: ActorRef): Unit = msg match {
    case TransactionDAO.Create(tx) => create(tx).map { t =>
      logger.info(s"created $t")
      sender ! t
    }
    case TransactionDAO.CreateAll(txs) => createAll(txs).map(ts => sender ! ts)
    case TransactionDAO.Read(txId) =>
      val r = read(txId)
      r.onFailure { case err => throw err }
        r.map { txOpt =>
      logger.info(s"READ TXOPT $txOpt")
      sender ! txOpt
    }
  }

  override def createAll(txs: Seq[Transaction]): Future[Seq[Transaction]] = {
    val actions = txs.map(tx => (table += tx).andThen(DBIO.successful(tx)))
    val result = database.run(DBIO.sequence(actions))
    result
  }

  override def findAll(txs: Seq[Transaction]): Query[Table[_],  Transaction, Seq] = {
    findByPrimaryKeys(txs.map(_.txId))
  }

  override def findByPrimaryKeys(txIds : Seq[DoubleSha256Digest]): Query[Table[_], Transaction, Seq] = {
    import ColumnMappers._
    table.filter(tx => tx.txId.inSet(txIds))
  }
}


object TransactionDAO {
  sealed abstract class TransactionDAOMsg
  case class Create(tx: Transaction) extends TransactionDAOMsg
  case class CreateAll(txs: Seq[Transaction]) extends TransactionDAOMsg
  case class Read(txId: DoubleSha256Digest) extends TransactionDAOMsg
  private case class TransactionDAOImpl(dbConfig: DbConfig) extends TransactionDAO
  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = context.actorOf(props(dbConfig),
    BitcoinSpvNodeUtil.createActorName(BlockHeaderDAO.getClass))

  def props(dbConfig: DbConfig): Props = Props(classOf[TransactionDAOImpl],dbConfig)

}
