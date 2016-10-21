package org.bitcoins.spvnode.networking.sync

import akka.actor.{Actor, ActorRef, ActorRefFactory, PoisonPill, Props}
import akka.event.LoggingReceive
import org.bitcoins.core.config.{MainNet, NetworkParameters, RegTest, TestNet3}
import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.blockchain.BlockHeader
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.{Constants, DbConfig}
import org.bitcoins.spvnode.messages.{GetHeadersMessage, HeadersMessage}
import org.bitcoins.spvnode.messages.data.GetHeadersMessage
import org.bitcoins.spvnode.models.BlockHeaderDAO
import org.bitcoins.spvnode.networking.PeerMessageHandler
import org.bitcoins.spvnode.networking.sync.BlockHeaderSyncActor.{BlockHeadersDoNotConnect, CheckHeaderResult, StartAtLastSavedHeader}
import org.bitcoins.spvnode.util.BitcoinSpvNodeUtil

import scala.annotation.tailrec

/**
  * Created by chris on 9/5/16.
  *
  *
  */
trait BlockHeaderSyncActor extends Actor with BitcoinSLogger {

  /** This is the maximum amount of headers the bitcoin protocol will transmit
    * in one request
    * [[https://bitcoin.org/en/developer-reference#getheaders]]
    *
    * @return
    */
  private def maxHeaders = 2000

  def dbConfig: DbConfig

  /** Helper function to provide a fresh instance of a [[BlockHeaderDAO]] actor */
  private def blockHeaderDAO: ActorRef = BlockHeaderDAO(context, dbConfig)

  /** Helper function to connect to a new peer on the network */
  private def peerMessageHandler: ActorRef = PeerMessageHandler(context,dbConfig)

  def receive = LoggingReceive {
    case startHeader: BlockHeaderSyncActor.StartHeaders =>
      val lastHeader = startHeader.headers.last
      if (lastHeader == Constants.chainParams.genesisBlock.blockHeader) {
        //TODO: Think this causes a bug in our test because the table is being dropped
        //while the message is still being processed
        //seed the database with the genesis header
        logger.info("Switching to awaitGenesisHeaderCreateReply from receive")
        context.become(awaitGenesisHeaderCreateReply)
        blockHeaderDAO ! BlockHeaderDAO.Read(lastHeader.hash)
      } else {
        val getHeadersMsg = GetHeadersMessage(startHeader.headers.map(_.hash))
        logger.info("Switching to blockHeaderSync from receive")
        context.become(blockHeaderSync(startHeader.headers))
        peerMessageHandler ! getHeadersMsg
      }
    case StartAtLastSavedHeader =>
      blockHeaderDAO ! BlockHeaderDAO.FindHeadersForGetHeadersMessage
      logger.info("Switching to awaitHeadersForGetHeadersMessage from receive")
      context.become(awaitFindHeadersForGetHeadersMessage(None))
    case headersMsg: HeadersMessage =>
      context.become(awaitFindHeadersForGetHeadersMessage(Some(headersMsg)))
      blockHeaderDAO ! BlockHeaderDAO.FindHeadersForGetHeadersMessage

  }

  /** Main block header sync context, lastHeader is used to make sure the batch of block headers we see
    * matches connects to the last batch of block headers we saw (thus forming a blockchain)
    *
    * @param getHeaders the message we originally sent to our peer for syncing or chain
    * @return
    */
  def blockHeaderSync(getHeaders: Seq[BlockHeader]): Receive = LoggingReceive {
    case headersMsg: HeadersMessage =>
      val headers = headersMsg.headers
      if (headers.isEmpty) {
        logger.info("We received an empty HeadersMessage inside of BlockHeaderSyncActor, we must be synced with the network")
        self ! PoisonPill
      } else {
        logger.info("Switching to awaitCheckHeaders from blockHeaderSync")
        logger.info("getHeaders: " + getHeaders.map(_.hash))
        logger.info("headersMsg: " + headersMsg.headers.map(_.previousBlockHash))
        //find the header that our peer started giving us block headers from
        val lastHeader = getHeaders.find(_.hash == headersMsg.headers.head.previousBlockHash)
        if (lastHeader.isDefined) {
          context.become(awaitCheckHeaders(lastHeader.get,headers))
          val b = blockHeaderDAO
          b ! BlockHeaderDAO.MaxHeight
        } else {
          context.parent ! BlockHeaderSyncActor.BlockHeadersDoNotConnect(getHeaders.head,headers.head)
        }
      }
  }

