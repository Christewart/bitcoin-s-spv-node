package org.bitcoins.spvnode.models

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.gen.{CryptoGenerators, NumberGenerator, TransactionGenerators}
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.gen.UTXOGenerator
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.utxo._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import slick.driver.PostgresDriver.api._

/**
  * Created by chris on 9/24/16.
  */
class UTXODAOTest extends TestKit(ActorSystem("BlockHeaderDAOTest")) with ImplicitSender
  with FlatSpecLike with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {

  val table = TableQuery[UTXOTable]
  val database: Database = TestConstants.database

  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(table.schema.create), 10.seconds)
  }

  "UTXODAO" must "create a utxo to track in the database" in {
    val (utxoDAO, probe) = utxoDAORef
    val u = UTXOGenerator.utxo.sample.get
    val createMsg = UTXODAO.Create(u)
    utxoDAO ! createMsg
    val created = probe.expectMsgType[UTXODAO.Created]

    //make sure we can read it
    val read = UTXODAO.Read(created.utxo.id.get)
    utxoDAO ! read

    val readMsg = probe.expectMsgType[UTXODAO.ReadReply]
    readMsg.utxo.get must be (created.utxo)
  }

  it must "find all outputs by a given set of txids" in {
    val (utxoDAO, probe) = utxoDAORef
    val u1 = UTXOGenerator.utxo.sample.get
    val u2 = UTXOGenerator.utxo.sample.get

    val createMsg1 = UTXODAO.Create(u1)
    utxoDAO ! createMsg1
    val created1 = probe.expectMsgType[UTXODAO.Created]

    val createMsg2 = UTXODAO.Create(u2)
    utxoDAO ! createMsg2
    val created2 = probe.expectMsgType[UTXODAO.Created]

    val txids = Seq(u1.txId, u2.txId)
    utxoDAO ! UTXODAO.FindTxIds(txids)

    val foundTxIds = probe.expectMsgType[UTXODAO.FindTxIdsReply]
    val expectedUtxoStates = Seq(created1,created2).map(_.utxo)
    foundTxIds.utxos must be (expectedUtxoStates)
  }

  it must "update a utxo to be spent" in {
    val (utxoDAO, probe) = utxoDAORef
    val u = UTXOGenerator.utxo(Spendable).sample.get

    val createMsg1 = UTXODAO.Create(u)
    utxoDAO ! createMsg1
    val created1 = probe.expectMsgType[UTXODAO.Created]

    val utxoIsSpent = UTXO(created1.utxo.id, created1.utxo.output, created1.utxo.vout, created1.utxo.txId,
      created1.utxo.blockHash,Spent)

    utxoDAO ! UTXODAO.Update(utxoIsSpent)

    val updatedMsg = probe.expectMsgType[UTXODAO.UpdateReply]
    updatedMsg.utxo.get must be (utxoIsSpent)

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

  private def utxoDAORef: (TestActorRef[UTXODAO], TestProbe) = {
    val probe = TestProbe()
    val utxoDAO: TestActorRef[UTXODAO] = TestActorRef(UTXODAO.props(TestConstants),probe.ref)
    (utxoDAO,probe)
  }

}
