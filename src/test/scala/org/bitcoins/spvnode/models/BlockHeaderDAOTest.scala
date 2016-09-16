package org.bitcoins.spvnode.models

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.gen.BlockchainElementsGenerator
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.spvnode.constant.TestConstants
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
  * Created by chris on 9/8/16.
  */
class BlockHeaderDAOTest  extends TestKit(ActorSystem("BlockHeaderDAOTest")) with ImplicitSender
  with FlatSpecLike with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {

  val table = TableQuery[BlockHeaderTable]
  val database: Database = TestConstants.database

  before {
    //Awaits need to be used to make sure this is fully executed before the next test case starts
    //TODO: Figure out a way to make this asynchronous
    Await.result(database.run(table.schema.create), 10.seconds)
  }

  "BlockHeaderDAO" must "store a blockheader in the database, then read it from the database" in {
    val probe = TestProbe()
    val blockHeader = BlockchainElementsGenerator.blockHeader.sample.get
    val blockHeaderDAO = TestActorRef(BlockHeaderDAO.props(database),probe.ref)
    blockHeaderDAO ! BlockHeaderDAO.Create(blockHeader)
    val createdHeader = probe.expectMsgType[BlockHeaderDAO.CreatedHeader]
    createdHeader.header must be (blockHeader)

    blockHeaderDAO ! BlockHeaderDAO.Read(blockHeader.hash)
    val readHeader = probe.expectMsgType[BlockHeaderDAO.ReadReply]
    readHeader.hash.get must be (blockHeader)
  }

  it must "be able to create multiple block headers in our database at once" in {
    val probe = TestProbe()
    val blockHeaderDAO = TestActorRef(BlockHeaderDAO.props(database),probe.ref)
    val blockHeader1 = BlockchainElementsGenerator.blockHeader.sample.get
    val blockHeader2 = BlockchainElementsGenerator.blockHeader.sample.get

    val headers = Seq(blockHeader1,blockHeader2)

    blockHeaderDAO ! BlockHeaderDAO.CreateAll(headers)

    val actualBlockHeaders = probe.expectMsgType[BlockHeaderDAO.CreatedHeaders]
    actualBlockHeaders.headers must be (headers)


  }

  it must "delete a block header in the database" in {
    val probe = TestProbe()
    val blockHeader = BlockchainElementsGenerator.blockHeader.sample.get
    val blockHeaderDAO = TestActorRef(BlockHeaderDAO.props(database),probe.ref)
    blockHeaderDAO ! BlockHeaderDAO.Create(blockHeader)
    val createdHeader = probe.expectMsgType[BlockHeaderDAO.CreatedHeader]

    //delete the header in the db
    blockHeaderDAO ! BlockHeaderDAO.Delete(blockHeader)
    val deleteReply = probe.expectMsgType[BlockHeaderDAO.DeleteReply]
    deleteReply.blockHeader.get must be (blockHeader)

    //make sure we cannot read our deleted header
    blockHeaderDAO ! BlockHeaderDAO.Read(blockHeader.hash)
    val readHeader = probe.expectMsgType[BlockHeaderDAO.ReadReply]
    readHeader.hash must be (None)
  }

  it must "retrieve the last block header saved in the database" in {
    val probe = TestProbe()
    val blockHeader = BlockchainElementsGenerator.blockHeader.sample.get
    val blockHeaderDAO = TestActorRef(BlockHeaderDAO.props(database),probe.ref)
    blockHeaderDAO ! BlockHeaderDAO.Create(blockHeader)
    probe.expectMsgType[BlockHeaderDAO.CreatedHeader]

    blockHeaderDAO ! BlockHeaderDAO.LastSavedHeader
    val lastSavedHeader = probe.expectMsgType[BlockHeaderDAO.LastSavedHeaderReply]
    lastSavedHeader.headers.head must be (blockHeader)

    //insert another header and make sure that is the new last header
    val blockHeader2 = BlockchainElementsGenerator.blockHeader.sample.get
    blockHeaderDAO ! BlockHeaderDAO.Create(blockHeader2)
    probe.expectMsgType[BlockHeaderDAO.CreatedHeader]

    blockHeaderDAO ! BlockHeaderDAO.LastSavedHeader
    val lastSavedHeader2 = probe.expectMsgType[BlockHeaderDAO.LastSavedHeaderReply]
    lastSavedHeader2.headers.head must be (blockHeader2)
  }


  it must "return none when retrieving block headers from an empty database" in {
    val probe = TestProbe()
    val blockHeader = BlockchainElementsGenerator.blockHeader.sample.get
    val blockHeaderDAO = TestActorRef(BlockHeaderDAO.props(database),probe.ref)

    blockHeaderDAO ! BlockHeaderDAO.LastSavedHeader

    val lastSavedHeader = probe.expectMsgType[BlockHeaderDAO.LastSavedHeaderReply]
    lastSavedHeader.headers.headOption must be (None)
  }

  it must "retrieve a block header by height" in {
    val probe = TestProbe()
    val blockHeader = BlockchainElementsGenerator.blockHeader.sample.get
    val blockHeaderDAO = TestActorRef(BlockHeaderDAO.props(database),probe.ref)
    blockHeaderDAO ! BlockHeaderDAO.Create(blockHeader)

    probe.expectMsgType[BlockHeaderDAO.CreatedHeader]

    blockHeaderDAO ! BlockHeaderDAO.GetAtHeight(1)

    val blockHeaderAtHeight = probe.expectMsgType[BlockHeaderDAO.BlockHeaderAtHeight]
    blockHeaderAtHeight.headers.head must be (blockHeader)
    blockHeaderAtHeight.height must be (1)

    //create one at height 2
    val blockHeader2 = BlockchainElementsGenerator.blockHeader.sample.get
    blockHeaderDAO ! BlockHeaderDAO.Create(blockHeader2)

    probe.expectMsgType[BlockHeaderDAO.CreatedHeader]

    blockHeaderDAO ! BlockHeaderDAO.GetAtHeight(2)

    val blockHeaderAtHeight2 = probe.expectMsgType[BlockHeaderDAO.BlockHeaderAtHeight]

    blockHeaderAtHeight2.headers.head must be (blockHeader2)
    blockHeaderAtHeight2.height must be (2)

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