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
package com.flipkart.connekt.commons.utils

import java.io.InputStream
import java.lang.reflect.{ParameterizedType, Type => JType}
import java.math.BigInteger
import java.security.SecureRandom
import java.util.UUID

import akka.http.scaladsl.model.HttpEntity
import akka.stream.Materializer
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.flipkart.connekt.commons.utils.NullWrapper._
import org.apache.commons.codec.CharEncoding
import org.apache.commons.validator.routines.UrlValidator

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.runtime.universe._
import scala.reflect.{ClassTag, _}


object StringUtils {

  val currentMirror = runtimeMirror(getClass.getClassLoader)

  private val urlValidator = new UrlValidator(Array("http", "https"))

  implicit def enum2String(enumValue: Enumeration#Value): String = enumValue.toString

  implicit def generateUUID: String = UUID.randomUUID().toString

  implicit class StringHandyFunctions(val s: String) {
    def getUtf8Bytes = s.getBytes(CharEncoding.UTF_8)

    def getUtf8BytesNullWrapped = Option(s).map(_.getUtf8Bytes).orNull.wrap

    def hasOnlyAllowedChars = s.forall(allowedCharsSet.contains)

    def isDefined = null != s && s.nonEmpty

    def isValidUrl = urlValidator.isValid(s)

    def stripNewLines = s.replaceAll("\n", "").replaceAll("\r", "")

  }

  def supplier[T](obj: => T): org.apache.logging.log4j.util.Supplier[T] = new org.apache.logging.log4j.util.Supplier[T] {
    override def get(): T = obj
  }

  implicit class InputStreamHandyFunctions(val is: InputStream) {
    def getString = scala.io.Source.fromInputStream(is).mkString
  }

  implicit class StringOptionHandyFunctions(val obj: Option[String]) {
    def orEmpty = obj.getOrElse("")
  }

  implicit class OptionHandyFunctions(val obj: Option[Any]) {
    def getString = obj.map(_.toString).get
  }


  implicit class ObjectHandyFunction(val obj: AnyRef) {
    def asMap: Map[String, Any] = {
      val fieldsAsPairs = for (field <- obj.getClass.getDeclaredFields) yield {
        field.setAccessible(true)
        (field.getName, field.get(obj))
      }
      Map(fieldsAsPairs: _*)
    }

  }

  implicit class MapHandyFunction(val map:Map[String,Any]){
    def getObj[T: ClassTag]: T = {
      val clz = classTag[T].runtimeClass
      val constructor = clz.getConstructors.head
      val fields = clz.getDeclaredFields.map(_.getName)
      val arguments = map.filterKeys(fields.contains)
      val obj = constructor.newInstance(arguments.values.map(_.asInstanceOf[Object]).toSeq :_ * )
      obj.asInstanceOf[T]
    }

    def getObjMap[T: ClassTag] : Map[String,Any]  = {
      val clz = classTag[T].runtimeClass
      val fields = clz.getDeclaredFields.map(_.getName)
      map.filterKeys(fields.contains)
    }
  }

  val objMapper = new ObjectMapper() with ScalaObjectMapper
  objMapper.registerModules(Seq(DefaultScalaModule): _*)
  objMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  // enable toString method of enums to return the value to be mapped
  objMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
  objMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)

  implicit class JSONMarshallFunctions(val o: AnyRef) {
    def getJson = objMapper.writeValueAsString(o)

    def getJsonNode = objMapper.convertValue(o, classOf[ObjectNode])
  }

  implicit class ByteArrayHandyFunctions(val b: Array[Byte]) {
    def getString = new String(b, CharEncoding.UTF_8)

    def getObj[T: ClassTag] = objMapper.readValue(b, classTag[T].runtimeClass).asInstanceOf[T]

    def getStringNullable = b.unwrap match {
      case array if array.isEmpty => null
      case value => new String(value, CharEncoding.UTF_8)
    }
  }

