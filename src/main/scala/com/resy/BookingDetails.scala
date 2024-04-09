package com.resy

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import scala.concurrent.duration._

final case class Venue(
  id: String,
  hourOfDayToStartBooking: Int = 0,
  advance: FiniteDuration = 7.days,
  diningTypes: List[String] = List.empty,
  info: Option[String] = None,
  timeZone: TimeZone = TimeZone.getTimeZone("America/New_York")
)

@SuppressWarnings(Array("scalafix:MissingFinal"))
case class Preference(
  time: LocalTime,
  diningType: Option[String] = None
)

final case class BookingDetails(
  authToken: String,
  apiKey: String,
  venue: Venue,
  date: LocalDate,
  preferences: List[Preference],
  partySize: Int,
  retryTimeout: FiniteDuration,
  wakeAdjustment: FiniteDuration
) {

  val day: String = date.format(DateTimeFormatter.ISO_DATE)

  def inBookingWindow(
    leadTime: FiniteDuration,
    clock: Clock = Clock.systemDefaultZone()
  ): Boolean =
    bookingWindowStart(leadTime).toInstant.getEpochSecond <= clock.instant().getEpochSecond

  def bookingWindowStart(leadTime: FiniteDuration): ZonedDateTime =
    date
      .atStartOfDay(venue.timeZone.toZoneId)
      .minus(leadTime.toDays, ChronoUnit.DAYS)
      .plusHours(venue.hourOfDayToStartBooking)

  def secondsToBookingWindowStart(
    leadTime: FiniteDuration,
    clock: Clock = Clock.systemDefaultZone()
  ): FiniteDuration =
    (bookingWindowStart(leadTime).toEpochSecond - clock.instant().getEpochSecond).seconds
}
