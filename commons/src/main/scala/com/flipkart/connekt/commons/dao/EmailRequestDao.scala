package com.flipkart.connekt.commons.dao

import com.flipkart.connekt.commons.behaviors.HTableFactory
import com.flipkart.connekt.commons.iomodels.{ChannelRequestInfo, ChannelRequestData}

/**
 * @author aman.shrivastava on 14/12/15.
 */
class EmailRequestDao(tableName: String, hTableFactory: HTableFactory) extends RequestDao(tableName: String, hTableFactory: HTableFactory) {
  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = ???

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = ???

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = ???

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): ChannelRequestData = ???
}