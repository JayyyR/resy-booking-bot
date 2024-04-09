package com.resy

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.actor.ActorSystem
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc.{AhcCurlRequestLogger, AhcWSClient}

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.chaining.scalaUtilChainingOps

class ResyApiWrapper extends StrictLogging {
  implicit private val system: ActorSystem                  = ActorSystem()
  implicit private val dispatcher: ExecutionContextExecutor = system.dispatcher

  private val ws = AhcWSClient()

  private val commonHeaders: BookingDetails => Seq[(String, String)] = details =>
    Seq(
      "Authorization"     -> s"""ResyAPI api_key="${details.apiKey}"""",
      "user-agent"        -> s"Mozilla/5.0 ${UUID.randomUUID().toString}",
      "dnt"               -> "1",
      "accept"            -> "application/json, text/plain",
      "cache-control"     -> "no-cache",
      "X-Resy-Auth-Token" -> details.authToken,
      "referer"           -> "https://widgets.resy.com/",
      "origin"            -> "https://widgets.resy.com/"
    )

  private val responseHandler: WSResponse => Either[Exception, String] = {
    case resp if resp.status < 400 =>
      logger.debug(resp.body)
      Right(resp.body)
    case resp if resp.status == 412 =>
      Left(ReservationAlreadyMade(resp.body))
    case resp =>
      logger.error(s"HTTP ERROR: ${resp.status}: ${resp.body}")
      Left(new Exception(resp.statusText))
  }

  def shutdown(): Unit = ws.close()

  def execute(apiDetails: ApiDetails, queryParams: Map[String, String] = Map.empty)(implicit
    details: BookingDetails
  ): Future[String] = {
    ws.url(apiDetails.url)
      .withRequestFilter(AhcCurlRequestLogger())
      .addHttpHeaders(commonHeaders(details): _*)
      .withMethod(apiDetails.method)
      .pipe { req =>
        (apiDetails.method, apiDetails.contentType) match {
          case ("GET", _) =>
            req.withQueryStringParameters(queryParams.toSeq: _*)
          case ("POST", "application/x-www-form-urlencoded") =>
            req
              .addHttpHeaders("Content-Type" -> apiDetails.contentType)
              .withBody(queryParams)
          case ("POST", contentType) =>
            req
              .addHttpHeaders("Content-Type" -> contentType)
              .withBody(Json.stringify(Json.toJson(queryParams)))
        }
      }
      .execute()
      .map(responseHandler)
  }.flatMap {
    case Right(v) => Future.successful(v)
    case Left(t)  => Future.failed(t)
  }
}

object ResyApiWrapper extends ResyApiWrapper
