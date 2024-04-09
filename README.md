# resy-booking-bot

## Introduction

This is a reservation booking bot designed to snipe reservations from resy.com using the Resy API. New
reservations become available at midnight everyday, 30 days from the current date. When running the bot, it will sleep
until midnight and wake up to try to snipe a reservation. It will attempt to grab a reservation for a couple of seconds
and shutdown, outputting whether is it was or wasn't successful in getting a reservation.

## Usage

Configuration for a bot run is supplied in the `src/main/resources/application.conf` file.

The fields are as follows:

`booking-details`
---
Main configuration section

`auth-token`: Grabs the auth token from an environment variable `API_KEY` - pass this in at runtime.

`retry-timeout`: Period the bot will attempt to make a reservation for.

`api-key`: This is the Resy API key and can be extracted from calls on the website.
It changes relatively infrequently.

`date`: The date on which you intend to make your booking

`party-size`: Number of people being booked for

`venue`: A reference to one of the defined venues in this file. This uses `${venue."<<name>>"}` syntax
(please note the quoting to correctly reference venue names with punctuation or spaces)

`preferences`: An ordered collection of `time`s and `dining-type`s that you want to attempt to book.
These will be matched in order of their definition. Omitting the `dining-type` will infer that you
don't have a preference of type and will select whatever is available at that time.

#### Note

Preferences will be strictly applied in the order as defined here and will be applied
to the ordered data returned from Resty. 

As an example:

```hocon
preferences = [
    {
        time = "19:00"
        dining-type = "Dining Room"
    },
    {
        time = "19:00"
    }
]
```

will match exactly against a 7pm booking for the "Dining Room" and failing that
will fallback to a 7pm booking for any table type.

```hocon
preferences = [
  {
    time = "19:00"
  },
  {
    time = "18:00",
    dining-type = "Indoor"
  }
]
```

will match any booking at 7pm and failing that look for an Indoor booking at 6pm.
Please ensure that preferences are carefully set in the order required to maximise
potential for sniping.

Note also that times are _EXACT MATCHES_ there is no fuzziness with bookings (at present)
so be careful when attempting to snipe that you are aware of the times the restaurant
releases.

`venues`
---
A list of all venues that you potentially want to book for.
Each entry should have a name uniquely identifying it. These are configured as the human readable
venue names for ease of configuration.

`id`: Resy's internal Id for the venue

`name`: The name of the venue

`advance`: How far in advance you can make bookings for this venue (informational only - the bot will verify advance
booking windows)

`hour-of-day-to-start-booking`: Hour at which new bookings are released (0 = midnight) NB: see timezone below

`dining-types`: List of supported dining-types (NB: this isn't validated and is for general information when setting
your preferences)

`info`: Any other human readable info about the venue (Not used by the bot)

`time-zone`: The timezone in which the restaurant is located - used for establishing sleep periods for
the bot.

#### Note:
The bot now has functionality built in to establish the user's current timezone and the offset to the
restaurant being booked (based on the `time-zone` configured for the venue). This allows for bookings to
be made whilst in a different timezone without having to adjust when the booking window will open for the
venue. Please ensure that any restaurants configured have correct time-zone information set (defaults to
US/East time).

## Execution
The main entry point of the bot is in ResyBookingBot under the main function. Upon running the bot, it will
evaluate your `application.conf` file and determine if the booking date is open for a reservation.
If the reservation window is not et open, the system will evaluate how long it needs to sleep before attempting to
snipe.

Upon waking, the bot will attempt to secure a reservation for a period of 10 seconds to cover any variation in release
times.

When times are retrieved, it will try to find the best available time slot given your priority list of reservation
times.  

If a time can be booked, it will make an attempt to snipe it.
Otherwise it will report that it was unable to acquire a reservation.  
In the event it was unable to get any reservations for 10 seconds, the bot will automatically shutdown.

## Repository Configuration For Github Workflows

### Github Actions

Enable GitHub actions for the repository under:

`Settings >> Code and automation >> Actions >> General >> Actions permissions`
* check `Allow all actions and reusable workflows`

`Settings >> Code and automation >> Actions >> General >> Workflow permissions`
* Check `Read and write permissions`

### Scala CI
This action builds and tests the project and reports back on status and code coverage.

It is triggered on Pull Requests and merges to master branch.

#### formatting
Verifies the file formatting complies with the rules defined in the `.scalafmt.conf` file.

#### build
Builds, tests and reports coverage of the project.

#### JUnit Test Report
Exposes the test report in the actions status.

### Release
This action runs on a commit to the master branch only.

#### publish
Tags the repository with a new version number and produces release artifacts (jar files).

### Codecov
Provides code coverage reports for your repositories. Used in the Scala CI pipeline.

Sign up here: https://about.codecov.io/sign-up/

Codecov is free for the developer plan which is sufficient for working with the bot.

You will need to grant the codecov app access to the repository and additionally define
and setup a secret holidng your CODECOV_TOKEN (see documentation)
