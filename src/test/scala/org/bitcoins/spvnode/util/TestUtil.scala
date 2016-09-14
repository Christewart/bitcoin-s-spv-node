package org.bitcoins.spvnode.util

import org.bitcoins.core.protocol.blockchain.{BlockHeader, TestNetChainParams}
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.spvnode.NetworkMessage
import org.bitcoins.spvnode.messages.control.VersionMessage
import org.bitcoins.spvnode.messages.data.GetHeadersMessage

/**
  * Created by chris on 6/2/16.
  */
trait TestUtil {

  //txid on testnet 44e504f5b7649d215be05ad9f09026dee95201244a3b218013c504a6a49a26ff
  //this tx has multiple inputs and outputs
  def rawTransaction = "01000000" +
    "02df80e3e6eba7dcd4650281d3c13f140dafbb823a7227a78eb6ee9f6cedd040011b0000006a473044022040f91c48f4011bf2e2edb6621bfa8fb802241de939cb86f1872c99c580ef0fe402204fc27388bc525e1b655b5f5b35f9d601d28602432dd5672f29e0a47f5b8bbb26012102c114f376c98d12a0540c3a81ab99bb1c5234245c05e8239d09f48229f9ebf011ffffffff" +
    "df80e3e6eba7dcd4650281d3c13f140dafbb823a7227a78eb6ee9f6cedd04001340000006b483045022100cf317c320d078c5b884c44e7488825dab5bcdf3f88c66314ac925770cd8773a7022033fde60d33cc2842ea73fce5d9cf4f8da6fadf414a75b7085efdcd300407f438012102605c23537b27b80157c770cd23e066cd11db3800d3066a38b9b592fc08ae9c70ffffffff" +
    "02c02b00000000000017a914b0b06365c482eb4eabe6e0630029fb8328ea098487e81c0000000000001976a914938da2b50fd6d8acdfa20e30df0e7d8092f0bc7588ac00000000"
  def transaction = Transaction(rawTransaction)

  /**
    * Random version message bitcoins created when connecting to a testnet seed
    * This does not include the header
    * @return
    */
  def rawVersionMessage = "7c1101000000000000000000d805833655010000000000000000000000000000000000000000ffff0a940106479d010000000000000000000000000000000000ffff739259bb479d0000000000000000182f626974636f696e732d7370762d6e6f64652f302e302e310000000000"
  def versionMessage = VersionMessage(rawVersionMessage)

  /**
    * This is a raw network message indicating the version a node is using on the p2p network
    * This has BOTH the header and the payload
    * @return
    */
  def rawNetworkMessage = "0b11090776657273696f6e0000000000660000002f6743da721101000100000000000000e0165b5700000000010000000000000000000000000000000000ffffad1f27a8479d010000000000000000000000000000000000ffff00000000479d68dc32a9948d149b102f5361746f7368693a302e31312e322f7f440d0001"
  def networkMessage = NetworkMessage(rawNetworkMessage)

  /**
    * This is a get headers message taken from wireshark off of a node that sent the message
    * @return
    */
  def rawGetHeadersMsg = "721101001f693a1ceb6eabcd03f7f7e22a763efec0f4fd1618a50558283b23600000000000e933b17e7f39aa00f24cabbae1e618ecbfbd70a3ecc4f63072dc790000000000e53160d1edccfd7feed401233c274fbc229f7f0d2b6152735344ec0000000000110274616c7161a8aca8390e1a472f22de7470368e5066f20d050000000000008f98d517947a765069f976de05f910a65743fbac59a430aaf30d350000000000572b328dc2155a853157bebcc616fc82fd6996cb348d6a3cd929c500000000007d2c3eca25e06b684132325c85673675b928c3d0ee2eec553c01000000000000a5e239f5b4c6998078e71a065bd71216583e88bf80a93d170253460000000000b6c926f4cb309d2d87f40d4790905f3f29cd05f3ea26854e060700000000000032668e2de62f181cdeabad351318008288993b3db3e411216aad70000000000079f9e80a6ffe62ab13daa65e6410c8d36d513e198fc161b90cd1d40000000000bbf6a01b2faeb102d177ab03131263166790548181ff3cb04308000000000000b90ef011e62cd6c259939ac4ad372c5f395718a93ade933fd5503b0000000000f346a56f2fa278919c40cdde7ea424058543ce2237f6c9df174e2300000000002fd1795f0dbbc70f7a41d369d4b89c56bf1c6cf2c43ef8f8ed00000000000000643a31c93787ab66b51a4ccfc2ce867d855f4ad64b2a3136e1a12d00000000001323867c8b11027eac79e0cc71fce91f24b1066c6423e69ec409000000000000d1eb1916bab3839da423f1e5aa1c271204bd5564bac6fefd498e0f0000000000a4dcf02c42a71b5b10433917dda89a0d34984a065c0b05a52d03000000000000c57477df9128ef4f71366c4a89e432445d94b0c2b02e7a9ccb060000000000004c45281d6afa17835d264cc8ba181b8c51501247c128d644e2000000000000005c6d201f400a544250bae463ff28f47d53f32d97ae27b5b73b5f580000000000b1def34939f027654943457d69e104304c9798c0af837a7e1f1500000000000069164c8213a0d6b38fe1d9a2c63bcfb5808b65f6e50376726a120000000000002893d0fdafe84e3670a31b22ba80edfb841746462417bad024ac5e0000000000063015920d27befb9ff25f9a1989cda07e4ce62fa9ac8ec0f5b401000000000040a936762fbde4b51bea3ad59dfe202f16dd220761235172960c000000000000332d487a5cc80c00296c43c5bec6b6b1a41a499ce2efd6b6d8514b00000000009400a26083d0551175374c45746488d1c9eaea8d891e69f2e57c5712000000005d62facd94114f5ee55ab6e6797a5c6a8d0e0626b9200ffdf647f15c0000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea3309000000000000000000000000000000000000000000000000000000000000000000000000"
  def getHeadersMsg = GetHeadersMessage(rawGetHeadersMsg)

  def firstFiveTestNetBlockHeaders = Seq(
    BlockHeader(""),
    BlockHeader(""),
    BlockHeader(""),
    BlockHeader(""),
    BlockHeader("")

  )
}

object TestUtil extends TestUtil