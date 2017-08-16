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
package com.flipkart.connekt.busybees.streams.flows.dispatchers

import java.util.Date

import com.flipkart.connekt.busybees.models.APNSRequestTracker
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels.{APSPayloadEnvelope, Priority, iOSPNPayload}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.turo.pushy.apns.DeliveryPriority
import com.turo.pushy.apns.util.SimpleApnsPushNotification


class APNSDispatcherPrepare extends MapFlowStage[APSPayloadEnvelope, (SimpleApnsPushNotification, APNSRequestTracker)] {

  override val map: APSPayloadEnvelope => List[(SimpleApnsPushNotification, APNSRequestTracker)] = envelope => {
    try {
      val payload = envelope.apsPayload.asInstanceOf[iOSPNPayload]

      ConnektLogger(LogFile.PROCESSORS).info("APNSDispatcherPrepare received message: {}", supplier(envelope.messageId))
      ConnektLogger(LogFile.PROCESSORS).trace("APNSDispatcherPrepare received message: {}", supplier(envelope))

      val priority = payload.priority.getOrElse(Priority.HIGH) match {
        case Priority.NORMAL => DeliveryPriority.CONSERVE_POWER
        case Priority.HIGH => DeliveryPriority.IMMEDIATE
      }

      val pushNotification = new SimpleApnsPushNotification(payload.token, payload.topic, payload.data, new Date(payload.expiryInMillis), priority)

      List((pushNotification, APNSRequestTracker(envelope.messageId, envelope.clientId, envelope.deviceId, envelope.appName, envelope.contextId, envelope.meta)))

    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"APNSDispatcherPrepare:: onPush :: Error", e)
        throw new ConnektPNStageException(envelope.messageId, envelope.clientId, Set(envelope.deviceId), InternalStatus.StageError, envelope.appName, MobilePlatform.IOS, envelope.contextId, envelope.meta, s"APNSDispatcherPrepare-${e.getMessage}", e)
    }
  }
}
