package com.leekleak.trafficlight.integrations

/**
 * Parses the bounded output of the allowlisted `content query` probe.
 *
 * Only the four domain fields are extracted. Nothing here formats or logs a record, so a
 * subscriber identifier can never reach a log line through this type.
 */
internal object RootSubscriptionParser {
    const val MAX_RECORDS = 8
    const val MAX_LINES = 512
    const val MAX_LINE_LENGTH = 4_096
    private const val MAX_CARRIER_LENGTH = 64

    fun parseUid(lines: List<String>): Boolean = bounded(lines).singleOrNull()?.trim() == "0"

    fun parseSubscriptions(lines: List<String>): List<SubscriptionRecord> = bounded(lines)
        .asSequence()
        .filter { it.startsWith("Row:") }
        .mapNotNull(::parseRow)
        .filter { it.simSlotIndex >= 0 }
        .distinctBy { it.subscriptionId }
        .take(MAX_RECORDS)
        .toList()

    private fun bounded(lines: List<String>): List<String> = lines
        .take(MAX_LINES)
        .filter { it.length <= MAX_LINE_LENGTH }

    private fun parseRow(line: String): SubscriptionRecord? {
        val body = line.substringAfter("Row:").trim().substringAfter(' ')
        val values = parseAssignments(body)
        val subscriptionId = values["_id"]?.toIntOrNull() ?: return null
        val slot = values["sim_id"]?.toIntOrNull() ?: return null
        return SubscriptionRecord(
            subscriptionId = subscriptionId,
            simSlotIndex = slot,
            carrierName = sanitizeCarrier(values["carrier_name"].orEmpty()),
            subscriberId = values["imsi"]?.takeIf(::isPresent),
        )
    }

    private fun sanitizeCarrier(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(MAX_CARRIER_LENGTH)

    private fun isPresent(value: String): Boolean =
        value.isNotBlank() && !value.equals("null", ignoreCase = true)

    private fun parseAssignments(input: String): Map<String, String> {
        val keys = ASSIGNMENT_KEY.findAll(input).toList()
        return buildMap {
            keys.forEachIndexed { index, match ->
                val start = match.range.last + 1
                val end = keys.getOrNull(index + 1)?.range?.first ?: input.length
                put(match.groupValues[1], input.substring(start, end).trim().trimEnd(','))
            }
        }
    }

    private val ASSIGNMENT_KEY = Regex("(?:^|,\\s*)([A-Za-z_][A-Za-z0-9_]*)=")
}
