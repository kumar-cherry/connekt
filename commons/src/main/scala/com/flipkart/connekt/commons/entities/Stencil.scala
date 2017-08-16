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
package com.flipkart.connekt.commons.entities

import java.util.Date
import javax.persistence.Column

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonSerializer, SerializerProvider}
import com.flipkart.connekt.commons.entities.StencilEngine.StencilEngine
import com.flipkart.connekt.commons.utils.StringUtils._

class Stencil() {
  @Column(name = "id")
  var id: String = _

  @EnumTypeHint(value = "com.flipkart.connekt.commons.entities.StencilEngine")
  @Column(name = "engine")
  @JsonSerialize(using = classOf[StencilEngineToStringSerializer])
  @JsonDeserialize(using = classOf[StencilEngineToStringDeserializer])
  var engine: StencilEngine.StencilEngine = StencilEngine.GROOVY

  @Column(name = "engineFabric")
  var engineFabric: String = _

  @Column(name = "component")
  var component: String = _

  @Column(name = "name")
  var name: String = _

  @Column(name = "type")
  var `type`: String = _

  @Column(name = "createdBy")
  var createdBy: String = _

  @Column(name = "updatedBy")
  var updatedBy: String = _

  @Column(name = "version")
  var version: Int = 1

  @Column(name = "creationTS")
  var creationTS: Date = _

  @Column(name = "lastUpdatedTS")
  var lastUpdatedTS: Date = _

  @Column(name = "bucket")
  var bucket: String = _

  override def toString = s"Stencil($id, $name, $component, $engine, $engineFabric)"

  def validate() = {
    require(bucket.isDefined, "`bucket` must be defined.")
    require(name.isDefined, "`name` must be defined.")
    require(`type`.isDefined, "`type` must be defined.")
  }

  private [connekt] def this(id:String, engine: StencilEngine.StencilEngine, fabric:String) {
    this()
    this.id = id
    this.engine = engine
    this.engineFabric = fabric
  }

  def toInfo:StencilInfo = StencilInfo(this.id, this.`type`,this.name, this.createdBy, this.lastUpdatedTS.getTime)
}

case class StencilInfo(id:String, @JsonProperty("type") `type`:String, name:String, createdBy:String, lastUpdatedTS:Long)

object StencilEngine extends Enumeration {
  type StencilEngine = Value
  val VELOCITY, GROOVY = Value
}

class StencilEngineToStringSerializer extends JsonSerializer[StencilEngine] {
  override def serialize(t: StencilEngine.Value, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) = {
    jsonGenerator.writeObject(t.toString)
  }
}

class StencilEngineToStringDeserializer extends JsonDeserializer[StencilEngine] {
  @Override
  override def deserialize(parser: JsonParser, context: DeserializationContext): StencilEngine.Value = {
    try {
      StencilEngine.withName(parser.getValueAsString.toUpperCase)
    } catch {
      case e: NoSuchElementException =>
        null
    }
  }
}
