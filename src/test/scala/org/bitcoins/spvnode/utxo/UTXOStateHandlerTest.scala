package org.bitcoins.spvnode.utxo

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.protocol.script.EmptyScriptSignature
import org.bitcoins.core.protocol.transaction.{Transaction, TransactionConstants, TransactionInput, TransactionOutPoint}
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.gen.UTXOGenerator
import org.bitcoins.spvnode.messages.data.TransactionMessage
import org.bitcoins.spvnode.models.{BlockHeaderDAO, UTXOStateDAO, UTXOStateTable}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import slick.driver.PostgresDriver.api._

/**
  * Created by chris on 9/26/16.
  */
class UTXOStateHandlerTest extends TestKit(ActorSystem("BlockHeaderDAOTest")) with ImplicitSender
  with FlatSpecLike with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {

  val table = TableQuery[UTXOStateTable]
  val database: Database = TestConstants.database

  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(table.schema.create), 10.seconds)
  }

  "UTXOStateHandler" must "update the state of a utxo to spent if we see a tx that spends it" in {
    //create a utxo in the database
    val utxo = UTXOGenerator.utxoState(false).sample.get
    val (utxoStateDAO,utxoStateDAOProbe) = utxoStateDAORef

    utxoStateDAO ! UTXOStateDAO.Create(utxo)
    val createdUTXO = utxoStateDAOProbe.expectMsgType[UTXOStateDAO.Created]
    //make sure it isn't spent already
    createdUTXO.uTXOState.isSpent must be (false)

    //build tx that spends this utxo
    //since spv nodes don't check digital signatures we can just check the outpoint
    val outPoint = TransactionOutPoint(utxo.txId, utxo.vout)
    val input = TransactionInput(outPoint,EmptyScriptSignature, TransactionConstants.sequence)
    val spendingTx = Transaction(TransactionConstants.version,Seq(input), Nil, TransactionConstants.lockTime)
    //tx message to send to utxoStateHandler
    val txMessage = TransactionMessage(spendingTx)

    val (utxoStateHandler,probe) = utxoStateHandlerRef
    utxoStateHandler ! txMessage

    val processedMsg = probe.expectMsgType[UTXOStateHandler.Processed](10.seconds)
    processedMsg.dataPayload must be (txMessage)

    //now make sure the utxo was updated to spent in the db
    utxoStateDAO ! UTXOStateDAO.Read(createdUTXO.uTXOState.id.get)
    val readReply = utxoStateDAOProbe.expectMsgType[UTXOStateDAO.ReadReply]

    readReply.utxoState.get.isSpent must be (true)
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

  private def utxoStateHandlerRef: (TestActorRef[UTXOStateHandler], TestProbe) = {
    val probe = TestProbe()
    val utxoStateHandler: TestActorRef[UTXOStateHandler] = TestActorRef(UTXOStateHandler.props(TestConstants),probe.ref)
    (utxoStateHandler,probe)
  }

  private def utxoStateDAORef: (TestActorRef[UTXOStateDAO], TestProbe) = {
    val probe = TestProbe()
    val utxoStateDAO: TestActorRef[UTXOStateDAO] = TestActorRef(UTXOStateDAO.props(TestConstants),probe.ref)
    (utxoStateDAO,probe)
  }
}
