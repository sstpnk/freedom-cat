package tech.stpnk.freedomcat.fmt.wireguard

import java.math.BigInteger

private const val IPV4_BITS = 32
private const val IPV6_BITS = 128

private val ZERO = BigInteger.ZERO
private val ONE = BigInteger.ONE

private data class IpPrefix(val bits: Int, val address: BigInteger, val prefixLength: Int) {
    val start: BigInteger
        get() = address.shiftRight(bits - prefixLength).shiftLeft(bits - prefixLength)
    val end: BigInteger
        get() = start.add(ONE.shiftLeft(bits - prefixLength)).subtract(ONE)
}

/**
 * Brings WireGuard addresses to the form sing-box expects.
 *
 * WireGuard `.conf` files may contain a bare address (`Address = 10.8.0.2`),
 * while sing-box decodes `address` and `allowed_ips` as `netip.Prefix` and
 * rejects anything without a prefix length.
 */
fun normalizeIpPrefixes(values: List<String>, keepInvalid: Boolean = false): List<String> {
    val result = ArrayList<String>(values.size)
    for (raw in values) {
        val value = raw.trim()
        if (value.isEmpty()) continue
        val normalized = normalizeIpPrefix(value)
        when {
            normalized != null -> if (normalized !in result) result.add(normalized)
            keepInvalid -> if (value !in result) result.add(value)
        }
    }
    return result
}

/**
 * Returns the prefixes that are not covered by [values], so that traffic
 * outside WireGuard `AllowedIPs` can be routed directly instead of being
 * dropped by the tunnel.
 */
fun ipPrefixComplement(values: List<String>): List<String> {
    val prefixes = values.mapNotNull(::parseIpPrefix)
    val result = ArrayList<String>()
    for (bits in intArrayOf(IPV4_BITS, IPV6_BITS)) {
        val ranges = prefixes.filter { it.bits == bits }
            .map { it.start to it.end }
            .sortedBy { it.first }
        result.addAll(rangeGaps(mergeRanges(ranges, bits), bits))
    }
    return result
}

private fun normalizeIpPrefix(value: String): String? {
    val slash = value.indexOf('/')
    if (slash < 0) {
        val bits = ipAddressBits(value) ?: return null
        return "$value/${if (bits == IPV4_BITS) 32 else 128}"
    }
    if (value.indexOf('/', slash + 1) >= 0) return null
    val addressPart = value.substring(0, slash)
    val lengthPart = value.substring(slash + 1)
    val bits = ipAddressBits(addressPart) ?: return null
    val prefixLength = lengthPart.toIntOrNull() ?: return null
    if (prefixLength !in 0..bits) return null
    return value
}

private fun parseIpPrefix(value: String): IpPrefix? {
    val trimmed = value.trim()
    val slash = trimmed.indexOf('/')
    if (slash <= 0 || slash == trimmed.lastIndex) return null
    if (trimmed.indexOf('/', slash + 1) >= 0) return null
    val parsed = parseIpAddress(trimmed.substring(0, slash)) ?: return null
    val prefixLength = trimmed.substring(slash + 1).toIntOrNull() ?: return null
    if (prefixLength !in 0..parsed.first) return null
    return IpPrefix(parsed.first, parsed.second, prefixLength)
}

private fun ipAddressBits(value: String): Int? = parseIpAddress(value)?.first

private fun parseIpAddress(value: String): Pair<Int, BigInteger>? {
    parseIpv4(value)?.let { return IPV4_BITS to it }
    parseIpv6(value)?.let { return IPV6_BITS to it }
    return null
}

private fun parseIpv4(value: String): BigInteger? {
    if (value.isEmpty()) return null
    val parts = value.split('.')
    if (parts.size != 4) return null
    var result = ZERO
    for (part in parts) {
        if (part.isEmpty() || part.length > 3) return null
        if (!part.all { it in '0'..'9' }) return null
        val octet = part.toInt()
        if (octet > 255) return null
        result = result.shiftLeft(8).or(BigInteger.valueOf(octet.toLong()))
    }
    return result
}

