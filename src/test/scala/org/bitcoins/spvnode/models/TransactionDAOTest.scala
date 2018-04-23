package org.bitcoins.spvnode.models

import akka.actor.{ActorSystem, PoisonPill}
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import org.bitcoins.core.protocol.transaction.{Transaction}
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import slick.driver.PostgresDriver.api._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
class TransactionDAOTest extends TestKit(ActorSystem("TransactionDAOTest"))
  with ScalaFutures with FlatSpecLike with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  private val logger = BitcoinSLogger.logger
  val table = TableQuery[TransactionTable]
  val database: Database = TestConstants.database
  implicit val timeout = Timeout(5.seconds)
  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(table.schema.create), 10.seconds)
  }


  "TransactionDAO" must "insert and read a tx" in {
    val (txDAO,probe) = txDAORef
    val tx = TestUtil.transaction
    val created = (txDAO ? TransactionDAO.Create(tx)).mapTo[Transaction]
    val read: Future[Option[Transaction]] = created.flatMap { tx =>
      logger.info(s"created tx in test $tx")
      (txDAO ? TransactionDAO.Read(tx.txId)).mapTo[Option[Transaction]]
    }
    whenReady(read, timeout(5.seconds), interval(100.millis)) { txOpt =>
      txOpt.get must be (tx)
      txDAO ! PoisonPill
    }
  }


  private def txDAORef: (TestActorRef[TransactionDAO], TestProbe) = {
    val probe = TestProbe()
    val txDAO: TestActorRef[TransactionDAO] = TestActorRef(TransactionDAO.props(TestConstants),probe.ref)
    (txDAO,probe)
  }

  after {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(table.schema.drop),10.seconds)
  }

  override def afterAll = {
    database.close()
    TestKit.shutdownActorSystem(system)
  }


}
