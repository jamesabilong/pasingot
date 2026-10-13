package app.personal.workouttracker.shared

/** Keep cache validation aligned with the phone's authored circuit contract. */
fun validScheduleCircuits(rows: List<ScheduleRow>): Boolean {
    if (rows.any { row -> row.circuit?.let {
            !Regex("[A-Za-z0-9_-]{1,100}").matches(it.id) || it.restSeconds !in 0..3600
        } == true }) return false
    for (slot in rows.groupBy { it.day to it.time }.values) {
        val seen = mutableSetOf<String>()
        var index = 0
        while (index < slot.size) {
            val first = slot[index]
            val circuit = first.circuit
            if (circuit == null) { index += 1; continue }
            if (!seen.add(circuit.id)) return false
            var end = index + 1
            while (end < slot.size && slot[end].circuit?.id == circuit.id) end += 1
            val members = slot.subList(index, end)
            if (members.size !in 2..24 || first.sets !in 1..100 || members.any {
                    it.circuit != circuit || it.sets != first.sets || it.questId != null ||
                        it.dateOverrides != first.dateOverrides
                }) return false
            index = end
        }
    }
    return true
}

class CircuitRequiresPhoneException : IllegalArgumentException(
    "Today's supersets and circuits require the phone workout player."
)
