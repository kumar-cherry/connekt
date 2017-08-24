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

import java.util.Properties

import com.flipkart.connekt.commons.factories.{KafkaConsumerFactory, KafkaProducerFactory}
import com.flipkart.connekt.commons.utils.StringUtils
import com.typesafe.config.Config
import kafka.consumer.{Consumer, ConsumerConfig}
import kafka.producer.{Producer, ProducerConfig}
import kafka.utils.{ZKGroupTopicDirs, ZKStringSerializer, ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.apache.commons.pool.impl.GenericObjectPool

import scala.util.Try
import StringUtils._

import scala.collection.mutable

trait KafkaConnectionHelper extends KafkaZKHelper {

  def createKafkaConsumerPool(factoryConf: Config,
                              maxActive: Option[Int],
                              maxIdle: Option[Int],
                              minEvictionIdleMillis: Option[Long],
                              timeBetweenEvictionRunMillis: Option[Long],
                              enableLifo: Boolean = false) = {

    val factoryProps = new Properties()
    factoryProps.setProperty("zookeeper.connect", factoryConf.getString("zookeeper.connect"))
    factoryProps.setProperty("group.id", factoryConf.getString("group.id"))
    factoryProps.setProperty("zookeeper.session.timeout.ms", factoryConf.getString("zookeeper.session.timeout.ms"))
    factoryProps.setProperty("zookeeper.sync.time.ms", factoryConf.getString("zookeeper.sync.time.ms"))
    factoryProps.setProperty("auto.commit.interval.ms", factoryConf.getString("auto.commit.interval.ms"))
    factoryProps.setProperty("consumer.timeout.ms", factoryConf.getString("consumer.timeout.ms"))

    val kafkaConsumerFactory = new KafkaConsumerFactory(factoryProps)

    val kafkaConsumerPool = new GenericObjectPool(kafkaConsumerFactory)
    kafkaConsumerPool.setMaxActive(maxActive.getOrElse(GenericObjectPool.DEFAULT_MAX_ACTIVE))
    kafkaConsumerPool.setMaxIdle(maxIdle.getOrElse(GenericObjectPool.DEFAULT_MAX_IDLE))
    kafkaConsumerPool.setMinEvictableIdleTimeMillis(minEvictionIdleMillis.getOrElse(GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS))
    kafkaConsumerPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunMillis.getOrElse(GenericObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS))
    kafkaConsumerPool.setWhenExhaustedAction(GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION)
    kafkaConsumerPool.setLifo(enableLifo)

    kafkaConsumerPool
  }

  def createKafkaProducerPool(factoryConf: Config,
                              maxActive: Option[Int],
                              maxIdle: Option[Int],
                              minEvictionIdleMillis: Option[Long],
                              timeBetweenEvictionRunMillis: Option[Long],
                              enableLifo: Boolean = false) = {

    val factoryProps = new Properties()
    factoryProps.setProperty("metadata.broker.list", factoryConf.getString("metadata.broker.list"))
    factoryProps.setProperty("serializer.class", factoryConf.getString("serializer.class"))
    factoryProps.setProperty("request.required.acks", factoryConf.getString("request.required.acks"))
    factoryProps.setProperty("producer.type", Try(factoryConf.getString("producer.type")).getOrElse("sync"))
    factoryProps.setProperty("compression.codec", "1")

    val kafkaProducerFactory = new KafkaProducerFactory[String, String](factoryProps)

    val kafkaProducerPool = new GenericObjectPool(kafkaProducerFactory)
    kafkaProducerPool.setMaxActive(maxActive.getOrElse(GenericObjectPool.DEFAULT_MAX_ACTIVE))
    kafkaProducerPool.setMaxIdle(maxIdle.getOrElse(GenericObjectPool.DEFAULT_MAX_IDLE))
    kafkaProducerPool.setMinEvictableIdleTimeMillis(minEvictionIdleMillis.getOrElse(GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS))
    kafkaProducerPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunMillis.getOrElse(GenericObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS))
    kafkaProducerPool.setWhenExhaustedAction(GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION)
    kafkaProducerPool.setLifo(enableLifo)

    kafkaProducerPool
  }
  
  def createKafkaConsumer(groupId: String, kafkaConsumerConf: Config) = {
    
    require(groupId.isDefined, "`groupId` must be defined")
    
    val consumerProps = new Properties()
    consumerProps.setProperty("zookeeper.connect", kafkaConsumerConf.getString("zookeeper.connect"))
    consumerProps.setProperty("group.id", groupId)
    consumerProps.setProperty("zookeeper.session.timeout.ms", kafkaConsumerConf.getString("zookeeper.session.timeout.ms"))
    consumerProps.setProperty("zookeeper.sync.time.ms", kafkaConsumerConf.getString("zookeeper.sync.time.ms"))
    consumerProps.setProperty("auto.commit.interval.ms", kafkaConsumerConf.getString("auto.commit.interval.ms"))
    consumerProps.setProperty("consumer.timeout.ms", kafkaConsumerConf.getString("consumer.timeout.ms"))
    consumerProps.setProperty("fetch.message.max.bytes",  kafkaConsumerConf.getString("fetch.message.max.byte"))
    consumerProps.setProperty("num.consumer.fetchers", "4")
    consumerProps.setProperty("rebalance.max.retries", kafkaConsumerConf.getString("rebalance.max.retries"))
    consumerProps.setProperty("rebalance.backoff.ms", kafkaConsumerConf.getString("rebalance.backoff.ms"))
    consumerProps.setProperty("queued.max.message.chunks", "200")
    consumerProps.setProperty("socket.receive.buffer.bytes",kafkaConsumerConf.getString("socket.receive.buffer.bytes"))

    Consumer.create(new ConsumerConfig(consumerProps))
  }
  
  def createKafkaProducer[K, M](kafkaProducerConf: Config) = {

    val producerProps = new Properties()
    producerProps.setProperty("metadata.broker.list", kafkaProducerConf.getString("metadata.broker.list"))
    producerProps.setProperty("serializer.class", kafkaProducerConf.getString("serializer.class"))
    producerProps.setProperty("request.required.acks", kafkaProducerConf.getString("request.required.acks"))
    producerProps.setProperty("producer.type", Try(kafkaProducerConf.getString("producer.type")).getOrElse("sync"))
    producerProps.setProperty("compression.codec", "1")

    new Producer[K, M](new ProducerConfig(producerProps))
  }

}
