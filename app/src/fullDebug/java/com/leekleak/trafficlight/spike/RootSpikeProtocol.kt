package com.leekleak.trafficlight.spike

internal data class SpikeSubscriptionRecord(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val carrierName: String,
    val subscriberIdPresent: Boolean,
)

internal data class SpikeProviderRow(
    val rowId: Int,
    val subscriptionId: Int?,
    val simSlotIndex: Int,
    val carrierName: String,
    val subscriberIdPresent: Boolean,
)

internal data class SpikeDeviceInfo(
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val selinux: String,
    val libsuGrantState: String,
    val shellStatus: String,
    val shellStatusInt: Int,
    val startupShellCached: Boolean,
    val authorizationRequestedByExplicitAction: Boolean,
    val shellCachedBefore: Boolean,
    val suVisibleToApp: Boolean,
)

internal data class SpikeProbeResult(
    val id: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val lineCount: Int,
    val stderrLineCount: Int,
    val recordCount: Int,
    val category: String,
)

internal object RootSpikeProtocol {
    const val MAX_RECORDS = 8
    const val MAX_LINES = 512
    const val MAX_LINE_LENGTH = 4_096

    /** Read-only production-intent probes. Never interpolated. */
    val ALLOWLIST = listOf(
        "id -u",
        "getenforce",
        "content query --uri content://telephony/siminfo --projection _id:sim_id:carrier_name:imsi",
        "content query --uri content://telephony/siminfo --projection _id:subscription_id:sim_id:carrier_name:imsi",
        "dumpsys isub",
    )

    /** Spike-only fault injection. Harmless and non-mutating. */
    val FAILURE_INJECTION = listOf(
        "false",
        "sleep 5",
    )

    fun requireAllowlisted(command: String): String = command.also {
        require(it in ALLOWLIST || it in FAILURE_INJECTION) { "Command is not allowlisted" }
    }

    fun parseUid(lines: List<String>): Boolean = boundedLines(lines).singleOrNull()?.trim() == "0"

    /** UID 0 is the authoritative root proof; libsu status is captured before any probe closes the shell. */
    fun shellStatusLabel(acquiredStatus: Int, uidVerified: Boolean): String = when {
        uidVerified -> "root_uid_0"
        acquiredStatus == 1 -> "root_uid_unverified"
        acquiredStatus == 0 -> "non_root"
        else -> "unknown"
    }

    val PROVIDER_MARKERS = linkedMapOf(
        "rowId" to Regex("(?:^|[,\\s])_id="),
        "subscriptionId" to Regex("(?:^|[,\\s])subscription_id="),
        "simSlot" to Regex("(?:^|[,\\s])sim_id="),
        "carrier" to Regex("(?:^|[,\\s])carrier_name="),
        "subscriberId" to Regex("(?:^|[,\\s])(?:imsi|subscriber_id)="),
    )

    val ISUB_MARKERS = linkedMapOf(
        "subscriptionInfoInternal" to Regex("SubscriptionInfoInternal\\{"),
        "subscriptionInfo" to Regex("(?:^|[^A-Za-z])SubscriptionInfo\\{"),
        "mSubscriptionInfo" to Regex("mSubscriptionInfo="),
        "slotIndexToSubId" to Regex("mSlotIndexToSubId"),
        "simSlotIndex" to Regex("simSlotIndex="),
        "subscriptionId" to Regex("(?:^|[,\\s])(?:id|subscriptionId)="),
    )

    fun markerCounts(lines: List<String>, markers: Map<String, Regex>): String = markers
        .map { (label, pattern) -> "$label=${boundedLines(lines).sumOf { pattern.findAll(it).count() }}" }
        .joinToString(",")

    fun parseSelinux(lines: List<String>): String = when (boundedLines(lines).singleOrNull()?.trim()) {
        "Enforcing" -> "Enforcing"
        "Permissive" -> "Permissive"
        "Disabled" -> "Disabled"
        else -> "unknown"
    }