  /** This context is for when we need to send a [[org.bitcoins.spvnode.messages.GetHeadersMessage]] to a peer
    * but we need to figure out the hashes to place inside of it. The headers placed inside of the GetHeadersMessage
    * is calculated by [[BlockHeaderDAO]]
    * @return
    */
  def awaitFindHeadersForGetHeadersMessage(headersMsg: Option[HeadersMessage]): Receive = LoggingReceive {
    case headersForGetHeadersMsg: BlockHeaderDAO.FindHeadersForGetHeadersMessageReply =>
      if (headersMsg.isDefined) {
        context.become(blockHeaderSync(headersForGetHeadersMsg.headers))
        self ! headersMsg.get
      } else {
        val p = peerMessageHandler
        p ! GetHeadersMessage(headersForGetHeadersMsg.headers.map(_.hash))
      }
      sender ! PoisonPill
  }

  /** This behavior is responsible for calling the [[checkHeader]] function, after evaluating
    * if the headers are valid
    *
    * The only message this context expects is the [[BlockHeaderDAO]] to send it the current
    * max height of the blockchain that it has stored right now
    *
    * @param lastHeader
    * @param headers
    * @return
    */
  def awaitCheckHeaders(lastHeader: BlockHeader, headers: Seq[BlockHeader]) = LoggingReceive {
    case maxHeight: BlockHeaderDAO.MaxHeightReply =>
      val result = BlockHeaderSyncActor.checkHeaders(lastHeader,headers,maxHeight.height)
      sender ! PoisonPill
      self ! result
    case checkHeaderResult: CheckHeaderResult =>
      logger.info("Check header result: " + checkHeaderResult)
      logger.info("lastHeader: " + lastHeader)
      val validHeaders = checkHeaderResult.error match {
        case None => checkHeaderResult.headers
        case Some(syncError) =>
          logger.error("We had an error syncing our blockchain: " + checkHeaderResult.error.get)
          logger.info("lastValidHash: " + syncError.lastValidBlockHeader.hash)
          logger.info("firstInvalidHash: " + syncError.firstInvalidBlockHeader.hash)
          context.parent ! checkHeaderResult.error.get
          //we can store all headers up until the last valid hash
          val lastValidHeaderIndex = checkHeaderResult.headers.indexOf(syncError.lastValidBlockHeader)
          val (validHeaders,_) = checkHeaderResult.headers.splitAt(lastValidHeaderIndex)
          validHeaders
      }
      if (validHeaders.nonEmpty) handleValidHeaders(validHeaders)
  }

  /** Stores the valid headers in our database, sends our actor a message to start syncing from the last
    * header we received if necessary
    *
    * @param headers
    */
  def handleValidHeaders(headers: Seq[BlockHeader]): Unit = {
    logger.debug("Headers size to be inserted: "  + headers.size)
    val createAllMsg = BlockHeaderDAO.CreateAll(headers)
    val b = blockHeaderDAO
    logger.info("Switching to awaitCreatedAllReply from handleValidHeaders")
    context.become(awaitCreatedAllReply(headers))
    b ! createAllMsg
  }

  /** Waits for our [[BlockHeaderDAO]] to reply with all the headers it created in persistent storage
    *
    * @param networkHeaders the headers we received from our peer on the network. This may be a different
    *                       size than what is returned from [[BlockHeaderDAO.CreateAllReply]]. This can
    *                       be the case if our database fails to insert some of the headers.
    * */
  def awaitCreatedAllReply(networkHeaders: Seq[BlockHeader]): Receive = LoggingReceive {
    case createdHeaders: BlockHeaderDAO.CreateAllReply =>
      val headers = createdHeaders.headers
      val lastHeader = createdHeaders.headers.last
      if (headers.size == maxHeaders || headers.size != networkHeaders.size) {
        //this means we either stored all the headers in the database and need to start from the last header or
        //we failed to store all the headers (due to some error in BlockHeaderDAO) and we need to start from the last
        //header we successfully stored in the database
        context.become(receive)
        self ! StartAtLastSavedHeader
      } else {
        //else we we are synced up on the network, send the parent the last header we have seen
        context.parent ! BlockHeaderSyncActor.SuccessfulSyncReply(lastHeader)
        self ! PoisonPill
      }
      sender ! PoisonPill
  }

  /** This behavior is used to seed the database,
    * we cannot do anything until the genesis header is created in persistent storage */
  def awaitGenesisHeaderCreateReply: Receive = {
    case createReply: BlockHeaderDAO.CreateReply =>
      logger.info("Switching to receive from awaitGenesisHeaderCreateReply")
      context.become(receive)
      self ! StartAtLastSavedHeader
      sender ! PoisonPill
    case read: BlockHeaderDAO.ReadReply =>
      if (read.header.isDefined) {
        context.become(receive)
        val getHeadersMessage = GetHeadersMessage(Seq(read.header.get.hash))
        peerMessageHandler ! getHeadersMessage
        context.become(blockHeaderSync(Seq(read.header.get)))
      } else sender ! BlockHeaderDAO.Create(Constants.chainParams.genesisBlock.blockHeader)
  }
}

