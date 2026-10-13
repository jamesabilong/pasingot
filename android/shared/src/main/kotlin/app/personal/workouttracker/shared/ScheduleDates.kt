package app.personal.workouttracker.shared

import java.time.LocalDate

private val dateFormat = Regex("\\d{4}-\\d{2}-\\d{2}")
private val timeFormat = Regex("([01]\\d|2[0-3]):[0-5]\\d")

private fun validScheduleDate(value: String): Boolean = dateFormat.matches(value) && value >= "1900-01-01" &&
    runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)

fun validScheduleDateOverrides(row: ScheduleRow): Boolean =
    (row.dateOverrides.isEmpty() || row.questId == null) && row.dateOverrides.all { (source, target) ->
        validScheduleDate(source) && (target == null || (validScheduleDate(target.date) && timeFormat.matches(target.time)))
    }

/** Resolve on the phone, so the watch still receives an ordinary daily payload. */
fun scheduleRowsOnDate(rows: List<ScheduleRow>, date: String, weekday: String): List<ScheduleRow> {
    require(validScheduleDate(date)) { "Invalid schedule date" }
    require(rows.all(::validScheduleDateOverrides)) { "Invalid date-specific schedule" }
    return rows.flatMap { row ->
        val recurring = if (row.day.equals(weekday, ignoreCase = true) && !row.dateOverrides.containsKey(date)) listOf(row) else emptyList()
        recurring + row.dateOverrides.values.filterNotNull().filter { it.date == date }
            .map { row.copy(day = weekday, time = it.time) }
    }.sortedBy { it.time }
}
