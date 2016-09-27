package org.bitcoins.spvnode.models

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.gen.{CryptoGenerators, NumberGenerator, TransactionGenerators}
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.gen.UTXOGenerator
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.utxo.UTXOState
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import slick.driver.PostgresDriver.api._

/**
  * Created by chris on 9/24/16.
  */
class UTXOStateDAOTest extends TestKit(ActorSystem("BlockHeaderDAOTest")) with ImplicitSender
  with FlatSpecLike with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {

  val table = TableQuery[UTXOStateTable]
  val database: Database = TestConstants.database

  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(table.schema.create), 10.seconds)
  }

  "UTXOStateDAO" must "create a utxo to track in the database" in {
    val (utxoStateDAO, probe) = utxoStateDAORef
    val u = UTXOGenerator.utxoState.sample.get
    val createMsg = UTXOStateDAO.Create(u)
    utxoStateDAO ! createMsg
    val created = probe.expectMsgType[UTXOStateDAO.Created]

    //make sure we can read it
    val read = UTXOStateDAO.Read(created.uTXOState.id.get)
    utxoStateDAO ! read

    val readMsg = probe.expectMsgType[UTXOStateDAO.ReadReply]
    readMsg.utxoState.get must be (created.uTXOState)
  }

  it must "find all outputs by a given set of txids" in {
    val (utxoStateDAO, probe) = utxoStateDAORef
    val u1 = UTXOGenerator.utxoState.sample.get
    val u2 = UTXOGenerator.utxoState.sample.get

    val createMsg1 = UTXOStateDAO.Create(u1)
    utxoStateDAO ! createMsg1
    val created1 = probe.expectMsgType[UTXOStateDAO.Created]

    val createMsg2 = UTXOStateDAO.Create(u2)
    utxoStateDAO ! createMsg2
    val created2 = probe.expectMsgType[UTXOStateDAO.Created]

    val txids = Seq(u1.txId, u2.txId)
    utxoStateDAO ! UTXOStateDAO.FindTxIds(txids)

    val foundTxIds = probe.expectMsgType[UTXOStateDAO.FindTxIdsReply]
    val expectedUtxoStates = Seq(created1,created2).map(_.uTXOState)
    foundTxIds.utxoStates must be (expectedUtxoStates)
  }

  it must "update a utxo to be spent" in {
    val (utxoStateDAO, probe) = utxoStateDAORef
    val u = UTXOGenerator.utxoState(false).sample.get

    val createMsg1 = UTXOStateDAO.Create(u)
    utxoStateDAO ! createMsg1
    val created1 = probe.expectMsgType[UTXOStateDAO.Created]

    val utxoIsSpent = UTXOState(created1.uTXOState.id, created1.uTXOState.output, created1.uTXOState.vout, created1.uTXOState.txId,
      created1.uTXOState.blockHash,true)

    utxoStateDAO ! UTXOStateDAO.Update(utxoIsSpent)

    val updatedMsg = probe.expectMsgType[UTXOStateDAO.UpdateReply]
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

  private def utxoStateDAORef: (TestActorRef[UTXOStateDAO], TestProbe) = {
    val probe = TestProbe()
    val utxoStateDAO: TestActorRef[UTXOStateDAO] = TestActorRef(UTXOStateDAO.props(TestConstants),probe.ref)
    (utxoStateDAO,probe)
  }

}
