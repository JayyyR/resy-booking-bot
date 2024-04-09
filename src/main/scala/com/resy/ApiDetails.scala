package com.resy

sealed class ApiDetails(val ctx: String, val method: String, val contentType: String) {
  val url = s"https://api.resy.com$ctx"
}

object ApiDetails {
  case object Config extends ApiDetails("/2/config", "GET", "text/plain")
  case object FindReservation extends ApiDetails("/4/find", "GET", "text/plain")
  case object ReservationDetails extends ApiDetails("/3/details", "GET", "text/plain")

  case object BookReservation
      extends ApiDetails("/3/book", "POST", "application/x-www-form-urlencoded")
}
