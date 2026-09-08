# bustimes-android

An unofficial Android client for [bustimes.org](https://bustimes.org): a
map of live buses and stops, with departure boards and timetables.

Personal project. Not affiliated with bustimes.org.

## What it does

Opens on a map centred on your approximate location, showing nearby stops
and every tracked bus. Tapping a bus draws its route and slides up the
schedule with expected and actual times; tapping a stop shows its
departure board; tapping a route number opens the full timetable.

## Data

Everything comes from bustimes.org. There is no API key.

| Screen | Endpoint |
| --- | --- |
| Buses on the map | `/vehicles.json?xmin&ymin&xmax&ymax` |
| Stops on the map | `/stops.json?xmin&ymin&xmax&ymax` |
| Selected bus | `/api/trips/<trip_id>/`, `/vehicles.json?service=&trip=` |
| Stop departures | `/stops/<atco>/departures` (HTML) |
| Full timetable | `/services/<id>/timetable.csv`, `/api/trips/?service=&date=` |
| Slug to id | `/api/services/?slug__in=` |

Two of those are not JSON and are parsed from the site's own output, so
`app/src/test/resources` pins recorded responses and the parsers throw
rather than returning empty results. If bustimes.org changes a template
or export, a test fails instead of the app silently going blank.

### Being a reasonable client

- Polls only while the app is in the foreground, never in the background
- 12 second interval, chained after each response rather than fixed, so a
  slow network self-throttles
- Refetches vehicles only when the viewport leaves the area already
  fetched, and cancels superseded requests in flight
- Honours `max-age` and revalidates with `If-None-Match` /
  `If-Modified-Since` via a 20 MB OkHttp disk cache
- Identifies itself honestly in `User-Agent`, with a contact URL
- Corrects for device clock skew using the server's `Date` header, so
  delays are computed against the server's clock

## Installing

Download the APK from the [latest
release](https://github.com/pashri/bustimes-android/releases/latest) and
open it on your phone. Android will ask you to allow installing from an
unknown source, because the app is signed with a personal key rather than
distributed through Play.

It asks for approximate location only, and just to decide where to point
the map. Location is never used in the background: polling stops the
moment the app leaves the foreground.

## Building

Needs JDK 17 and an Android SDK with platform 35.

```sh
./gradlew :app:testDebugUnitTest   # parser tests, against recorded fixtures
./gradlew :app:assembleDebug
```

Release builds are signed only if a `keystore.properties` is present in
the project root, naming a keystore, its passwords and a key alias.
Without one the project still builds; the release variant is simply left
unsigned.

## Basemap

MapLibre Native with [OpenFreeMap](https://openfreemap.org) Positron
(light) and Dark, both keyless. Map data © OpenStreetMap contributors.
