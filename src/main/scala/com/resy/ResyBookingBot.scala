package com.resy

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.lang3.time.DurationFormatUtils
import org.apache.pekko.actor.ActorSystem
import play.api.libs.json._
import pureconfig._
import pureconfig.generic.auto._

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Success, Try}

object ResyBookingBot extends App with StrictLogging {

  implicit private val config: BookingDetails =
    ConfigSource.default.at("booking-details").loadOrThrow[BookingDetails]

  logger.info(s"Starting Resy Booking Bot with config: $config")

  private val system = ActorSystem()

  private val workflow = BookReservationWorkflow(config)

  private def bookReservationWorkflow(): Unit = {
    logger.info(s"Attempting to snipe reservation")

    val result: Future[Option[String]] = for {
      findResResp    <- workflow.retryFindReservation
      resDetailsResp <- workflow.getReservationDetails(findResResp)
      bookResResp    <- workflow.bookReservation(resDetailsResp)
    } yield {
      val bookDetails = Json.parse(bookResResp)
      logger.info(s"Booking Response: $bookDetails")
      (bookDetails \\ "reservation_id").headOption.map(_.toString)
    }

    Try(Await.result(result, config.retryTimeout)) match {
      case Failure(_: TimeoutException) =>
        logger.error(s"Failed to snipe - Retry period exceeded")
      case Failure(f: ReservationAlreadyMade) =>
        logger.error(
          s"Failed to snipe - Reservation already in place! On ${f.date} at ${f.time} id: ${f.id}"
        )
      case Failure(t) =>
        logger.error(s"Failed to snipe - ${t.getMessage}", t)
      case Success(Some(token)) =>
        logger.info(s"Successfully sniped reservation")
        logger.info(s"ReservationId: $token")
      case Success(None) =>
        logger.info("Failed to snipe - reservation already exists?")
    }

    logger.info("Shutting down Resy Booking Bot")

    ResyApiWrapper.shutdown()

    System.exit(0)
  }

  workflow.getLeadTime.map { leadTime =>
    if (config.inBookingWindow(leadTime)) {
      logger.info("Within booking window for venue - attempting immediate reservation")
      bookReservationWorkflow()
    } else {
      val durationToSleep: FiniteDuration =
        config.secondsToBookingWindowStart(leadTime) - config.wakeAdjustment
      logger.info(
        // format: off
        s"""
           |  Booking window is $leadTime available at ${config.venue.hourOfDayToStartBooking} ${config.venue.timeZone.getDisplayName}
           |  For the current booking, we will wake ${config.wakeAdjustment} before ${config.bookingWindowStart(leadTime)}
           |  Local time is currently                                ${ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES)}
           |  Calculated sleep period of ${DurationFormatUtils.formatDuration(durationToSleep.toMillis, "d' days 'HH:mm:ss")}""".stripMargin
        // format: on
      )

      system.scheduler.scheduleOnce(durationToSleep)(bookReservationWorkflow())
    }
  }
}
