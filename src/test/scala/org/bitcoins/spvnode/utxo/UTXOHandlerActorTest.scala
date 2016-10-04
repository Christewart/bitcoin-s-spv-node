package org.bitcoins.spvnode.utxo

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.gen.BlockchainElementsGenerator
import org.bitcoins.core.protocol.blockchain.TestNetChainParams
import org.bitcoins.core.protocol.script.EmptyScriptSignature
import org.bitcoins.core.protocol.transaction.{Transaction, TransactionConstants, TransactionInput, TransactionOutPoint}
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.gen.UTXOGenerator
import org.bitcoins.spvnode.messages.data.TransactionMessage
import org.bitcoins.spvnode.models.{BlockHeaderDAO, UTXODAO, UTXOTable}
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
  * Created by chris on 9/26/16.
  */
class UTXOHandlerActorTest extends TestKit(ActorSystem("BlockHeaderDAOTest")) with ImplicitSender
  with FlatSpecLike with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {

  val utxoTable = TableQuery[UTXOTable]
  val headerTable = TableQuery[BlockHeaderTable]
  val database: Database = TestConstants.database
  val genesisHeader = TestNetChainParams.genesisBlock.blockHeader
  val genesisHash = genesisHeader.hash

  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(headerTable.schema.create), 10.seconds)
    //create genesis header in db
    val (blockHeaderDAO, probe) = TestUtil.blockHeaderDAORef(system)
    blockHeaderDAO ! BlockHeaderDAO.Create(genesisHeader)
    probe.expectMsgType[BlockHeaderDAO.CreateReply]
    blockHeaderDAO ! PoisonPill
    Await.result(database.run(utxoTable.schema.create), 10.seconds)
  }

  "UTXOStateHandler" must "update the state of a utxo to spent if we see a tx that spends it" in {
    val header = BlockchainElementsGenerator.blockHeader(genesisHash).sample.get
    val utxo = TestUtil.createUtxo(header,Spendable,system)
    val (utxoStateDAO,utxoStateDAOProbe) = TestUtil.utxoDAORef(system)

    //make sure it isn't spent already
    utxo.state must be (Spendable)

    //build tx that spends this utxo
    //since spv nodes don't check digital signatures we can just check the outpoint
    val outPoint = TransactionOutPoint(utxo.txId, utxo.vout)
    val input = TransactionInput(outPoint,EmptyScriptSignature, TransactionConstants.sequence)
    val spendingTx = Transaction(TransactionConstants.version,Seq(input), Nil, TransactionConstants.lockTime)
    //tx message to send to utxoStateHandler
    val txMessage = TransactionMessage(spendingTx)

    val (utxoStateHandler,probe) = utxoStateHandlerRef
    utxoStateHandler ! txMessage

    val processedMsg = probe.expectMsgType[UTXOHandlerActor.Processed](10.seconds)
    processedMsg.dataPayload must be (txMessage)

    //now make sure the utxo was updated to spent in the db
    utxoStateDAO ! UTXODAO.Read(utxo.id.get)
    val readReply = utxoStateDAOProbe.expectMsgType[UTXODAO.ReadReply]

    readReply.utxo.get.state must be (SpentUnconfirmed())
  }

  it must "transition a tx from ReceivedUnconfirmed -> Spendable" in {
    
  }

  after {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(utxoTable.schema.drop),10.seconds)
    Await.result(database.run(headerTable.schema.drop),10.seconds)
  }

  override def afterAll = {
    database.close()
    TestKit.shutdownActorSystem(system)
  }

  private def utxoStateHandlerRef: (TestActorRef[UTXOHandlerActor], TestProbe) = {
    val probe = TestProbe()
    val utxoStateHandler: TestActorRef[UTXOHandlerActor] = TestActorRef(UTXOHandlerActor.props(TestConstants),probe.ref)
    (utxoStateHandler,probe)
  }
}
