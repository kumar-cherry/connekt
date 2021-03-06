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

import com.flipkart.connekt.busybees.models.GCMRequestTracker
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{LogFile, ConnektLogger}
import com.flipkart.connekt.commons.iomodels.{GCMXmppPNPayload, FCMXmppRequest, GCMPayloadEnvelope}
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils._

class GCMXmppDispatcherPrepare extends MapFlowStage[GCMPayloadEnvelope, (FCMXmppRequest, GCMRequestTracker)] {

  override implicit val map: GCMPayloadEnvelope => List[(FCMXmppRequest, GCMRequestTracker)] = message => {
    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"GCMXmppDispatcherPrepare received message: ${message.messageId}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"GCMXmppDispatcherPrepare received message: ${message.toString}")

      KeyChainManager.getGoogleCredential(message.appName).map { credential =>
        val xmppRequest:FCMXmppRequest = FCMXmppRequest(message.gcmPayload.asInstanceOf[GCMXmppPNPayload], credential)
        val requestTracker = GCMRequestTracker(message.messageId, message.clientId, message.deviceId, message.appName, message.contextId, message.meta)
        List(xmppRequest -> requestTracker)
      }.getOrElse(List.empty[(FCMXmppRequest, GCMRequestTracker)])
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"GCMXmppDispatcherPrepare failed with ${e.getMessage}", e)
        throw new ConnektPNStageException(message.messageId, message.clientId, message.deviceId.toSet, InternalStatus.StageError, message.appName, MobilePlatform.ANDROID, message.contextId, message.meta, s"GCMXmppDispatcherPrepare-${e.getMessage}", e)
    }
  }
}
