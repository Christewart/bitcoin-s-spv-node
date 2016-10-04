package org.bitcoins.spvnode.utxo

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.gen.BlockchainElementsGenerator
import org.bitcoins.core.protocol.blockchain.TestNetChainParams
import org.bitcoins.core.protocol.script.EmptyScriptSignature
import org.bitcoins.core.protocol.transaction.{Transaction, TransactionConstants, TransactionInput, TransactionOutPoint}
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.gen.UTXOGenerator
import org.bitcoins.spvnode.messages.MsgBlock
import org.bitcoins.spvnode.messages.data.{Inventory, InventoryMessage, TransactionMessage}
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

    val (utxoHandler,probe) = TestUtil.utxoHandlerRef(system)
    utxoHandler ! txMessage

    val processedMsg = probe.expectMsgType[UTXOHandlerActor.Processed](10.seconds)
    processedMsg.dataPayload must be (txMessage)

    //now make sure the utxo was updated to spent in the db
    utxoStateDAO ! UTXODAO.Read(utxo.id.get)
    val readReply = utxoStateDAOProbe.expectMsgType[UTXODAO.ReadReply]

    readReply.utxo.get.state must be (SpentUnconfirmed())
  }

  it must "transition a utxo from ReceivedUnconfirmed -> Spendable when we receive sufficient blocks" in {
    val header = BlockchainElementsGenerator.blockHeader(genesisHash).sample.get
    val utxo = TestUtil.createUtxo(header, ReceivedUnconfirmed(), system)

    val h1 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(header.hash).sample.get,system)
    val h2 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h1.hash).sample.get,system)
    val h3 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h2.hash).sample.get,system)
    val h4 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h3.hash).sample.get,system)
    val h5 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h4.hash).sample.get,system)


    //now if we send h5 to utxo handler, we should have utxo transition from ReceivedUnconfirmed -> Spendable
    val (utxoHandler,probe) = TestUtil.utxoHandlerRef(system)
    val invMsg = InventoryMessage(Seq(Inventory(MsgBlock,h5.hash)))
    utxoHandler ! invMsg

    val proccessMsg = probe.expectMsgType[UTXOHandlerActor.Processed]
    proccessMsg.dataPayload must be (invMsg)

    val (utxoDAO, utxoDAOProbe) = TestUtil.utxoDAORef(system)
    utxoDAO ! UTXODAO.Read(utxo.id.get)
    val updatedUTXO = utxoDAOProbe.expectMsgType[UTXODAO.ReadReply]
    updatedUTXO.utxo.get.state must be (Spendable)

    utxoDAO ! PoisonPill
    utxoHandler ! PoisonPill
  }

  it must "NOT transaction transition a utxo from ReceivedUnconfirmed -> Spendable when we have not received blocks" in {
    val header = BlockchainElementsGenerator.blockHeader(genesisHash).sample.get
    val utxo = TestUtil.createUtxo(header, ReceivedUnconfirmed(), system)

    //NOTE: Currently we require 6 confirmations to transition a utxo from Unconfirmed -> Confirmed
    //this is only 4 block headers, thus should not transition the utxo
    val h1 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(header.hash).sample.get,system)
    val h2 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h1.hash).sample.get,system)
    val h3 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h2.hash).sample.get,system)
    val h4 = TestUtil.createHeader(BlockchainElementsGenerator.blockHeader(h3.hash).sample.get,system)

    val (utxoHandler,probe) = TestUtil.utxoHandlerRef(system)
    val invMsg = InventoryMessage(Seq(Inventory(MsgBlock,h4.hash)))
    utxoHandler ! invMsg

    val proccessMsg = probe.expectMsgType[UTXOHandlerActor.Processed]
    proccessMsg.dataPayload must be (invMsg)

    val (utxoDAO, utxoDAOProbe) = TestUtil.utxoDAORef(system)
    utxoDAO ! UTXODAO.Read(utxo.id.get)
    val updatedUTXO = utxoDAOProbe.expectMsgType[UTXODAO.ReadReply]
    updatedUTXO.utxo.get.state must be (ReceivedUnconfirmed())

    utxoDAO ! PoisonPill
    utxoHandler ! PoisonPill
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
}
