package org.bitcoins.spvnode

import java.net.InetSocketAddress

import akka.actor.PoisonPill
import akka.pattern.ask
import akka.testkit.ImplicitSender
import akka.util.Timeout
import org.bitcoins.core.protocol.blockchain.{Block, BlockHeader, TestNetChainParams}
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.zmq._
import org.bitcoins.spvnode.constant.Constants
import org.bitcoins.spvnode.models.{BlockHeaderDAO, TransactionDAO, TransactionTable}
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.networking.sync.BlockHeaderSyncActor
import org.slf4j.{Logger, LoggerFactory}
import slick.driver.PostgresDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
/**
  * Created by chris on 8/29/16.
  */
object Main extends App {
  implicit val timeout = Timeout(120.seconds)
  private val logger = BitcoinSLogger.logger
  override def main(args : Array[String]) = {
    val system = Constants.actorSystem
    implicit val ec = system.dispatcher
    val dbConfig = Constants.dbConfig
    def txListener: Option[Transaction => Future[Unit]] = Some { tx : Transaction =>
      val txDAO = TransactionDAO(system,dbConfig)
      val msg = TransactionDAO.Create(tx)
      txDAO ! msg
      txDAO ! PoisonPill
      Future.successful(Unit)
    }

    def blockListener: Option[Block => Future[Unit]] = Some { block: Block =>
      val header = block.blockHeader
      val blockHeaderDAO = BlockHeaderDAO(system,dbConfig)
      blockHeaderDAO ! BlockHeaderDAO.Create(header)
      blockHeaderDAO ! PoisonPill
      Future.successful(Unit)
    }
    val socket = new InetSocketAddress("tcp://127.0.0.1", 28332)
    val zmqSub = new ZMQSubscriber[Transaction,Block](socket,None,None,txListener,blockListener)
    val f : Future[Unit] = zmqSub.start()
    f
  }

}
