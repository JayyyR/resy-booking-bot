package com.resy

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike

import java.time._
import java.util.TimeZone
import scala.concurrent.duration.DurationInt

class BookingDetailsTest extends AnyWordSpecLike {

  val details: BookingDetails = BookingDetails(
    authToken = "",
    apiKey    = "",
    venue = Venue(
      id                      = "1",
      timeZone                = TimeZone.getTimeZone("America/New_York"),
      hourOfDayToStartBooking = 10,
      advance                 = 14.days
    ),
    date           = LocalDate.parse("2024-01-01"),
    preferences    = List(Preference(LocalTime.parse("20:00"), Some("Inside"))),
    partySize      = 1,
    retryTimeout   = 5.seconds,
    wakeAdjustment = 2.seconds
  )

  "Calculate the start time for a booking window correctly from the finite duration" in {
    details.bookingWindowStart(14.days) shouldBe ZonedDateTime.parse(
      "2023-12-18T10:00-05:00[America/New_York]"
    )
    details.bookingWindowStart(2.days) shouldBe ZonedDateTime.parse(
      "2023-12-30T10:00-05:00[America/New_York]"
    )
    details.bookingWindowStart(30.days) shouldBe ZonedDateTime.parse(
      "2023-12-02T10:00-05:00[America/New_York]"
    )
    details.bookingWindowStart(162.days) shouldBe ZonedDateTime.parse(
      "2023-07-23T10:00-04:00[America/New_York]"
    )
  }

  "Calculate the start time of the booking window correctly when in a different TimeZone to the restaurant" in {
    val londonDetails =
      details.copy(venue = details.venue.copy(timeZone = TimeZone.getTimeZone("Europe/London")))

    londonDetails.bookingWindowStart(14.days) shouldBe ZonedDateTime.parse(
      "2023-12-18T10:00Z[Europe/London]"
    )
    londonDetails.bookingWindowStart(162.days) shouldBe ZonedDateTime.parse(
      "2023-07-23T10:00+01:00[Europe/London]"
    )
  }

  "Calculate the sleep time to the booking window correctly based on the local date/time" in {
    details
      .secondsToBookingWindowStart(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-30T10:00:00-05:00"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      )
      .toHours shouldBe 24

    details
      .secondsToBookingWindowStart(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-30T15:00:00Z"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      )
      .toHours shouldBe 24

    details
      .secondsToBookingWindowStart(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-30T10:00:00Z"),
          TimeZone.getTimeZone("Europe/London").toZoneId
        )
      )
      .toHours shouldBe 29
  }

  "Verify we are inside/outside the booking window correctly" in {
    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-30T10:00:00-05:00"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      ) shouldBe false

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-30T15:00:00Z"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      ) shouldBe false

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-30T10:00:00Z"),
          TimeZone.getTimeZone("Europe/London").toZoneId
        )
      ) shouldBe false

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-31T09:59:59-05:00"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      ) shouldBe false

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-31T10:00:00-05:00"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      ) shouldBe true

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-31T15:00:00Z"),
          TimeZone.getTimeZone("America/New_York").toZoneId
        )
      ) shouldBe true

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-31T10:00:00Z"),
          TimeZone.getTimeZone("Europe/London").toZoneId
        )
      ) shouldBe false

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-31T14:59:00Z"),
          TimeZone.getTimeZone("Europe/London").toZoneId
        )
      ) shouldBe false

    details
      .inBookingWindow(
        1.days,
        Clock.fixed(
          Instant.parse("2023-12-31T15:00:00Z"),
          TimeZone.getTimeZone("Europe/London").toZoneId
        )
      ) shouldBe true
  }
}
