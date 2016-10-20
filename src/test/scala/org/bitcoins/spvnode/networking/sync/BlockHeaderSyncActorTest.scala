package org.bitcoins.spvnode.networking.sync

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.bitcoins.core.config.{MainNet, TestNet3}
import org.bitcoins.core.gen.BlockchainElementsGenerator
import org.bitcoins.core.protocol.blockchain.{BlockHeader, MainNetChainParams, TestNetChainParams}
import org.bitcoins.spvnode.constant.{Constants, TestConstants}
import org.bitcoins.spvnode.messages.data.HeadersMessage
import org.bitcoins.spvnode.models.BlockHeaderDAO
import org.bitcoins.spvnode.modelsd.BlockHeaderTable
import org.bitcoins.spvnode.networking.sync.BlockHeaderSyncActor.BlockHeaderDifficultyFailure
import org.bitcoins.spvnode.util.TestUtil
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
  * Created by chris on 9/13/16.
  */
class BlockHeaderSyncActorTest extends TestKit(ActorSystem("BlockHeaderSyncActorSpec"))
  with ImplicitSender with FlatSpecLike with MustMatchers with BeforeAndAfter with  BeforeAndAfterAll {

  val genesisHeader = TestNetChainParams.genesisBlock.blockHeader
  val genesisBlockHash = genesisHeader.hash
  val table = TableQuery[BlockHeaderTable]
  val database: Database = TestConstants.database

  before {
    TestUtil.createBlockHeaderTable(system)
  }

  "BlockHeaderSyncActor" must "send us an error if we receive two block headers that are not connected" in {
    val (b,probe) = TestUtil.blockHeaderSyncActor(system)
    val blockHeader1 = BlockchainElementsGenerator.blockHeader(genesisBlockHash).sample.get
    val blockHeader2 = BlockchainElementsGenerator.blockHeader.sample.get
    val headersMsg = HeadersMessage(Seq(blockHeader2))
    b ! BlockHeaderSyncActor.StartHeaders(Seq(blockHeader1))
    b ! headersMsg
    val errorMsg = probe.expectMsgType[BlockHeaderSyncActor.BlockHeadersDoNotConnect]
    errorMsg must be (BlockHeaderSyncActor.BlockHeadersDoNotConnect(blockHeader1,blockHeader2))
    b ! PoisonPill
  }

  it must "stop syncing when we do not receive 2000 block headers from our peer" in {
    val (b,probe) = TestUtil.blockHeaderSyncActor(system)
    b ! BlockHeaderSyncActor.StartHeaders(Seq(TestNetChainParams.genesisBlock.blockHeader))
    val headersMsg = HeadersMessage(TestUtil.firstFiveTestNetBlockHeaders)
    system.scheduler.scheduleOnce(2.seconds, b, headersMsg)(system.dispatcher)
    val reply = probe.expectMsgType[BlockHeaderSyncActor.SuccessfulSyncReply](7.seconds)
    reply.lastHeader must be (TestUtil.firstFiveTestNetBlockHeaders.last)
    b ! PoisonPill
  }

  it must "successfully check two block headers if their difficulty is the same" in {
    val firstHeader = BlockchainElementsGenerator.blockHeader.sample.get
    //note that this header properly references the previous header, but nBits are different
    val secondHeader = BlockchainElementsGenerator.blockHeader(firstHeader.hash,firstHeader.nBits).sample.get
    val checkHeaderResult = BlockHeaderSyncActor.checkHeaders(firstHeader, Seq(secondHeader),0,MainNet)

    checkHeaderResult.error.isDefined must be (false)
    checkHeaderResult.headers must be (Seq(secondHeader))
  }

  it must "successfully check a sequence of headers if their is a difficulty change on the 2016 block" in {
    val firstHeaders = BlockchainElementsGenerator.validHeaderChain(2015,genesisHeader).sample.get
    val lastHeader = BlockchainElementsGenerator.blockHeader(firstHeaders.last.hash).sample.get
    val headers = firstHeaders ++ Seq(lastHeader)
    val checkHeaderResult = BlockHeaderSyncActor.checkHeaders(genesisHeader,headers.tail,1,MainNet)
    checkHeaderResult.error must be (None)
    checkHeaderResult.headers must be (headers.tail)
  }

  it must "fail a checkHeader on a sequence of headers if their is a difficulty change on the 2015 or 2017 block" in {
    val firstHeaders = BlockchainElementsGenerator.validHeaderChain(2015,genesisHeader).sample.get
    val lastHeader = BlockchainElementsGenerator.blockHeader(firstHeaders.last.hash).sample.get
    val headers = firstHeaders ++ Seq(lastHeader)
    val checkHeaderResult = BlockHeaderSyncActor.checkHeaders(headers.head,headers.tail,0,MainNet)
    checkHeaderResult.error.get.isInstanceOf[BlockHeaderDifficultyFailure] must be (true)
    checkHeaderResult.headers must be (headers.tail)

    val firstHeaders2 = BlockchainElementsGenerator.validHeaderChain(2017,genesisHeader).sample.get
    val lastHeader2 = BlockchainElementsGenerator.blockHeader(firstHeaders2.last.hash).sample.get
    val headers2 = firstHeaders2 ++ Seq(lastHeader2)
    val checkHeaderResult2 = BlockHeaderSyncActor.checkHeaders(headers2.head,headers2.tail,0,MainNet)
    checkHeaderResult2.error.get.isInstanceOf[BlockHeaderDifficultyFailure] must be (true)
    checkHeaderResult2.headers must be (headers2.tail)
  }

  it must "fail to check two block headers if the network difficulty isn't correct" in {
    val firstHeader = BlockchainElementsGenerator.blockHeader.sample.get
    //note that this header properly references the previous header, but nBits are different
    val secondHeader = BlockchainElementsGenerator.blockHeader(firstHeader.hash).sample.get
    val checkHeaderResult = BlockHeaderSyncActor.checkHeaders(firstHeader, Seq(secondHeader),0,MainNet)

    val errorMsg = checkHeaderResult.error.get.asInstanceOf[BlockHeaderSyncActor.BlockHeaderDifficultyFailure]

    errorMsg.lastValidBlockHeader must be (firstHeader)
    errorMsg.firstInvalidBlockHeader must be (secondHeader)
  }

  after {
    TestUtil.dropBlockHeaderTable
  }


  override def afterAll = {
    database.close()
    TestKit.shutdownActorSystem(system)
  }
}