object BlockHeaderSyncActor extends BitcoinSLogger {
  private case class BlockHeaderSyncActorImpl(dbConfig: DbConfig) extends BlockHeaderSyncActor

  def apply(context: ActorRefFactory, dbConfig: DbConfig): ActorRef = {
    context.actorOf(props(dbConfig),
      BitcoinSpvNodeUtil.createActorName(this.getClass()))
  }

  def props(dbConfig: DbConfig): Props = {
    Props(classOf[BlockHeaderSyncActorImpl], dbConfig)
  }

  sealed trait BlockHeaderSyncMessage

  sealed trait BlockHeaderSyncMessageRequest
  sealed trait BlockHeaderSyncMessageReply

  /** Indicates a set of headers to query our peer on the network to start our sync process */
  case class StartHeaders(headers: Seq[BlockHeader]) extends BlockHeaderSyncMessageRequest

  /** Starts syncing our blockchain at the last header we have seen, if we haven't see any it starts at the genesis block */
  case object StartAtLastSavedHeader extends BlockHeaderSyncMessageRequest
  /** Reply for [[StartAtLastSavedHeader]] */
  case class StartAtLastSavedHeaderReply(header: BlockHeader) extends BlockHeaderSyncMessageReply


  /** Indicates that we have successfully synced our blockchain, the [[lastHeader]] represents the header at the max height on the chain */
  case class SuccessfulSyncReply(lastHeader: BlockHeader) extends BlockHeaderSyncMessageReply

  /** Indicates an error happened during the sync of our blockchain */
  sealed trait BlockHeaderSyncError extends BlockHeaderSyncMessageReply {
    /** The last valid block header in the chain */
    def lastValidBlockHeader: BlockHeader
    /** The first block header that does not properly refer to the previous block header */
    def firstInvalidBlockHeader: BlockHeader

  }

  /** Indicates that our block headers do not properly reference one another */
  case class BlockHeadersDoNotConnect(lastValidBlockHeader: BlockHeader, firstInvalidBlockHeader: BlockHeader) extends BlockHeaderSyncError

  /** Indicates that our node saw a difficulty adjustment on the network when there should not have been one between the
    * two given [[BlockHeader]]s */
  case class BlockHeaderDifficultyFailure(lastValidBlockHeader: BlockHeader, firstInvalidBlockHeader: BlockHeader) extends BlockHeaderSyncError

  //INTERNAL MESSAGES FOR BlockHeaderSyncActor
  case class CheckHeaderResult(error: Option[BlockHeaderSyncError], headers: Seq[BlockHeader]) extends BlockHeaderSyncMessage

  /** Checks that the given block headers all connect to each other
    * If the headers do not connect, it returns the two block header hashes that do not connect
    * [[https://github.com/bitcoin/bitcoin/blob/0.13/src/main.cpp#L5865-L5879]]
    *
    * @param startingHeader header we are starting our header check from, this header is not checked
    *                       if this is not defined we just start from the first header in blockHeaders
    * @param blockHeaders the set of headers we are checking the validity of
    * @param maxHeight the height of the blockchain before checking the block headers
    * */
  def checkHeaders(startingHeader: BlockHeader, blockHeaders: Seq[BlockHeader],
                   maxHeight: Long, network: NetworkParameters = Constants.networkParameters): CheckHeaderResult = {
    @tailrec
    def loop(previousBlockHeader: BlockHeader, remainingBlockHeaders: Seq[BlockHeader]): CheckHeaderResult = {
      if (remainingBlockHeaders.isEmpty) CheckHeaderResult(None,blockHeaders)
      else {
        val header = remainingBlockHeaders.head
        if (header.previousBlockHash != previousBlockHeader.hash) {
          val error = BlockHeaderSyncActor.BlockHeadersDoNotConnect(previousBlockHeader, header)
          CheckHeaderResult(Some(error),blockHeaders)
        } else if (header.nBits == previousBlockHeader.nBits) {
          loop(header, remainingBlockHeaders.tail)
        } else {
          network match {
            case MainNet =>
              val blockHeaderHeight = (blockHeaders.size - remainingBlockHeaders.tail.size) + maxHeight
              logger.debug("Block header height: " + blockHeaderHeight)
              if ((blockHeaderHeight % MainNet.difficultyChangeThreshold) == 0) loop(remainingBlockHeaders.head, remainingBlockHeaders.tail)
              else {
                val error = BlockHeaderSyncActor.BlockHeaderDifficultyFailure(previousBlockHeader,remainingBlockHeaders.head)
                CheckHeaderResult(Some(error),blockHeaders)
              }
            case RegTest | TestNet3 =>
              //currently we are just ignoring checking difficulty on testnet and regtest as they vary wildly
              loop(remainingBlockHeaders.head, remainingBlockHeaders.tail)
          }
        }
      }
    }
    val result = loop(startingHeader,blockHeaders)
    result
  }

}
