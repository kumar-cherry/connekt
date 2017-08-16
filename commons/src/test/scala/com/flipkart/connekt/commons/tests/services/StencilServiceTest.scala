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
package com.flipkart.connekt.commons.tests.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.entities.{Bucket, Stencil, StencilEngine}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.tests.CommonsBaseTest
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils._
import org.skyscreamer.jsonassert.{JSONAssert, JSONCompareMode}
import org.skyscreamer.jsonassert.comparator.DefaultComparator

class StencilServiceTest extends CommonsBaseTest {

  val stencil = new Stencil
  stencil.id = StringUtils.generateRandomStr(10)
  stencil.name = StringUtils.generateRandomStr(10)
  stencil.component = "data"
  stencil.engine = StencilEngine.VELOCITY
  stencil.engineFabric =
    """
      |{
      |	"data": "Order for $!{product}, $booleanValue, $integerValue",
      | "mapV" : "$map.get("k1")",
      | "mapI" : "$!map.get("k2")",
      | "list" : "$list[0]",
      | "invalid" : "$!invalid"
      |
      | #if ( $!invalidInt > 0 )
      |   , "int2" : $integerValue
      | #end
      |}
    """.stripMargin

  stencil.createdBy = "connekt-genesis"
  stencil.updatedBy = "connekt-genesis"
  stencil.version = 1
  stencil.bucket = "GLOBAL"

  val product = StringUtils.generateRandomStr(10)
  lazy val stencilService = ServiceFactory.getStencilService

  val payload =
    """
      |{
      |	"booleanValue": true,
      |	"integerValue": 1678,
      | "map": {
      |    "k1" : "v1"
      | },
      | "list" : [
      |   "hello"
      | ]
      |}
    """.stripMargin

  val dataResult =
    """
      |{
      |	"data": "Order for , true, 1678",
      | "mapV" : "v1",
      | "mapI" : "",
      | "list" : "hello",
      | "invalid" : ""
      |}
    """.stripMargin

  val bucket = new Bucket
  bucket.id = StringUtils.generateRandomStr(10)
  bucket.name = StringUtils.generateRandomStr(10)

  "Stencil Service" should "add stencil" in {

    noException should be thrownBy stencilService.add(stencil.id, List(stencil))
  }

  "Stencil Service" should "get the stencil" in {
    stencilService.get(stencil.id).head.toString shouldEqual stencil.toString
  }

  "Stencil Service" should "render the stencil for given ConnektRequest" in {
    noException should be thrownBy stencilService.materialize(stencil, payload.getObj[ObjectNode])
    val renderResult:String = stencilService.materialize(stencil, payload.getObj[ObjectNode]).toString
    println(renderResult)
    JSONAssert.assertEquals( "reder-result" ,dataResult, renderResult, new DefaultComparator(JSONCompareMode.LENIENT))
  }

  "Stencil Service" should "add bucket" in {
    noException should be thrownBy stencilService.addBucket(bucket)
  }

  "Stencil Service" should "get bucket" in {
    stencilService.getBucket(bucket.name).get.toString shouldEqual bucket.toString
  }

  "Stencil Service" should "get all ensemble" in {
    stencilService.getAllEnsemble().size should not be 0
  }
}
