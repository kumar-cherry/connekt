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
package com.flipkart.connekt.callback.topologies

import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.entities.{DeviceDetails, HTTPEventSink, Subscription}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.{SyncManager, SyncMessage, SyncType}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.FireflyBoot
import com.typesafe.config.ConfigFactory

class HttpTopologyTest extends TopologyUTSpec with Instrumented {

  "HttpTopology Test" should "run" in {

    FireflyBoot.start()

    val subscription = new Subscription()

    subscription.name = "HttpTopologyTest"
    subscription.id = "e7fe4e8b-ea16-402a-b0b6-7568eadc0bf6"
    subscription.shutdownThreshold = 3
    subscription.createdBy = "connekt-genesis"
    subscription.sink = new HTTPEventSink("POST", "http://requestb.in/1i0oin41")
    subscription.stencilId = "testEventFilter"

    SyncManager.get().publish(SyncMessage(topic = SyncType.SUBSCRIPTION, List("start", subscription.getJson)))

    val kafkaProducerConnConf = ConnektConfig.getConfig("connections.kafka.producerConnProps").get
    val kafkaProducerPoolConf = ConnektConfig.getConfig("connections.kafka.producerPool").getOrElse(ConfigFactory.empty())
    val kafkaProducerHelper = KafkaProducerHelper.init(kafkaProducerConnConf, kafkaProducerPoolConf)

    val msg = DeviceDetails("random", "random", "random", "random", "random", "random", "random", "random", "random", "random")
    println(msg.toCallbackEvent.getJson)

    val time1 = System.currentTimeMillis()
    for( a <- 1 to 1000) {
      kafkaProducerHelper.writeMessages("active_events", Tuple2( null,  msg.toCallbackEvent.getJson))
    }

    val time2 =  System.currentTimeMillis()

    println(" time taken = " + (time2 - time1))


    Thread.sleep(100000)

    SyncManager.get().publish(SyncMessage(topic = SyncType.SUBSCRIPTION, List("stop", subscription.getJson)))

    Thread.sleep(1000)

  }

}
