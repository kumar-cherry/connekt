/*
 *         -╥⌐⌐⌐⌐            -⌐⌐⌐⌐-
 *      ≡╢░░░░⌐\░░░φ     ╓╝░░░░⌐░░░░╪╕
 *     ╣╬░░`    `░░░╢┘ φ▒╣╬╝╜     ░░╢╣Q
 *    ║╣╬░⌐        ` ╤▒▒▒Å`        ║╢╬╣
 *    ╚╣╬░⌐        ╔▒▒▒▒`«╕        ╢╢╣▒
 *     ╫╬░░╖    .░ ╙╨╨  ╣╣╬░φ    ╓φ░╢╢Å
 *      ╙╢░░░░⌐"░░░╜     ╙Å░░░░⌐░░░░╝`
 *        ``˚¬ ⌐              ˚˚⌐´
 *
 *      Copyright © 2016 Flipkart.com
 */
package com.flipkart.connekt.commons.helpers

import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.typesafe.config.Config
import kafka.producer.{KeyedMessage, Producer}
import org.apache.commons.pool.impl.GenericObjectPool

import scala.util.Try
import scala.util.control.NonFatal

trait KafkaProducer {
  def writeMessages(topic: String, message: (String,String)*)
}

class KafkaProducerHelper(producerFactoryConf: Config, globalContextConf: Config) extends KafkaConnectionHelper with GenericObjectPoolHelper {

  validatePoolProps("kafka producer pool", globalContextConf)

  val kafkaProducerPool: GenericObjectPool[Producer[String, String]] = {
    try {
      createKafkaProducerPool(producerFactoryConf,
        Try(globalContextConf.getInt("maxActive")).toOption,
        Try(globalContextConf.getInt("maxIdle")).toOption,
        Try(globalContextConf.getLong("minEvictableIdleTimeMillis")).toOption,
        Try(globalContextConf.getLong("timeBetweenEvictionRunsMillis")).toOption,
        Try(globalContextConf.getBoolean("enableLifo")).getOrElse(true)
      )
    } catch {
      case NonFatal(e) =>
        ConnektLogger(LogFile.FACTORY).error(s"Failed creating kafka producer pool. ${e.getMessage}", e)
        throw e
    }
  }

  def writeMessages(topic: String, message: (String,String)*) = {
    val producer = kafkaProducerPool.borrowObject()
    try {
      val keyedMessages = message.map(m => new KeyedMessage[String,  String](topic,m._1 ,m._2))
      producer.send(keyedMessages:_*)
    } finally {
      kafkaProducerPool.returnObject(producer)
    }
  }

  def shutdown() = try {
    kafkaProducerPool.close()
    ConnektLogger(LogFile.FACTORY).info("KafkaProducerHelper shutdown.")
  } catch {
    case e: Exception => ConnektLogger(LogFile.FACTORY).error(s"Error in KafkaProducerHelper companion shutdown. ${e.getMessage}", e)
  }
}

object KafkaProducerHelper extends KafkaProducer {

  private var instance: KafkaProducerHelper = null

  def init(consumerConfig: Config, globalContextConf: Config) = {
    if(null == instance) {
      this.synchronized {
        instance = new KafkaProducerHelper(consumerConfig, globalContextConf)
      }
    }
    instance
  }

  def writeMessages(topic: String, message: (String,String)*) = instance.writeMessages(topic, message :_ *)

//  for old compatibity
//  def writeMessages(topic: String, messages: String*) = writeMessages(topic, messages.map(Tuple2[String,String](null,_)) :_ *)

}
