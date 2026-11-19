package com.leekleak.trafficlight.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSpikeProtocolTest {

    @Test
    fun `allowlist is exactly the constant read-only commands`() {
        assertEquals(
            listOf(
                "id -u",
                "getenforce",
                "content query --uri content://telephony/siminfo --projection _id:sim_id:carrier_name:imsi",
                "dumpsys isub",
            ),
            RootSpikeProtocol.ALLOWLIST,
        )
    }

    @Test
    fun `allowlist and failure injection contain no user controlled interpolation`() {
        val commands = RootSpikeProtocol.ALLOWLIST + RootSpikeProtocol.FAILURE_INJECTION
        assertTrue(commands.none { it.contains("\$") || it.contains("%s") || it.contains(";") })
    }

    @Test
    fun `allowlist and failure injection do not overlap`() {
        assertTrue(RootSpikeProtocol.ALLOWLIST.intersect(RootSpikeProtocol.FAILURE_INJECTION.toSet()).isEmpty())
    }

    @Test
    fun `non allowlisted command is rejected`() {
        assertTrue(rejected("rm -rf /data"))
    }

    @Test
    fun `failure injection commands are accepted`() {
        RootSpikeProtocol.FAILURE_INJECTION.forEach { RootSpikeProtocol.requireAllowlisted(it) }
    }

    @Test
    fun `uid probe only accepts zero`() {
        assertTrue(RootSpikeProtocol.parseUid(listOf("0")))
        assertFalse(RootSpikeProtocol.parseUid(listOf("2000")))
        assertFalse(RootSpikeProtocol.parseUid(listOf("uid=0(root) gid=0(root)")))
        assertFalse(RootSpikeProtocol.parseUid(emptyList()))
    }

    @Test
    fun `selinux probe is restricted to known values`() {
        assertEquals("Enforcing", RootSpikeProtocol.parseSelinux(listOf("Enforcing")))
        assertEquals("Permissive", RootSpikeProtocol.parseSelinux(listOf("Permissive")))
        assertEquals("unknown", RootSpikeProtocol.parseSelinux(listOf("something else")))
    }

    @Test
    fun `content query parser extracts id slot carrier and subscriber presence`() {
        val records = RootSpikeProtocol.parseContentQuery(
            listOf(
                "Row: 0 _id=1, sim_id=0, carrier_name=Telkomsel, imsi=510011234567890",
                "Row: 1 _id=2, sim_id=1, carrier_name=XL Axiata, imsi=null",
            ),
        )

        assertEquals(2, records.size)
        assertEquals(1, records[0].subscriptionId)
        assertEquals(0, records[0].simSlotIndex)
        assertEquals("Telkomsel", records[0].carrierName)
        assertTrue(records[0].subscriberIdPresent)
        assertEquals(1, records[1].simSlotIndex)
        assertFalse(records[1].subscriberIdPresent)
    }

    @Test
    fun `content query parser ignores rows without identifiers`() {
        val rows = listOf(
            "Row: 0 sim_id=0, carrier_name=Telkomsel",
            "no row here",
            "Row: 1 _id=2, carrier_name=XL",
        )

        assertEquals(0, RootSpikeProtocol.parseContentQuery(rows).size)
    }

    @Test
    fun `isub dump parser extracts subscription id slot and carrier`() {
        val records = RootSpikeProtocol.parseIsubDump(
            listOf(
                "Active subscriptions:",
                "  SubscriptionInfoInternal{id=1, simSlotIndex=0, carrierName=Telkomsel, carrierId=1}",
                "  SubscriptionInfoInternal{id=2, simSlotIndex=1, carrierName=XL Axiata, carrierId=2}",
            ),
        )

        assertEquals(2, records.size)
        assertEquals(1, records[0].subscriptionId)
        assertEquals(0, records[0].simSlotIndex)
        assertEquals("Telkomsel", records[0].carrierName)
        assertEquals(2, records[1].subscriptionId)
        assertEquals(1, records[1].simSlotIndex)
    }

    @Test
    fun `isub dump parser flags subscriber id presence`() {
        val records = RootSpikeProtocol.parseIsubDump(
            listOf(
                "  SubscriptionInfoInternal{id=1, simSlotIndex=0, carrierName=Telkomsel, imsi=510011234567890}",
                "  SubscriptionInfoInternal{id=2, simSlotIndex=1, carrierName=XL, imsi=null}",
            ),
        )

        assertTrue(records[0].subscriberIdPresent)
        assertFalse(records[1].subscriberIdPresent)
    }

    @Test
    fun `isub dump parser ignores lines without subscription info`() {
        val dump = listOf(
            "SubscriptionManagerService:",
            "  mSlotIndexToSubId: {0=1}",
            "  something else entirely",
        )

        assertEquals(0, RootSpikeProtocol.parseIsubDump(dump).size)
    }

    @Test
    fun `parser caps record count`() {
        val dump = (0 until RootSpikeProtocol.MAX_RECORDS + 10).map { index ->
            "  SubscriptionInfoInternal{id=${index + 1}, simSlotIndex=0, carrierName=Carrier$index}"
        }

        assertEquals(RootSpikeProtocol.MAX_RECORDS, RootSpikeProtocol.parseIsubDump(dump).size)
    }

    @Test
    fun `parser skips over long lines`() {
        val overlong = "  SubscriptionInfoInternal{id=1, simSlotIndex=0, carrierName=" +
            "x".repeat(RootSpikeProtocol.MAX_LINE_LENGTH) + "}"
        val dump = listOf(overlong, "  SubscriptionInfoInternal{id=2, simSlotIndex=1, carrierName=XL}")

        val records = RootSpikeProtocol.parseIsubDump(dump)

        assertEquals(1, records.size)
        assertEquals(2, records[0].subscriptionId)
    }

    @Test
    fun `parser stops after max lines`() {
        val dump = (0 until RootSpikeProtocol.MAX_LINES + 5).map {
            "  SubscriptionInfoInternal{id=1, simSlotIndex=0, carrierName=Telkomsel}"
        }

        assertEquals(1, RootSpikeProtocol.parseIsubDump(dump).size)
    }

    @Test
    fun `provider records win over isub records`() {
        val provider = listOf(record(1, 0, "Telkomsel"))
        val isub = listOf(record(9, 1, "Other"))

        val (source, records) = RootSpikeProtocol.selectRecords(provider, isub)

        assertEquals("provider", source)
        assertEquals(provider, records)
    }

    @Test
    fun `isub records are used when provider is empty`() {
        val isub = listOf(record(9, 1, "Other"))

        val (source, records) = RootSpikeProtocol.selectRecords(emptyList(), isub)

        assertEquals("isub", source)
        assertEquals(isub, records)
    }

    @Test
    fun `no source is reported when both paths are empty`() {
        val (source, records) = RootSpikeProtocol.selectRecords(emptyList(), emptyList())

        assertEquals("none", source)
        assertTrue(records.isEmpty())
    }

    @Test
    fun `evidence never contains subscriber id value`() {
        val records = RootSpikeProtocol.parseContentQuery(
            listOf("Row: 0 _id=1, sim_id=0, carrier_name=Telkomsel, imsi=510011234567890"),
        )

        val evidence = evidence(records = records)

        assertFalse(evidence.contains("510011234567890"))
        assertTrue(evidence.contains("record[0].subscriberIdPresent=true"))
    }

    @Test
    fun `evidence reports protocol version and record count`() {
        val records = RootSpikeProtocol.parseIsubDump(
            listOf(
                "  SubscriptionInfoInternal{id=1, simSlotIndex=0, carrierName=Telkomsel}",
                "  SubscriptionInfoInternal{id=2, simSlotIndex=1, carrierName=XL}",
            ),
        )

        val evidence = evidence(records = records)

        assertTrue(evidence.contains("protocolVersion=1"))
        assertTrue(evidence.contains("recordCount=2"))
        assertTrue(evidence.contains("record[1].simSlotIndex=1"))
    }

    @Test
    fun `evidence never contains raw command output`() {
        val evidence = evidence()

        assertFalse(evidence.contains("dumpsys isub"))
        assertFalse(evidence.contains("content query"))
        assertFalse(evidence.contains("telephony/siminfo"))
        assertFalse(evidence.contains("sleep 5"))
        assertTrue(evidence.contains("probe[isub]"))
    }

    @Test
    fun `evidence states that startup never requested a shell`() {
        assertTrue(evidence().contains("startupShellRequest=false"))
    }

    @Test
    fun `evidence records attempt number so revoke and retry stay distinguishable`() {
        val evidence = evidence(attempt = 3)

        assertTrue(evidence.contains("attempt=3"))
    }

    @Test
    fun `evidence records shell cached state before the explicit action`() {
        assertTrue(evidence().contains("shellCachedBefore=false"))
    }

    @Test
    fun `evidence records su visibility as a diagnostic only`() {
        assertTrue(evidence().contains("suVisibleToApp=false"))
    }

    @Test
    fun `carrier name with suspicious characters is not marked safe`() {
        val records = RootSpikeProtocol.parseContentQuery(
            listOf("Row: 0 _id=1, sim_id=0, carrier_name=510011234567890/../x"),
        )

        val evidence = evidence(records = records)

        assertFalse(evidence.contains("510011234567890"))
        assertTrue(evidence.contains("record[0].carrierValueSafe=false"))
    }

    @Test
    fun `carrier name with diacritics is marked safe`() {
        val records = RootSpikeProtocol.parseContentQuery(
            listOf("Row: 0 _id=1, sim_id=0, carrier_name=Telkomsel ÄÖÜ"),
        )

        assertTrue(evidence(records = records).contains("record[0].carrierValueSafe=true"))
    }

    private fun rejected(command: String): Boolean = try {
        RootSpikeProtocol.requireAllowlisted(command)
        false
    } catch (_: IllegalArgumentException) {
        true
    }

    private fun record(id: Int, slot: Int, carrier: String) = SpikeSubscriptionRecord(
        subscriptionId = id,
        simSlotIndex = slot,
        carrierName = carrier,
        subscriberIdPresent = false,
    )

    private fun evidence(
        attempt: Int = 1,
        records: List<SpikeSubscriptionRecord> = emptyList(),
    ) = RootSpikeEvidence.format(
        attempt = attempt,
        device = testDevice(),
        probes = listOf(testProbe()),
        recordSource = if (records.isEmpty()) "none" else "provider",
        records = records,
        providerRecordCount = records.size,
        isubRecordCount = 0,
    )

    private fun testDevice() = SpikeDeviceInfo(
        androidRelease = "16",
        sdkInt = 36,
        manufacturer = "Google",
        model = "Pixel",
        selinux = "Enforcing",
        libsuGrantState = "granted",
        shellStatus = "root_uid_0",
        shellStatusInt = 2,
        authorizationRequestedByExplicitAction = true,
        shellCachedBefore = false,
        suVisibleToApp = false,
    )

    private fun testProbe() = SpikeProbeResult(
        id = "isub",
        exitCode = 0,
        timedOut = false,
        lineCount = 2,
        stderrLineCount = 0,
        recordCount = 2,
        category = "success",
    )
}
