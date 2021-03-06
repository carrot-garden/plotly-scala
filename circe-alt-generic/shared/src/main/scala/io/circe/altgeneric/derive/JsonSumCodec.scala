package io.circe.altgeneric
package derive

import cats.data.Xor
import io.circe._

abstract class JsonSumCodec {
  def encodeEmpty: Nothing
  def encodeField(fieldOrObj: Either[Json, (String, Json)]): Json

  def decodeEmpty(cursor: HCursor): Decoder.Result[Nothing]
  def decodeField[A](name: String, cursor: HCursor, decode: Decoder[A]): Decoder.Result[Either[ACursor, A]]
}

abstract class JsonSumCodecFor[S] {
  def codec: JsonSumCodec
}

object JsonSumCodecFor {
  def apply[S](codec0: JsonSumCodec): JsonSumCodecFor[S] =
    new JsonSumCodecFor[S] {
      def codec = codec0
    }

  implicit def default[T]: JsonSumCodecFor[T] =
    JsonSumCodecFor(JsonSumCodec.obj)
}

object JsonSumCodec {
  val obj: JsonSumCodec = new JsonSumObjCodec
  val typeField: JsonSumCodec = new JsonSumTypeFieldCodec
}

class JsonSumObjCodec extends JsonSumCodec {

  def toJsonName(name: String): String = name

  def encodeEmpty: Nothing =
    throw new IllegalArgumentException("empty")
  def encodeField(fieldOrObj: Either[Json, (String, Json)]): Json =
    fieldOrObj match {
      case Left(other) => other
      case Right((name, content)) =>
        Json.obj(toJsonName(name) -> content)
    }

  def decodeEmpty(cursor: HCursor): Decoder.Result[Nothing] =
    Xor.left(DecodingFailure(
      s"unrecognized type(s): ${cursor.fields.getOrElse(Nil).mkString(", ")}",
      cursor.history
    ))
  def decodeField[A](name: String, cursor: HCursor, decode: Decoder[A]): Decoder.Result[Either[ACursor, A]] =
    cursor.downField(toJsonName(name)).either match {
      case Xor.Left(_) =>
        Xor.right(Left(ACursor.ok(cursor)))
      case Xor.Right(content) =>
        decode(content).map(Right(_))
    }
}

class JsonSumTypeFieldCodec extends JsonSumCodec {

  def typeField: String = "type"

  def toTypeValue(name: String) = name

  def encodeEmpty: Nothing =
    throw new IllegalArgumentException("empty")
  def encodeField(fieldOrObj: Either[Json, (String, Json)]): Json =
    fieldOrObj match {
      case Left(other) => other
      case Right((name, content)) =>
        content.mapObject((typeField -> Json.fromString(toTypeValue(name))) +: _)
    }

  def decodeEmpty(cursor: HCursor): Decoder.Result[Nothing] =
    Xor.Left(DecodingFailure(
      cursor.downField(typeField).focus match {
        case None => "no type found"
        case Some(type0) => s"unrecognized type: $type0"
      },
      cursor.history
    ))
  def decodeField[A](name: String, cursor: HCursor, decode: Decoder[A]): Decoder.Result[Either[ACursor, A]] =
    cursor.downField(typeField).as[String] match {
      case Xor.Right(name0) if toTypeValue(name) == name0 =>
        decode(cursor).map(Right(_))
      case _ =>
        Xor.Right(Left(ACursor.ok(cursor)))
    }
}
