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
package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.dao.TRequestDao
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PullCallbackEvent, PullRequestData, PullRequestInfo}
import com.flipkart.connekt.commons.utils.StringUtils.generateUUID
import com.roundeights.hasher.Implicits._
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.fasterxml.jackson.databind.node.ObjectNode

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.commons.lang.RandomStringUtils

class PullMessageService(requestDao: TRequestDao) extends TService {
  private val messageDao: TRequestDao = requestDao

  def saveRequest(request: ConnektRequest)(implicit ec: ExecutionContext): Try[String] = {
    try {
      val reqWithId = request.copy(id = generateUUID)
      val pullInfo = request.channelInfo.asInstanceOf[PullRequestInfo]
      val pullData = request.channelData.asInstanceOf[PullRequestData]
      // read and createTS will be removed after migration Completes
      val read = if(pullData.data.get("read") != null && pullData.data.get("read").asBoolean()) 1L else 0L
      val createTS = if(pullData.data.get("generatedOn") != null) pullData.data.get("generatedOn").asLong else System.currentTimeMillis()
      if (!request.isTestRequest)
      {
        messageDao.saveRequest(reqWithId.id, reqWithId, true)
        pullInfo.userIds.map(
          ServiceFactory.getPullMessageQueueService.enqueueMessage(reqWithId.appName, _, reqWithId.id, reqWithId.expiryTs, Some(read), Some(createTS))
        )
      }
      Success(reqWithId.id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Failed to save in app request ${e.getMessage}", e)
        Failure(e)
    }
  }

  def getRequest(appName: String, contactIdentifier: String, timeStampRange: Option[(Long, Long)], filter: Map[String, Any])(implicit ec: ExecutionContext): Future[(Seq[ObjectNode], Int)] = {
    val pendingMessages = ServiceFactory.getPullMessageQueueService.getMessages(appName, contactIdentifier, timeStampRange)
    pendingMessages.map(queueMessages => {
      val messageMap = queueMessages.toMap
      val distinctMessageIds = queueMessages.map(_._1).distinct
      val fetchedMessages: Try[List[ConnektRequest]] = getRequestbyIds(distinctMessageIds.toList)

      val sortedMessages: Try[Seq[ConnektRequest]] = fetchedMessages.map { _messages =>
        val mIdRequestMap = _messages.map(r => r.id -> r).toMap
        distinctMessageIds.flatMap(mId => mIdRequestMap.find(_._1 == mId).map(_._2))
      }
      val validMessages = sortedMessages.map(_.filter(_.expiryTs.forall(_ >= System.currentTimeMillis)).filterNot(_.isTestRequest)).getOrElse(List.empty[ConnektRequest])

      val stencilService = ServiceFactory.getStencilService
      val filteredMessages = stencilService.getStencilsByName(s"pull-${appName.toLowerCase}-fetch-filter").headOption match {
        case Some(stencil) =>
          validMessages.filter(c => stencilService.materialize(stencil, Map("data" -> c.channelData.asInstanceOf[PullRequestData], "filter" -> filter).getJsonNode).asInstanceOf[Boolean])
        case None => validMessages
      }
      val unreadCount = filteredMessages.count(m => messageMap(m.id).read.get == 0L)
      val pullRequesData = filteredMessages.map { prd =>
        val data = prd.channelData.asInstanceOf[PullRequestData].data
        data.put("messageId", prd.id)
        data.put("read", messageMap(prd.id).read.get == 1L)
        data.put("createTs", messageMap(prd.id).createTs)
        data.put("expiryTs", messageMap(prd.id).expiryTs)
        data
      }
      (pullRequesData, unreadCount)
    })
  }

  def getRequestbyIds(ids: List[String]): Try[List[ConnektRequest]] = {
    try {
      Success(requestDao.fetchRequest(ids))
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Get request info failed ${e.getMessage}", e)
        Failure(e)
    }
  }

  def markAsRead(appName: String, contactIdentifier: String, filter: Map[String, Any])(implicit ec: ExecutionContext) = {
    getRequest(appName, contactIdentifier, None, filter).map(_messages => {
      val unReadMsgIds = _messages match {
        case (messages, _) => messages.filter(!_.get("read").asInstanceOf[Boolean])
                                       .map(_.get("messageId").toString)
      }
      ServiceFactory.getPullMessageQueueService.markQueueMessagesAsRead(appName, contactIdentifier, unReadMsgIds)
      val events = unReadMsgIds.map(msgId => {
        PullCallbackEvent(
          messageId = msgId,
          eventId = RandomStringUtils.randomAlphabetic(10),
          clientId = filter.get("client").getOrElse("").toString,
          contextId = "",
          appName = appName,
          eventType = "markAsRead")
      })
      events.persist
    })


  }
}
