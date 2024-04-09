package com

import play.api.libs.json.{Json, Reads}
import pureconfig.ConfigConvert
import pureconfig.configurable.{
  localDateConfigConvert,
  localDateTimeConfigConvert,
  localTimeConfigConvert
}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.TimeZone
import scala.util.Try

package object resy {

  private val customDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  implicit val localDateTimeReads: Reads[LocalDateTime] =
    Reads.localDateTimeReads(customDateTimeFormatter)

  implicit val localTimeConverter: ConfigConvert[LocalTime] = localTimeConfigConvert(
    DateTimeFormatter.ISO_TIME
  )

  implicit val localDateConverter: ConfigConvert[LocalDate] = localDateConfigConvert(
    DateTimeFormatter.ISO_DATE
  )

  implicit val localDateTimeConverter: ConfigConvert[LocalDateTime] = localDateTimeConfigConvert(
    DateTimeFormatter.ISO_DATE_TIME
  )

  implicit val timeZoneConverter: ConfigConvert[TimeZone] =
    ConfigConvert.viaStringOpt[TimeZone](str => Try(TimeZone.getTimeZone(str)).toOption, _.getID)

  final case class ReservationAlreadyMade(body: String) extends Exception {
    private val json = Json.parse(body)

    val date: LocalDate = (json \ "specs" \ "day").as[LocalDate]
    val time: LocalTime = (json \ "specs" \ "time_slot").as[LocalTime]
    val id: String      = (json \ "specs" \ "reservation_id").get.toString()
  }

  final class Slot(
    start: LocalDateTime,
    override val diningType: Option[String],
    val token: Option[String]
  ) extends Preference(time = start.toLocalTime)
}
