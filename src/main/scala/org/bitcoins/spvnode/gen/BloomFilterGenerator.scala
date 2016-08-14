package org.bitcoins.spvnode.gen

import org.bitcoins.core.gen.{CryptoGenerators, NumberGenerator}
import org.bitcoins.spvnode.bloom._
import org.scalacheck.Gen

/**
  * Created by chris on 8/7/16.
  */
trait BloomFilterGenerator {

  /** Builds a generic bloom filter loaded with no hashes and returns it */
  def bloomFilter: Gen[BloomFilter] = for {
    size <- Gen.choose(1,100)
    falsePositiveRate <- Gen.choose(0.00001, 0.99999)
    tweak <- NumberGenerator.uInt32s
    flags <- bloomFlag
  } yield BloomFilter(size,falsePositiveRate, tweak, flags)


  /** Loads a generic bloom filter with the given byte vectors and returns it */
  def bloomFilter(byteVectors : Seq[Seq[Byte]]): Gen[BloomFilter] = for {
    filter <- bloomFilter
  } yield filter.insertByteVectors(byteVectors)

  /** Returns a bloom filter loaded with randomly generated byte vectors */
  def loadedBloomFilter: Gen[(BloomFilter,Seq[Seq[Byte]])] = for {
    filter <- bloomFilter
    randomNum <- Gen.choose(0,filter.filterSize.num.toInt)
    hashes <- CryptoGenerators.doubleSha256DigestSeq(randomNum)
    loaded = filter.insertHashes(hashes)
  } yield (loaded,hashes.map(_.bytes))

  def bloomFlag: Gen[BloomFlag] = for {
    randomNum <- Gen.choose(0,2)
  } yield {
    if (randomNum == 0) BloomUpdateNone
    else if (randomNum == 1) BloomUpdateAll
    else BloomUpdateP2PKOnly
  }

}

object BloomFilterGenerator extends BloomFilterGenerator