package com.ailauncher.domain.notifications

import com.ailauncher.domain.model.SmartExtraction

/**
 * Finds one-time codes, meeting links and parcel tracking numbers in notification text with plain
 * regular expressions: instant, deterministic and fully offline (the LLM is never woken up for it).
 *
 * Precision matters more than recall here, because every hit becomes a tappable pill on the home
 * screen: codes need an OTP-ish keyword nearby, and generic tracking numbers need a shipping word.
 */
object NotificationSmartExtractor {

    fun extract(title: String?, text: String?): List<SmartExtraction> {
        val combined = listOfNotNull(title, text).joinToString("\n").take(MAX_TEXT)
        if (combined.isBlank()) return emptyList()
        return buildList {
            findOtp(combined)?.let(::add)
            addAll(findMeetingLinks(combined))
            addAll(findTrackingNumbers(combined))
        }.distinctBy { it.value }
    }

    // ------------------------------------------------------------------------------------ OTP

    fun findOtp(text: String): SmartExtraction.Otp? {
        val keywords = OTP_CONTEXT.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        val candidates = OTP_CANDIDATE.findAll(text)
            .filterNot { looksLikeAmountOrTime(text, it.range) }
            .toList()
        val best = candidates.minByOrNull { candidate ->
            keywords.minOf { k -> distance(candidate.range, k) }
        } ?: return null
        if (keywords.minOf { distance(best.range, it) } > MAX_KEYWORD_DISTANCE) return null
        val code = best.value.filter(Char::isDigit)
        return if (code.length in 4..8) SmartExtraction.Otp(code) else null
    }

    private fun looksLikeAmountOrTime(text: String, range: IntRange): Boolean {
        val before = text.substring((range.first - 4).coerceAtLeast(0), range.first).lowercase()
        val after = text.substring(range.last + 1, (range.last + 6).coerceAtMost(text.length)).lowercase()
        if (CURRENCY.containsMatchIn(before.takeLast(4))) return true
        if (after.startsWith(":") || after.startsWith("%")) return true
        return UNIT_AFTER.containsMatchIn(after)
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    // -------------------------------------------------------------------------------- meetings

    /** Links in the order they appear in the text. */
    fun findMeetingLinks(text: String): List<SmartExtraction.MeetingLink> = MEETINGS.flatMap { (provider, regex) ->
        regex.findAll(text).map { m ->
            val url = m.value.trimEnd('.', ',', ')', ';', '!', '?').let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }
            m.range.first to SmartExtraction.MeetingLink(url, provider)
        }.toList()
    }.sortedBy { it.first }.map { it.second }.distinctBy { it.url }

    // -------------------------------------------------------------------------------- tracking

    fun findTrackingNumbers(text: String): List<SmartExtraction.TrackingNumber> {
        val shippingContext = TRACKING_CONTEXT.containsMatchIn(text)
        val found = mutableListOf<SmartExtraction.TrackingNumber>()
        fun add(number: String, carrier: String, url: String?) {
            if (found.none { it.number == number }) found += SmartExtraction.TrackingNumber(number, carrier, url)
        }
        UPS.findAll(text).forEach { add(it.value.uppercase(), "UPS", "https://www.ups.com/track?tracknum=${it.value.uppercase()}") }
        AMAZON.findAll(text).forEach { add(it.value.uppercase(), "Amazon", null) }
        if (shippingContext) {
            USPS.findAll(text).forEach { add(it.value.filter(Char::isLetterOrDigit).uppercase(), "USPS", "https://tools.usps.com/go/TrackConfirmAction?tLabels=${it.value.filter(Char::isLetterOrDigit)}") }
            if (FEDEX_WORD.containsMatchIn(text)) {
                FEDEX.findAll(text).forEach { add(it.value, "FedEx", "https://www.fedex.com/fedextrack/?trknbr=${it.value}") }
            }
            if (DHL_WORD.containsMatchIn(text)) {
                DHL.findAll(text).forEach { add(it.value, "DHL", "https://www.dhl.com/global-en/home/tracking/tracking-express.html?submit=1&tracking-id=${it.value}") }
            }
            GENERIC.findAll(text).forEach { m ->
                val number = m.groupValues[1].uppercase()
                if (number.any(Char::isDigit)) add(number, "Courier", null)
            }
        }
        return found
    }

    private const val MAX_TEXT = 4_000
    private const val MAX_KEYWORD_DISTANCE = 80

    private val OTP_CONTEXT = Regex(
        """\b(otp|one[- ]time|passcode|pass code|verification|verify|security code|login code|sign[- ]?in code|""" +
            """auth(?:entication)? code|2fa|two[- ]factor|confirmation code|access code|code|pin)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** 4-8 digits (or "123 456" / "123-456"), not glued to other digits, decimals or slashes. */
    private val OTP_CANDIDATE = Regex("""(?<![\d.,/:+])(\d{3}[- ]\d{3}|\d{4,8})(?![\d.,/]\d|\d)""")
    private val CURRENCY = Regex("""[₹$€£¥]|rs\.?\s?$|inr\s?$|usd\s?$""")
    private val UNIT_AFTER = Regex("""^\s?(rs|inr|usd|eur|am\b|pm\b|mb|gb|kb|min|hr|hour|day|sec|%|km|kg|items?)""")

    private val MEETINGS = listOf(
        "Zoom" to Regex("""(?:https?://)?(?:[\w-]+\.)?zoom\.us/(?:j|my|w|s)/[\w?=&./-]+""", RegexOption.IGNORE_CASE),
        "Google Meet" to Regex("""(?:https?://)?meet\.google\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}(?:\?[\w=&-]+)?""", RegexOption.IGNORE_CASE),
        "Microsoft Teams" to Regex("""https?://teams\.(?:microsoft|live)\.com/[^\s"'<>)]+""", RegexOption.IGNORE_CASE),
        "Webex" to Regex("""https?://[\w-]+\.webex\.com/[^\s"'<>)]+""", RegexOption.IGNORE_CASE),
    )

    private val TRACKING_CONTEXT = Regex(
        """\b(track(?:ing)?|shipment|shipped|dispatched|out for delivery|deliver(?:y|ed)|parcel|package|consignment|awb|courier)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val UPS = Regex("""\b1Z[0-9A-Z]{16}\b""", RegexOption.IGNORE_CASE)
    private val AMAZON = Regex("""\bTBA\d{12}\b""", RegexOption.IGNORE_CASE)
    private val USPS = Regex("""\b(?:9[2-5]\d{20}|9[2-5]\d{2} ?\d{4} ?\d{4} ?\d{4} ?\d{4} ?\d{2}|(?:EA|EC|CP|RA|LZ|RR)\d{9}US)\b""")
    private val FEDEX_WORD = Regex("""\bfed\s?ex\b""", RegexOption.IGNORE_CASE)
    private val FEDEX = Regex("""\b(?:\d{12}|\d{15})\b""")
    private val DHL_WORD = Regex("""\bdhl\b""", RegexOption.IGNORE_CASE)
    private val DHL = Regex("""\b\d{10}\b""")
    private val GENERIC = Regex(
        """(?:tracking\s*(?:number|no\.?|id|#|code)|awb\s*(?:number|no\.?)?|consignment\s*(?:number|no\.?))\s*(?:is|:|#|-)?\s*([A-Z0-9]{8,30})\b""",
        RegexOption.IGNORE_CASE,
    )
}
