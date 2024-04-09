package com.resy

import org.mockito.ArgumentMatchers.{eq => eql, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time._
import java.util.TimeZone
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class BookReservationWorkflowTest extends AsyncWordSpec with Matchers with Eventually {
  implicit val config: PatienceConfig = PatienceConfig(timeout = 5.seconds)

  private val clock = Clock.fixed(Instant.now(), ZoneId.of("GMT"))

  private val venue = Venue(
    id                      = "abcdef",
    hourOfDayToStartBooking = 10,
    advance                 = 10.days,
    diningTypes             = List.empty,
    info                    = None,
    timeZone                = TimeZone.getTimeZone("GMT")
  )

  private val preferences: List[Preference] = List(
    Preference(time = LocalTime.of(18, 0), Some("Inside")),
    Preference(time = LocalTime.of(18, 30), Some("Inside")),
    Preference(time = LocalTime.of(18, 0), None),
    Preference(time = LocalTime.of(18, 30), None)
  )

  private val details = BookingDetails(
    authToken      = "12345",
    apiKey         = "67890",
    venue          = venue,
    date           = LocalDate.now(clock),
    preferences    = preferences,
    partySize      = 2,
    retryTimeout   = 5.seconds,
    wakeAdjustment = 5.seconds
  )
  private val mockApi: ResyApiWrapper = mock(classOf[ResyApiWrapper])
  private val underTest               = new BookReservationWorkflow(mockApi)(details)

  "testGetLeadTime should invoke the API" in {
    val leadTime = 10.days
    when(mockApi.execute(eql(ApiDetails.Config), any[Map[String, String]])(any[BookingDetails]))
      .thenReturn(Future.successful(s"""{"lead_time_in_days": ${leadTime.toDays}}"""))

    eventually {
      underTest.getLeadTime.map(_ shouldBe leadTime)
    }
  }

  "testGetLeadTime should invoke the API and use the value from the API over config" in {
    val leadTime = 5.days
    when(mockApi.execute(eql(ApiDetails.Config), any[Map[String, String]])(any[BookingDetails]))
      .thenReturn(Future.successful(s"""{"lead_time_in_days": ${leadTime.toDays}}"""))

    eventually {
      underTest.getLeadTime.map(_ shouldBe leadTime)
    }
  }

  "finding a reservation" should {
    "respect the order of preferences as defined" in {
      val respJson =
        """{
          |  "results": {
          |    "venues": [
          |      {
          |        "slots": [
          |          {
          |            "date": {
          |              "start": "2023-12-30 18:00:00"
          |            },
          |            "config": {
          |              "type": "Outside",
          |              "token": "Outside/18:00"
          |            }
          |          },
          |          {
          |            "date": {
          |              "start": "2023-12-30 18:30:00"
          |            },
          |            "config": {
          |              "type": "Inside",
          |              "token": "Inside/18:30"
          |            }
          |          }
          |        ]
          |      }
          |    ]
          |  }
          |}""".stripMargin

      when(
        mockApi.execute(eql(ApiDetails.FindReservation), any[Map[String, String]])(
          any[BookingDetails]
        )
      )
        .thenReturn(Future.successful(respJson))

      eventually {
        underTest.retryFindReservation.map(_ shouldBe "Inside/18:30")
      }
    }

    "select a given time over a dining type if it appears earlier in the list" in {
      val respJson =
        """{
          |  "results": {
          |    "venues": [
          |      {
          |        "slots": [
          |          {
          |            "date": {
          |              "start": "2023-12-30 18:00:00"
          |            },
          |            "config": {
          |              "type": "Outside",
          |              "token": "Outside/18:00"
          |            }
          |          },
          |          {
          |            "date": {
          |              "start": "2023-12-30 18:30:00"
          |            },
          |            "config": {
          |              "type": "Inside",
          |              "token": "Inside/18:30"
          |            }
          |          }
          |        ]
          |      }
          |    ]
          |  }
          |}""".stripMargin

      when(
        mockApi.execute(eql(ApiDetails.FindReservation), any[Map[String, String]])(
          any[BookingDetails]
        )
      )
        .thenReturn(Future.successful(respJson))

      eventually {
        new BookReservationWorkflow(mockApi)(
          details.copy(preferences =
            List(
              Preference(LocalTime.of(18, 0), Some("Inside")),
              Preference(LocalTime.of(18, 0), None),
              Preference(LocalTime.of(18, 30), Some("Inside"))
            )
          )
        ).retryFindReservation.map(_ shouldBe "Outside/18:00")
      }
    }
  }

//  "testGetReservationDetails" in {}
//
//  "testBookReservation" in {}

}