    fun parseContentQuery(lines: List<String>): List<SpikeSubscriptionRecord> = parseProviderRows(lines)
        .filter { it.simSlotIndex >= 0 }
        .map {
            SpikeSubscriptionRecord(
                subscriptionId = it.subscriptionId ?: it.rowId,
                simSlotIndex = it.simSlotIndex,
                carrierName = it.carrierName,
                subscriberIdPresent = it.subscriberIdPresent,
            )
        }
        .distinctBy { it.subscriptionId }
        .take(MAX_RECORDS)

    fun parseProviderRows(lines: List<String>): List<SpikeProviderRow> = boundedLines(lines)
        .asSequence()
        .filter { it.startsWith("Row:") }
        .mapNotNull(::parseProviderRow)
        .take(MAX_LINES)
        .toList()

    /**
     * `SubscriptionManager` addresses subscriptions by the `subscription_id` column, not by
     * the provider row `_id`. The spike has to prove which one the device actually exposes.
     */
    fun subscriptionIdColumnPresent(rows: List<SpikeProviderRow>): Boolean =
        rows.isNotEmpty() && rows.all { it.subscriptionId != null }

    /** `null` means "cannot tell" because the column was not returned by the provider. */
    fun subscriptionIdMatchesRowId(rows: List<SpikeProviderRow>): Boolean? {
        if (!subscriptionIdColumnPresent(rows)) return null
        return rows.all { it.subscriptionId == it.rowId }
    }

    fun parseIsubDump(lines: List<String>): List<SpikeSubscriptionRecord> = boundedLines(lines)
        .asSequence()
        .filter { it.contains("SubscriptionInfoInternal{") || it.contains("SubscriptionInfo{") }
        .mapNotNull(::parseIsubLine)
        .distinctBy { it.subscriptionId }
        .take(MAX_RECORDS)
        .toList()

    /**
     * Provider query is canonical. The dumpsys path only fills in when the provider
     * returned nothing, so one logical subscription is never counted twice.
     */
    fun selectRecords(
        provider: List<SpikeSubscriptionRecord>,
        isub: List<SpikeSubscriptionRecord>,
    ): Pair<String, List<SpikeSubscriptionRecord>> = when {
        provider.isNotEmpty() -> "provider" to provider
        isub.isNotEmpty() -> "isub" to isub
        else -> "none" to emptyList()
    }

    private fun boundedLines(lines: List<String>): List<String> = lines
        .take(MAX_LINES)
        .filter { it.length <= MAX_LINE_LENGTH }

    private fun parseProviderRow(line: String): SpikeProviderRow? {
        val values = parseAssignments(line.substringAfter("Row:").trim().substringAfter(' '))
        val rowId = values["_id"]?.toIntOrNull() ?: return null
        val slot = values["sim_id"]?.toIntOrNull() ?: return null
        val imsi = values["imsi"] ?: values["subscriber_id"]
        return SpikeProviderRow(
            rowId = rowId,
            subscriptionId = values["subscription_id"]?.toIntOrNull(),
            simSlotIndex = slot,
            carrierName = values["carrier_name"].orEmpty().trim(),
            subscriberIdPresent = isPresent(imsi),
        )
    }

    private fun parseIsubLine(line: String): SpikeSubscriptionRecord? {
        val body = line
            .substringAfter("SubscriptionInfoInternal{", line.substringAfter("SubscriptionInfo{"))
            .substringBeforeLast('}')
        val values = parseAssignments(body)
        val id = (values["id"] ?: values["subscriptionId"])?.toIntOrNull() ?: return null
        val slot = values["simSlotIndex"]?.toIntOrNull() ?: return null
        return SpikeSubscriptionRecord(
            subscriptionId = id,
            simSlotIndex = slot,
            carrierName = values["carrierName"].orEmpty().trim(),
            subscriberIdPresent = isPresent(values["imsi"] ?: values["subscriberId"]),
        )
    }

    private fun isPresent(value: String?): Boolean =
        !value.isNullOrBlank() && !value.equals("null", ignoreCase = true)

    private fun parseAssignments(input: String): Map<String, String> {
        val keyMatches = ASSIGNMENT_KEY.findAll(input).toList()
        return buildMap {
            keyMatches.forEachIndexed { index, match ->
                val start = match.range.last + 1
                val end = keyMatches.getOrNull(index + 1)?.range?.first ?: input.length
                put(match.groupValues[1], input.substring(start, end).trim().trimEnd(','))
            }
        }
    }

