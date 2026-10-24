package tech.stpnk.freedomcat.fmt.wireguard

import org.junit.Assert.assertEquals
import org.junit.Test

class WireGuardRoutingTest {

    @Test
    fun bareAddressesGetHostPrefix() {
        assertEquals(
            listOf("10.8.0.2/32", "fd00::2/128"),
            normalizeIpPrefixes(listOf("10.8.0.2", "fd00::2")),
        )
    }

    @Test
    fun existingPrefixesAreKeptAsWritten() {
        assertEquals(
            listOf("10.8.0.2/24", "fd00::/8", "0.0.0.0/0"),
            normalizeIpPrefixes(listOf("10.8.0.2/24", "fd00::/8", "0.0.0.0/0")),
        )
    }

    @Test
    fun invalidValuesAreDroppedOrKept() {
        assertEquals(
            emptyList<String>(),
            normalizeIpPrefixes(listOf("garbage", "10.8.0.2/33", "1.2.3")),
        )
        assertEquals(
            listOf("garbage", "10.8.0.2/33"),
            normalizeIpPrefixes(listOf("garbage", "10.8.0.2/33"), keepInvalid = true),
        )
    }

    @Test
    fun fullTunnelHasNoComplement() {
        assertEquals(
            emptyList<String>(),
            ipPrefixComplement(listOf("0.0.0.0/0", "::/0")),
        )
    }

    @Test
    fun halfRangeComplement() {
        assertEquals(
            listOf("128.0.0.0/1", "::/0"),
            ipPrefixComplement(listOf("0.0.0.0/1")),
        )
    }

    @Test
    fun singlePrefixComplementCoversEverythingElse() {
        assertEquals(
            listOf(
                "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4",
                "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/1", "::/0",
            ),
            ipPrefixComplement(listOf("10.0.0.0/8")),
        )
    }

    @Test
    fun overlappingPrefixesAreMergedBeforeComplement() {
        assertEquals(
            listOf("128.0.0.0/1", "::/0"),
            ipPrefixComplement(listOf("0.0.0.0/2", "0.0.0.0/2", "64.0.0.0/2")),
        )
    }

    @Test
    fun vpnStackSplitTunnelKeepsPrivateRangesDirect() {
        assertEquals(
            listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/3", "::/0"),
            ipPrefixComplement(VPN_STACK_SPLIT),
        )
    }

    private val VPN_STACK_SPLIT = listOf(
        "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4",
        "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6",
        "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8",
        "174.0.0.0/7", "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13",
        "192.169.0.0/16", "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12",
        "192.192.0.0/10", "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5",
        "208.0.0.0/4", "8.8.8.8/32", "1.1.1.1/32",
    )
}
