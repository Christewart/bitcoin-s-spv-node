package org.bitcoins.spvnode.store

import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.gen.{BlockchainElementsGenerator, NumberGenerator}
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.core.protocol.transaction.EmptyTransaction
import org.bitcoins.core.util.CryptoUtil
import org.scalatest.{BeforeAndAfter, FlatSpec, MustMatchers}

/**
  * Created by chris on 9/5/16.
  */
class BlockHeaderStoreTest extends FlatSpec with MustMatchers with BeforeAndAfter {
  val testFile = new java.io.File("src/test/resources/block_header.dat")
  "BlockHeaderStore" must "write and then read a block header from the database" in {
    val blockHeader = buildBlockHeader(CryptoUtil.emptyDoubleSha256Hash)
    BlockHeaderStore.append(Seq(blockHeader),testFile)
    val headersFromFile = BlockHeaderStore.read(testFile)

    headersFromFile must be (Seq(blockHeader))
  }


  it must "write one blockheader to the file, then append another header to the file, then read them both" in {
    val blockHeader1 = buildBlockHeader(CryptoUtil.emptyDoubleSha256Hash)
    val blockHeader2 = buildBlockHeader(CryptoUtil.emptyDoubleSha256Hash)
    BlockHeaderStore.append(Seq(blockHeader1),testFile)
    val headersFromFile1 = BlockHeaderStore.read(testFile)
    headersFromFile1 must be (Seq(blockHeader1))

    BlockHeaderStore.append(Seq(blockHeader2),testFile)
    val headersFromFile2 = BlockHeaderStore.read(testFile)
    headersFromFile2 must be (Seq(blockHeader1, blockHeader2))
  }


  after {
    testFile.delete()
  }

  private def buildBlockHeader(prevBlockHash: DoubleSha256Digest): BlockHeader = {
    //nonce for the unique hash
    val nonce = NumberGenerator.uInt32s.sample.get
    BlockHeader(UInt32.one,prevBlockHash,EmptyTransaction.txId,UInt32.one,UInt32.one, nonce)
  }

}
