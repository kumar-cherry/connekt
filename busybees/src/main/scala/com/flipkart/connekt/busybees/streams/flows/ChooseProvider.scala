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
package com.flipkart.connekt.busybees.streams.flows

import com.flipkart.connekt.commons.iomodels.{EmailPayloadEnvelope, ProviderEnvelope}
import com.flipkart.connekt.commons.services.ConnektConfig

import scala.collection.JavaConverters._
import scala.util.Random

class ChooseProvider(channel: String) extends MapFlowStage[ProviderEnvelope, ProviderEnvelope] {

  lazy val availableProviders = ConnektConfig.getList[java.util.HashMap[String, String]](s"$channel.providers.share").map(_.asScala).map(p => {
    p("name").toString -> p("value").toString.toInt
  }).toMap

  override val map: (ProviderEnvelope) => List[ProviderEnvelope] = payload => {

    val selectedProvider = pickProvider(payload.provider.toList)

    val out = payload match {
      case email: EmailPayloadEnvelope =>
        email.copy(provider = email.provider :+ selectedProvider)
      case sms: _ =>
        payload
    }

    List(out)

  }

  def pickProvider(alreadyTriedProviders: List[String]): String = {

    val remainingProviders = availableProviders.filterKeys(!alreadyTriedProviders.contains(_))

    val maxValue = remainingProviders.foldLeft(0)(_ + _._2)
    val randomGenerator = new Random()
    val randomNumber = randomGenerator.nextInt(maxValue)
    var counter = 0

    remainingProviders.keySet.toList.sorted.map(key => {
      val compareValue = remainingProviders(key) + counter
      counter += remainingProviders(key)
      key -> compareValue
    }).find(_._2 > randomNumber).getOrElse(remainingProviders.head)._1

  }

}
