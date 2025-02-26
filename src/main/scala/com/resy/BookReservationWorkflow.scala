package com.resy

import com.resy.BookingDetails._
import com.resy.ResyApiWrapper._
import org.joda.time.DateTime
import play.api.libs.json.JsResult.Exception
import play.api.libs.json.{JsArray, JsError, JsValue, Json}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps
import collection.IndexedSeq

object BookReservationWorkflow {
  implicit val testing = false

  /**
    * STEP 1: FIND RESERVATION (GET CONFIG ID)
    * @return
    */
  private[this] def findReservation: Future[String] = {
    val findResQueryParams = Map(
      "day"        -> day,
      "lat"        -> "0",
      "long"       -> "0",
      "party_size" -> partySize,
      "venue_id"   -> venueId
    )

    sendGetRequest(ResyApiMapKeys.FindReservation, findResQueryParams)
  }

  /**
    * STEP 2: GET RESERVATION DETAILS (GET PAYMENT ID AND BOOK TOKEN)
    * @param configId
    * @return
    */
  def getReservationDetails(configId: String) = {
    val findResQueryParams = Map("config_id" -> configId, "day" -> day, "party_size" -> partySize)

    sendGetRequest(ResyApiMapKeys.ReservationDetails, findResQueryParams)
  }

  /**
    * STEP 3: BOOK RESERVATION
    * @param resDetailsResp
    * @return
    */
  def bookReservation(resDetailsResp: String) = {
    val resDetails = Json.parse(resDetailsResp)
    println(s"${DateTime.now} URL Response: $resDetailsResp")

    //PaymentMethodId - Searching for this pattern - "payment_methods": [{"is_default": true, "provider_name": "braintree", "id": 123456, "display": "1234", "provider_id": 1}]
    val paymentMethodId =
      (resDetails \ "user" \ "payment_methods" \ 0 \ "id").get.toString

    println(s"${DateTime.now} Payment Method Id: $paymentMethodId")

    //BookToken - Searching for this pattern - "book_token": {"value": "book_token_value"
    val bookToken =
      (resDetails \ "book_token" \ "value").get.toString
        .stripPrefix("\"")
        .stripSuffix("\"")

    println(s"${DateTime.now} Book Token: $bookToken")

    val bookResQueryParams = Map(
      "book_token"            -> bookToken,
      "struct_payment_method" -> s"""{"id":$paymentMethodId}""",
      "source_id"             -> "resy.com-venue-details"
    )

    sendPostRequest(ResyApiMapKeys.BookReservation, bookResQueryParams)
  }

  /**
    * Same as Step 1 but does a retry.  Blocks because the reservation can't proceed without an available reservation
    * @param endTime
    * @return
    */
  @tailrec
  def retryFindReservation(endTime: Long): String = {
    println("Attempting to find reservation slot")
    val findResResp = Await.result(findReservation, 6 seconds)

    println(s"${DateTime.now} URL Response: $findResResp")

    //ConfigId - Searching for this pattern - "time_slot": "17:15:00", "badge": null, "service_type_id": 2, "colors": {"background": "2E6D81", "font": "FFFFFF"}, "template": null, "id": 123457

    val results = Try(
      (Json.parse(findResResp) \ "results" \ "venues" \ 0 \ "slots").get
        .as[JsArray]
        .value
    )

    results match {
      case Success(reservationTimes) =>
        findReservationTime(reservationTimes, times, dining_type, endTime)
      case Failure(_) if endTime - DateTime.now.getMillis > 0 =>
        retryFindReservation(endTime)
      case _ =>
        throw new Exception(JsError("Could not find a reservation slot"))
    }
  }

  @tailrec
  private[this] def findReservationTime(
    reservationTimes: IndexedSeq[JsValue],
    timePrefsLeft: Seq[String],
    typePref: String,
    endTime: Long
  ): String = {
    println("Attempting to find reservation time from prefs")
    val reservation =
      if (typePref.isEmpty) {
        Try(
          (reservationTimes.filter(x => (x \ "date" \ "start").get.toString == s""""${timePrefsLeft.head}"""")(
            0
          ) \ "config" \ "token").get.toString
        )
      } else {
        Try(
          (reservationTimes.filter(x => (x \ "date" \ "start").get.toString == s""""${timePrefsLeft.head}"""" && (x \ "config" \ "type").get.toString.equalsIgnoreCase(s""""${typePref}""""))(
            0
          ) \ "config" \ "token").get.toString
        )
      }

    reservation match {
      case Success(configId) =>
        println(s"${DateTime.now} Config Id: $configId")
        configId.substring(1, configId.length()-1)
      case Failure(_) if timePrefsLeft.nonEmpty =>
        findReservationTime(reservationTimes, timePrefsLeft.tail, typePref, endTime)
      case Failure(_) if timePrefsLeft.isEmpty && (endTime - DateTime.now.getMillis) > 0 =>
        println("Couldn't find reservation at given time(s), trying again...")
        retryFindReservation(endTime) //retry again until endtime is up
      case _ =>
        throw new Exception(JsError("Could not find a reservation for the given time(s)"))
    }
  }
}