private fun parseIpv6(value: String): BigInteger? {
    if (value.isEmpty() || !value.contains(':')) return null

    val doubleColon = value.indexOf("::")
    if (doubleColon >= 0 && value.indexOf("::", doubleColon + 1) >= 0) return null

    val left: String
    val right: String
    if (doubleColon >= 0) {
        left = value.substring(0, doubleColon)
        right = value.substring(doubleColon + 2)
    } else {
        left = value
        right = ""
    }

    val leftGroups = parseIpv6Groups(left) ?: return null
    val rightGroups = parseIpv6Groups(right) ?: return null
    val groupsCount = leftGroups.size + rightGroups.size

    if (doubleColon >= 0) {
        if (groupsCount > 7) return null
    } else if (groupsCount != 8) {
        return null
    }

    var result = ZERO
    for (group in leftGroups) result = result.shiftLeft(16).or(BigInteger.valueOf(group.toLong()))
    repeat(8 - groupsCount) { result = result.shiftLeft(16) }
    for (group in rightGroups) result = result.shiftLeft(16).or(BigInteger.valueOf(group.toLong()))
    return result
}

private fun parseIpv6Groups(value: String): List<Int>? {
    if (value.isEmpty()) return emptyList()
    val parts = value.split(':')
    val groups = ArrayList<Int>(parts.size)
    for ((index, part) in parts.withIndex()) {
        if (part.isEmpty()) return null
        if (part.contains('.')) {
            if (index != parts.lastIndex) return null
            val ipv4 = parseIpv4(part) ?: return null
            val number = ipv4.toInt()
            groups.add((number ushr 16) and 0xffff)
            groups.add(number and 0xffff)
            continue
        }
        if (part.length > 4) return null
        if (!part.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        groups.add(part.toInt(16))
    }
    return groups
}

private fun mergeRanges(
    ranges: List<Pair<BigInteger, BigInteger>>,
    bits: Int,
): List<Pair<BigInteger, BigInteger>> {
    val max = ONE.shiftLeft(bits).subtract(ONE)
    val merged = ArrayList<Pair<BigInteger, BigInteger>>(ranges.size)
    for ((rawStart, rawEnd) in ranges) {
        val start = rawStart.max(ZERO)
        val end = rawEnd.min(max)
        if (start > end) continue
        val last = merged.lastOrNull()
        if (last != null && start <= last.second.add(ONE)) {
            if (end > last.second) merged[merged.lastIndex] = last.first to end
        } else {
            merged.add(start to end)
        }
    }
    return merged
}

private fun rangeGaps(ranges: List<Pair<BigInteger, BigInteger>>, bits: Int): List<String> {
    val max = ONE.shiftLeft(bits).subtract(ONE)
    val result = ArrayList<String>()
    var cursor = ZERO
    for ((start, end) in ranges) {
        if (start > cursor) result.addAll(rangeToPrefixes(cursor, start.subtract(ONE), bits))
        val next = end.add(ONE)
        if (next > cursor) cursor = next
    }
    if (cursor <= max) result.addAll(rangeToPrefixes(cursor, max, bits))
    return result
}

private fun rangeToPrefixes(start: BigInteger, end: BigInteger, bits: Int): List<String> {
    val result = ArrayList<String>()
    var current = start
    while (current <= end) {
        var size = ONE
        var shift = 0
        while (shift < bits) {
            val nextSize = size.shiftLeft(1)
            if (current.and(nextSize.subtract(ONE)) != ZERO) break
            if (current.add(nextSize).subtract(ONE) > end) break
            size = nextSize
            shift++
        }
        val prefixLength = bits - shift
        val address = if (bits == IPV4_BITS) formatIpv4(current) else formatIpv6(current)
        result.add("$address/$prefixLength")
        current = current.add(size)
    }
    return result
}

private fun formatIpv4(value: BigInteger): String {
    val number = value.toLong()
    return "${(number ushr 24) and 0xff}.${(number ushr 16) and 0xff}." +
            "${(number ushr 8) and 0xff}.${number and 0xff}"
}

private fun formatIpv6(value: BigInteger): String {
    val groupMask = BigInteger.valueOf(0xffff)
    val groups = IntArray(8) { index ->
        value.shiftRight((7 - index) * 16).and(groupMask).toInt()
    }
    var bestStart = -1
    var bestLength = 0
    var runStart = -1
    var runLength = 0
    for (index in groups.indices) {
        if (groups[index] == 0) {
            if (runStart < 0) {
                runStart = index
                runLength = 0
            }
            runLength++
            if (runLength > bestLength) {
                bestLength = runLength
                bestStart = runStart
            }
        } else {
            runStart = -1
            runLength = 0
        }
    }
    if (bestLength < 2) return groups.joinToString(":") { it.toString(16) }
    val left = groups.take(bestStart).joinToString(":") { it.toString(16) }
    val right = groups.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
    return "$left::$right"
}
