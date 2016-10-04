package org.bitcoins.spvnode.models

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.gen.{BlockchainElementsGenerator, CryptoGenerators, NumberGenerator, TransactionGenerators}
import org.bitcoins.core.protocol.blockchain.{BlockHeader, TestNetChainParams}
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.gen.UTXOGenerator
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.util.TestUtil
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

  val headerTable = TableQuery[BlockHeaderTable]
  val utxoTable = TableQuery[UTXOTable]
  val database: Database = TestConstants.database
  val genesisHeader = TestNetChainParams.genesisBlock.blockHeader
  val genesisHash = genesisHeader.hash
  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(headerTable.schema.create), 10.seconds)
    //create genesis header
    val (blockHeaderDAO, probe) = TestUtil.blockHeaderDAORef(system)
    blockHeaderDAO ! BlockHeaderDAO.Create(genesisHeader)
    probe.expectMsgType[BlockHeaderDAO.CreateReply]
    blockHeaderDAO ! PoisonPill
    Await.result(database.run(utxoTable.schema.create), 10.seconds)
  }

  "UTXODAO" must "create a utxo to track in the database" in {
    val (blockHeaderDAO, blockHeaderDAOProbe) = TestUtil.blockHeaderDAORef(system)
    val header = BlockchainElementsGenerator.blockHeader(genesisHash).sample.get

    blockHeaderDAO ! BlockHeaderDAO.Create(header)
    blockHeaderDAOProbe.expectMsgType[BlockHeaderDAO.CreateReply]

    val (utxoDAO, probe) = TestUtil.utxoDAORef(system)
    val u = UTXOGenerator.utxoBlockHash(header.hash).sample.get
    val createMsg = UTXODAO.Create(u)
    utxoDAO ! createMsg
    val created = probe.expectMsgType[UTXODAO.CreateReply]

    //make sure we can read it
    val read = UTXODAO.Read(created.utxo.id.get)
    utxoDAO ! read

    val readMsg = probe.expectMsgType[UTXODAO.ReadReply]
    readMsg.utxo.get must be (created.utxo)
    blockHeaderDAO ! PoisonPill
    utxoDAO ! PoisonPill
  }

  it must "find all outputs by a given set of txids" in {
    val (utxoDAO, probe) = TestUtil.utxoDAORef(system)
    val header = BlockchainElementsGenerator.blockHeader(genesisHash).sample.get
    val u1 = TestUtil.createUtxo(header,system)
    val header2 = BlockchainElementsGenerator.blockHeader(header.hash).sample.get
    val u2 = TestUtil.createUtxo(header2,system)

    val txids = Seq(u1.txId, u2.txId)
    utxoDAO ! UTXODAO.FindTxIds(txids)

    val foundTxIds = probe.expectMsgType[UTXODAO.FindTxIdsReply]
    val expectedUtxoStates = Seq(u1,u2)
    foundTxIds.utxos must be (expectedUtxoStates)

    utxoDAO ! PoisonPill
  }

  it must "update a utxo to be spent" in {
    val (utxoDAO, probe) = TestUtil.utxoDAORef(system)
    val header = BlockchainElementsGenerator.blockHeader(genesisHash).sample.get
    val u = TestUtil.createUtxo(header,Spendable, system)

    val utxoIsSpent = UTXO(u.id, u.output, u.vout, u.txId,
      u.blockHash,Spent)

    utxoDAO ! UTXODAO.Update(utxoIsSpent)

    val updatedMsg = probe.expectMsgType[UTXODAO.UpdateReply]
    updatedMsg.utxo.get must be (utxoIsSpent)
    utxoDAO ! PoisonPill
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
