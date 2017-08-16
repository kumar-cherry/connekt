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
package com.flipkart.connekt.busybees.streams.flows.formaters

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.NIOFlow
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.DeviceDetailsService
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class IOSChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, APSPayloadEnvelope](parallelism)(ec) {

  lazy val stencilService = ServiceFactory.getStencilService

  override def map: (ConnektRequest) => List[APSPayloadEnvelope] = message => {
    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"IOSChannelFormatter received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"IOSChannelFormatter received message: ${message.getJson}")
      val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]

      val devicesInfo = DeviceDetailsService.get(pnInfo.appName, pnInfo.deviceIds).get
      val invalidDeviceIds = pnInfo.deviceIds.diff(devicesInfo.map(_.deviceId).toSet)
      invalidDeviceIds.map(PNCallbackEvent(message.id, message.clientId, _, InternalStatus.MissingDeviceInfo, MobilePlatform.IOS, pnInfo.appName, message.contextId.orEmpty)).persist
      ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Option(message.platform), message.appName, InternalStatus.MissingDeviceInfo, invalidDeviceIds.size)

      val listOfTokenDeviceId = devicesInfo.map(r => (r.token, r.deviceId))
      val iosStencil = stencilService.getStencilsByName(s"ckt-${pnInfo.appName.toLowerCase}-ios")

      val ttlInMillis = message.expiryTs.getOrElse(System.currentTimeMillis() + 6.hours.toMillis)
      val apnsEnvelopes = listOfTokenDeviceId.map(td => {
        val data = message.channelData.asInstanceOf[PNRequestData].data
        val requestData = stencilService.materialize(iosStencil.find(s => s.component.equals("data")).orNull, data).asInstanceOf[String].getObj[ObjectNode]
          .put("messageId", message.id)
          .put("contextId", message.contextId.orEmpty)
        val apnsTopic = pnInfo.topic.getOrElse(stencilService.materialize(iosStencil.find(s => s.component.equals("topic")).orNull, data).asInstanceOf[String])
        val apnsPayload = iOSPNPayload(td._1, apnsTopic, ttlInMillis, requestData.toString, Option(pnInfo.priority))
        APSPayloadEnvelope(message.id, td._2, pnInfo.appName, message.contextId.orEmpty, message.clientId, apnsPayload, message.meta)
      })

      if (apnsEnvelopes.nonEmpty && ttlInMillis > System.currentTimeMillis()) {
        if (!message.isTestRequest) {
          ConnektLogger(LogFile.PROCESSORS).info(s"IOSChannelFormatter for message: ${message.id} done")
          apnsEnvelopes
        }
        else {
          ConnektLogger(LogFile.PROCESSORS).info(s"IOSChannelFormatter dropping dry-run message: ${message.id}")
          List.empty[APSPayloadEnvelope]
        }
      } else if (apnsEnvelopes.nonEmpty) {
        ConnektLogger(LogFile.PROCESSORS).warn(s"IOSChannelFormatter dropping ttl-expired message: ${message.id}")
        ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Option(message.platform), message.appName, InternalStatus.TTLExpired, devicesInfo.size)
        apnsEnvelopes.map(e => PNCallbackEvent(e.messageId, message.clientId, e.deviceId, InternalStatus.TTLExpired, MobilePlatform.IOS, e.appName, message.contextId.orEmpty)).persist
        List.empty[APSPayloadEnvelope]
      } else
        List.empty[APSPayloadEnvelope]

    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"IOSChannelFormatter error for ${message.id}", e)
        throw new ConnektPNStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, message.platform, message.contextId.orEmpty, message.meta, "IOSChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