    private val ASSIGNMENT_KEY = Regex("(?:^|,\\s*)([A-Za-z_][A-Za-z0-9_]*)=")
}

internal object RootSpikeEvidence {
    private const val SAFE_CARRIER_MAX = 64
    private val SAFE_CARRIER = Regex("[\\p{L}\\p{M} .&'_-]{1,$SAFE_CARRIER_MAX}")

    /**
     * Evidence files are named per attempt and per second so a grant, a denial, a revoke and
     * a retry can never be confused with each other, even when the harness is restarted
     * between attempts.
     */
    fun fileName(attempt: Int, epochMillis: Long): String =
        "traffic-light-root-spike-attempt-$attempt-$epochMillis.txt"

    fun format(
        attempt: Int,
        device: SpikeDeviceInfo,
        probes: List<SpikeProbeResult>,
        recordSource: String,
        records: List<SpikeSubscriptionRecord>,
        providerRecordCount: Int,
        isubRecordCount: Int,
        subscriptionIdColumn: String = "not_probed",
        subscriptionIdMatchesRowId: Boolean? = null,
        providerMarkers: String = "",
        isubMarkers: String = "",
    ): String = buildString {
        appendLine("protocolVersion=2")
        appendLine("attempt=$attempt")
        appendLine("startupShellRequest=false")
        appendLine("startupShellCached=${device.startupShellCached}")
        appendLine("authorizationRequestedByExplicitAction=${device.authorizationRequestedByExplicitAction}")
        appendLine("shellCachedBefore=${device.shellCachedBefore}")
        appendLine("suVisibleToApp=${device.suVisibleToApp}")
        appendLine("shellStatus=${device.shellStatus}")
        appendLine("shellStatusInt=${device.shellStatusInt}")
        appendLine("libsuGrantState=${device.libsuGrantState}")
        appendLine("androidRelease=${safeMetadata(device.androidRelease)}")
        appendLine("sdkInt=${device.sdkInt}")
        appendLine("manufacturer=${safeMetadata(device.manufacturer)}")
        appendLine("model=${safeMetadata(device.model)}")
        appendLine("selinux=${device.selinux}")
        probes.forEach { probe ->
            appendLine("probe[${probe.id}].category=${probe.category}")
            appendLine("probe[${probe.id}].exitCode=${probe.exitCode}")
            appendLine("probe[${probe.id}].timedOut=${probe.timedOut}")
            appendLine("probe[${probe.id}].lineCount=${probe.lineCount}")
            appendLine("probe[${probe.id}].stderrLineCount=${probe.stderrLineCount}")
            appendLine("probe[${probe.id}].recordCount=${probe.recordCount}")
        }
        appendLine("providerRecordCount=$providerRecordCount")
        appendLine("isubRecordCount=$isubRecordCount")
        appendLine("recordSource=$recordSource")
        appendLine("recordCount=${records.size}")
        appendLine("subscriptionIdColumn=$subscriptionIdColumn")
        appendLine("subscriptionIdMatchesRowId=${subscriptionIdMatchesRowId ?: "unknown"}")
        appendLine("providerMarkers=$providerMarkers")
        appendLine("isubMarkers=$isubMarkers")
        records.forEachIndexed { index, record ->
            appendLine("record[$index].subscriptionIdPresent=${record.subscriptionId >= 0}")
            appendLine("record[$index].simSlotIndex=${record.simSlotIndex}")
            appendLine("record[$index].slotValid=${record.simSlotIndex >= 0}")
            appendLine("record[$index].carrierPresent=${record.carrierName.isNotBlank()}")
            appendLine("record[$index].carrierValueSafe=${SAFE_CARRIER.matches(record.carrierName)}")
            appendLine("record[$index].subscriberIdPresent=${record.subscriberIdPresent}")
        }
    }

    private fun safeMetadata(value: String): String = value
        .take(80)
        .map { character -> if (character.isLetterOrDigit() || character in " ._-") character else '_' }
        .joinToString("")
}
