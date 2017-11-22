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

import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.WAContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.iomodels.ContactPayload
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import scala.util.Try

class WAContactService(queueProducerHelper: KafkaProducerHelper) extends TService with Instrumented {

  private lazy val dao = DaoFactory.getWAContactDao
  private final val WA_CONTACT_BATCH = ConnektConfig.getInt("wa.check.contact.batch.size").getOrElse(1000)
  private final val WA_CONTACT_QUEUE = ConnektConfig.getString("wa.check.contact.queue.name").get

  @Timed("add")
  def add(contactEntity: WAContactEntity): Try[Unit] = profile("add") {
    dao.add(contactEntity)
  }

  @Timed("get")
  def get(destination: String): Try[Option[WAContactEntity]] = profile("get") {
    dao.get(destination)
  }

  @Timed("gets")
  def gets(destinations: Set[String]): Try[List[WAContactEntity]] = profile("gets") {
    dao.gets(destinations)
  }

  def refreshWAContacts =  {
    val task = new Runnable {
      override def run() = {
        profile("refreshAll") {
          try {
            dao.getAllContacts
              .grouped(WA_CONTACT_BATCH)
              .foreach(_.foreach(contact => enqueueContactEvents(contact)))
          } catch {
            case e: Exception =>
              ConnektLogger(LogFile.SERVICE).error(s"Contact warm-up failure", e)
          }
        }
      }
    }
    new Thread(task).start()
  }

  @Timed("enqueueContactPayload")
  def enqueueContactEvents(contact: ContactPayload): Unit = {
    val reqId = generateUUID
    queueProducerHelper.writeMessages(WA_CONTACT_QUEUE, (reqId, contact.getJson))
  }

}

object WAContactService {
  var instance: WAContactService = _

  def apply(queueProducerHelper: KafkaProducerHelper): WAContactService = {
    if (null == instance)
      this.synchronized {
        instance = new WAContactService(queueProducerHelper)
      }
    instance
  }
}
