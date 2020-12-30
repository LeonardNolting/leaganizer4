import Temporal.Patterns.formatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

@Suppress("MemberVisibilityCanBePrivate", "unused")
object Temporal {
	val utcZone: ZoneId = ZoneId.from(ZoneOffset.UTC)
	val defaultAlternativeZoneIds: List<ZoneId?> = listOf(
		ZoneId.of("America/New_York")
	)

	object Patterns {
		val String.formatter: DateTimeFormatter get() = DateTimeFormatter.ofPattern(this)

		const val date = "yyyy-MM-dd"
		const val time24 = "HH:mm"
		const val time12 = "hh:mm a"
		const val zone = "VV"

		val dateTime24 = "$date $time24"
		val dateTime12 = "$date $time12"
		val dateTime24Zoned = "$dateTime24 $zone"
		val dateTime12Zoned = "$dateTime12 $zone"
		val time24Zoned = "$time24 $zone"
		val time12Zoned = "$time12 $zone"
	}

	fun parse(input: String, formatter: DateTimeFormatter): TemporalAccessor = formatter.parse(input)
	fun parse(input: String, pattern: String): TemporalAccessor = parse(input, pattern.formatter)
	fun parse(input: String, formatters: List<out DateTimeFormatter>): TemporalAccessor {
		formatters.forEach { formatter ->
			try {
				return@parse parse(input, formatter)
			} catch (e: Exception) {
			}
		}
		error("None of the given formatters could parse input `$input`.")
	}

	@JvmName("parseByPatterns")
	fun parse(input: String, patterns: List<String>) = parse(input, patterns.map { it.formatter })

	fun parseDate(input: String): LocalDate = LocalDate.parse(input, Patterns.date.formatter)
	fun parseTime(input: String): LocalDateTime =
		try {
			LocalDateTime.parse(input, Patterns.time24.formatter)
		} catch (e: Exception) {
			try {
				LocalDateTime.parse(input, Patterns.time12.formatter)
			} catch (e: Exception) {
				error("Couldn't parse time.")
			}
		}

	fun parseDateTime(input: String): LocalDateTime =
		try {
			LocalDateTime.parse(input, Patterns.dateTime24.formatter)
		} catch (e: Exception) {
			try {
				LocalDateTime.parse(input, Patterns.dateTime12.formatter)
			} catch (e: Exception) {
				error("Couldn't parse date and time.")
			}
		}

	fun parseDateTimeZoned(input: String): ZonedDateTime =
		try {
			ZonedDateTime.parse(input, Patterns.dateTime24Zoned.formatter)
		} catch (e: Exception) {
			try {
				ZonedDateTime.parse(input, Patterns.dateTime12Zoned.formatter)
			} catch (e: Exception) {
				error("Couldn't parse date, time and timezone.")
			}
		}

	fun parseDateTimeZoned(input: String, defaultZoneId: ZoneId? = null): ZonedDateTime =
		try {
			parseDateTimeZoned(input)
		} catch (e: Exception) {
			try {
				if (defaultZoneId != null) parseDateTime(input).atZone(defaultZoneId)
				else throw Exception()
			} catch (e: Exception) {
				error("Couldn't parse date, time and timezone. This time requires a timezone in the format Continent/City. Please add it, or set one in your settings via `-settings-timezone-set`.")
			}
		}


	fun format(input: TemporalAccessor, formatter: DateTimeFormatter): String = formatter.format(input)

	fun zoned(input: LocalDateTime, zoneId: ZoneId): ZonedDateTime = input.atZone(zoneId)
	fun zoned(input: Instant, zoneId: ZoneId = utcZone): ZonedDateTime = input.atZone(zoneId)
	fun zoned(input: ZonedDateTime, zoneId: ZoneId): ZonedDateTime =
		input.withZoneSameInstant(zoneId)

	fun utc(input: LocalDateTime) = zoned(input, utcZone)
	fun utc(input: Instant) = zoned(input, utcZone)
	fun utc(input: ZonedDateTime) = zoned(input, utcZone)

	fun displayDate(input: LocalDate) = format(input, Patterns.date.formatter)
	fun displayDate(input: ZonedDateTime) = format(input, Patterns.date.formatter)
	fun displayUTCDate(input: ZonedDateTime) = displayDate(utc(input))
	fun displayDate(input: Instant) = displayDate(utc(input))

	fun displayTime12(input: ZonedDateTime, showTimezone: Boolean = true) =
		format(input, if (showTimezone) Patterns.time12Zoned.formatter else Patterns.time12.formatter)

	fun displayTime24(input: ZonedDateTime, showTimezone: Boolean = true) =
		format(input, if (showTimezone) Patterns.time24Zoned.formatter else Patterns.time24.formatter)

	fun displayTime(input: ZonedDateTime, showTimezone: Boolean = true) = displayTime24(input, showTimezone)

	fun displayUTCTime12(input: ZonedDateTime, showTimezone: Boolean = true) = displayTime12(utc(input), showTimezone)
	fun displayUTCTime24(input: ZonedDateTime, showTimezone: Boolean = true) = displayTime24(utc(input), showTimezone)
	fun displayUTCTime(input: ZonedDateTime, showTimezone: Boolean = true) =
		displayUTCTime24(input, showTimezone)

	fun displayTime12(input: Instant, showTimezone: Boolean = true) = displayTime12(utc(input), showTimezone)
	fun displayTime24(input: Instant, showTimezone: Boolean = true) =
		displayTime24(utc(input), showTimezone)

	fun displayTime(input: Instant, showTimezone: Boolean = true) =
		displayTime24(input, showTimezone)

	fun displayUTC(input: ZonedDateTime) = displayUTCDate(input) + " " + displayUTCTime(input)

	fun displayTimeFull(
		input: ZonedDateTime,
		showTimezone: Boolean = true,
		alternativeZoneIds: List<ZoneId?> = defaultAlternativeZoneIds
	) = displayUTCTime(input, showTimezone) + " " + "(${
		alternativeZoneIds.filterNotNull().joinToString(" / ") { zoneId -> displayTime(zoned(input, zoneId)) }
	})"

	fun displayTimeFull(
		input: Instant,
		showTimezone: Boolean = true,
		alternativeZoneIds: List<ZoneId?> = defaultAlternativeZoneIds
	) = displayTimeFull(utc(input), showTimezone, alternativeZoneIds)

	fun display(input: ZonedDateTime, date: Boolean = true) =
		if (date) displayDate(input) + " " + displayTime(input)
		else displayTime(input)

	fun display(input: Instant, date: Boolean = true) = display(utc(input), date)

	fun displayFull(
		input: ZonedDateTime,
		date: Boolean = true,
		alternativeZoneIds: List<ZoneId?> = defaultAlternativeZoneIds
	): String {
		val time = displayTimeFull(input, alternativeZoneIds = alternativeZoneIds)
		return if (date) displayDate(input) + " " + time else time
	}

	fun displayFull(
		input: Instant,
		date: Boolean = true,
		alternativeZoneIds: List<ZoneId?> = defaultAlternativeZoneIds
	) = displayFull(utc(input), date, alternativeZoneIds)
}
