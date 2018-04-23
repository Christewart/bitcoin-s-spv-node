package org.bitcoins.spvnode

import java.net.InetSocketAddress

import akka.actor.PoisonPill
import akka.pattern.ask
import akka.util.Timeout
import org.bitcoins.core.protocol.blockchain.{Block, BlockHeader, TestNetChainParams}
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.zmq._
import org.bitcoins.spvnode.constant.Constants
import org.bitcoins.spvnode.models.{BlockHeaderDAO, TransactionDAO, TransactionTable}
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.networking.sync.BlockHeaderSyncActor
import slick.driver.PostgresDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
/**
  * Created by chris on 8/29/16.
  */
object Main extends App {
  implicit val timeout = Timeout(5.seconds)

  override def main(args : Array[String]) = {
    implicit val ec = Constants.actorSystem.dispatcher
    val blockHeaderTable = TableQuery[BlockHeaderTable]
    val txTable = TableQuery[TransactionTable]
    val db = Constants.database
    Await.result(Constants.database.run(blockHeaderTable.schema.create),3.seconds)
    Await.result(Constants.database.run(txTable.schema.create), 3.seconds)
    db.close()
    val txListener: Option[Transaction => Future[Unit]] = Some { tx : Transaction =>
      val txDAO = TransactionDAO.apply(Constants.actorSystem,Constants.dbConfig)
      val created = (txDAO ? TransactionDAO.Create(tx)).mapTo[Transaction]
      created.map { _ =>
        txDAO ! PoisonPill
        Unit
      }
    }

    val blockListener: Option[Block => Future[Unit]] = Some { block: Block =>
      val header = block.blockHeader
      val blockHeaderDAO = BlockHeaderDAO(Constants.actorSystem,Constants.dbConfig)
      val created = (blockHeaderDAO ? BlockHeaderDAO.Create(header)).mapTo[BlockHeader]
      created.map { _ =>
        blockHeaderDAO ! PoisonPill
        Unit
      }
    }
    val socket = new InetSocketAddress("tcp://127.0.0.1", 28332)
    val zmqSub = new ZMQSubscriber[Transaction,Block](socket,None,None,txListener,blockListener)
    zmqSub.start

/*
    val gensisBlockHash = TestNetChainParams.genesisBlock.blockHeader.hash
    val startHeader = BlockHeaderSyncActor.StartHeaders(Seq(gensisBlockHash))

    Constants.database.executor*/
    val blockHeaderSyncActor = BlockHeaderSyncActor(Constants.actorSystem, Constants.dbConfig, Constants.networkParameters)
    blockHeaderSyncActor ! BlockHeaderSyncActor.StartAtLastSavedHeader
  }

}