  implicit class JSONUnMarshallFunctions(val s: String) {

    def getObj[T: ClassTag] = objMapper.readValue(s, classTag[T].runtimeClass).asInstanceOf[T]

    def getObj(implicit cType: Class[_]) = objMapper.readValue(s, cType)

    def getObj[T](tTag: TypeTag[T]): T = objMapper.readValue(s, typeReference[T](tTag))

    private def typeReference[T](tag: TypeTag[T]): TypeReference[_] = new TypeReference[T] {
      override val getType = jTypeFromType(tag.tpe)
    }

    private def jTypeFromType(tpe: Type): JType = {
      val typeArgs = tpe match {
        case TypeRef(_, _, args) => args
      }
      val runtimeClass = currentMirror.runtimeClass(tpe)
      if (typeArgs.isEmpty) {
        runtimeClass
      }
      else new ParameterizedType {
        def getRawType = runtimeClass

        def getActualTypeArguments = typeArgs.map(jTypeFromType).toArray

        def getOwnerType = runtimeClass.getEnclosingClass
      }
    }
  }

  implicit class HttpEntity2String(val entity: HttpEntity) {
    def getString(implicit mat: Materializer): String = {
      import akka.http.scaladsl.unmarshalling._
      implicit val ec = mat.executionContext
      val futureString = Unmarshal(entity).to[String]
      Await.result(futureString, 60.seconds)
    }
  }

  def isNullOrEmpty(o: Any): Boolean = o match {
    case m: Map[_, _] => m.isEmpty
    case i: Iterable[Any] => i.isEmpty
    case null | None | "" => true
    case Some(x) => isNullOrEmpty(x)
    case _ => false
  }

  def getObjectNode = objMapper.createObjectNode()

  def getArrayNode = objMapper.createArrayNode()

  def generateRandomStr(len: Int): String = {
    val ZERO = Character.valueOf('0')
    val A = Character.valueOf('A')
    val sb = new StringBuffer()
    for (i <- 1 to len) {
      var n = (36.0 * Math.random).asInstanceOf[Int]
      if (n < 10) {
        n = ZERO + n
      }
      else {
        n -= 10
        n = A + n
      }
      sb.append(new Character(n.asInstanceOf[Char]))
    }
    new String(sb)
  }

  def generateSecureRandom: String = {
    val random: SecureRandom = new SecureRandom()
    new BigInteger(130, random).toString(32)
  }

  def getDetail(obj: Any, path: String, splitter: String = "/"): Option[Any] = {

    var myObj = obj
    val parts = path.split(splitter)
    var i = 0
    do {
      var index = parts(i)
      var negated = false
      if (parts(i).startsWith("~")) {
        index = parts(i).split("~").tail.head
        negated = true
      }
      val value = negated match {
        case true => myObj match {
          case _: Map[_, _] => myObj.asInstanceOf[Map[String, Any]].filter(_._1 != index)
          case _: List[_] =>
            if (myObj.asInstanceOf[List[Any]].nonEmpty)
              myObj.asInstanceOf[List[Any]] diff List[Any](myObj.asInstanceOf[List[Any]].get(index.toInt))
            else
              return None
          case _: java.util.Map[_, _] => myObj.asInstanceOf[java.util.Map[String, AnyRef]].filter(_._1 != index)
          case _: java.util.List[_] =>
            if (myObj.asInstanceOf[java.util.List[Any]].nonEmpty)
              myObj.asInstanceOf[java.util.List[Any]] diff List[Any](myObj.asInstanceOf[java.util.List[Any]].get(index.toInt))
            else
              return None
          case _ => return None
        }
        case false => myObj match {
          case _: Map[_, _] => myObj.asInstanceOf[Map[String, Any]].get(index)
          case _: List[_] =>
            if (myObj.asInstanceOf[List[Any]].length > index.toInt)
              myObj.asInstanceOf[List[Any]].get(index.toInt)
            else
              return None
          case _: java.util.Map[_, _] => myObj.asInstanceOf[java.util.Map[String, AnyRef]].get(index)
          case _: java.util.List[_] =>
            if (myObj.asInstanceOf[java.util.List[Any]].length >= index.toInt)
              myObj.asInstanceOf[java.util.List[Any]].get(index.toInt)
            else
              return None
          case _ => return None
        }
      }
      myObj = value match {
        case Some(null) => return None
        case Some(x) => x
        case Nil => return None
        case null => return None
        case None => return None
        case _ => value
      }
      i = i + 1
    } while (i < parts.size)
    Some(myObj)
  }

  val allowedCharsSet = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Set('_', '-', ':', '.', '|')).toSet
}
