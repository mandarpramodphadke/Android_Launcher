package com.ailauncher.domain.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** An instant answer for what is typed in the command bar: a sum or a unit conversion. */
data class QuickAnswer(
    /** What was understood, e.g. "15% of 2400" or "5 km". */
    val question: String,
    /** The result as shown, e.g. "360" or "3.107 mi". */
    val answer: String,
    /** The bare number, for copying. */
    val copyText: String,
)

/**
 * Sums and unit conversions answered as you type, without the model: "15% of 2400", "(3+4)*2",
 * "2^10", "sqrt 81", "5 km in miles", "72 f to c", "1.5 l in cups", "500 mb to gb".
 */
object QuickAnswers {

    fun answer(query: String): QuickAnswer? {
        val q = query.trim()
        if (q.length < 3 || q.none { it.isDigit() }) return null
        return UnitConverter.convert(q) ?: Calculator.answer(q)
    }

    /** Up to 10 significant digits, no trailing zeros, no exponent for everyday sizes. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        if (value == floor(value) && abs(value) < 1e15) return String.format(Locale.ROOT, "%d", value.toLong())
        val bd = BigDecimal(value).round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (abs(value) >= 1e-6 && abs(value) < 1e15) bd.toPlainString() else bd.toString()
    }
}

/** A small arithmetic parser: + − × ÷ ^ %, parentheses, sqrt, and "X% of Y". */
object Calculator {

    fun answer(query: String): QuickAnswer? {
        val text = normalize(query)
        // Only things that look like a sum: at least one operator between numbers, or sqrt / "% of".
        if (text.replace("sqrt", "").replace("of", "").any { it !in MATH_CHARS }) return null
        val looksLikeMath = Regex("""\d\s*[-+*/^]\s*[\d(.]|\d\s*%\s*of\s*\d|sqrt\s*\(?\s*\d|\)\s*[-+*/^]""").containsMatchIn(text)
        if (!looksLikeMath) return null
        val value = evaluate(text) ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        val result = QuickAnswers.format(value)
        return QuickAnswer(question = query.trim(), answer = result, copyText = result)
    }

    /** The value of [expression], or null if it isn't a valid sum. */
    fun evaluate(expression: String): Double? = runCatching {
        val parser = Parser(normalize(expression))
        val value = parser.expression()
        if (!parser.atEnd()) null else value
    }.getOrNull()

    private const val MATH_CHARS = "0123456789 .+-*/^()%"

    private fun normalize(raw: String): String = raw.lowercase(Locale.ROOT)
        .replace('×', '*').replace('x', '*').replace('÷', '/').replace('−', '-')
        .replace("**", "^")
        .replace(Regex("""(?<=\d),(?=\d{3}\b)"""), "") // 2,400 → 2400
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removeSuffix("=").trim()

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean {
            skip()
            return i >= s.length
        }

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun peek(): Char? {
            skip()
            return s.getOrNull(i)
        }

        private fun take(word: String): Boolean {
            skip()
            if (s.startsWith(word, i)) {
                i += word.length
                return true
            }
            return false
        }

        fun expression(): Double {
            var value = term()
            while (true) {
                value = when (peek()) {
                    '+' -> { i++; value + percentAware(value) }
                    '-' -> { i++; value - percentAware(value) }
                    else -> return value
                }
            }
        }

        /** "200 + 10%" adds 10 % of 200, the way phone calculators do. */
        private fun percentAware(base: Double): Double {
            val start = i
            val operand = term()
            val end = i
            return if (s.substring(start, end).trimEnd().endsWith("%") && !s.substring(start, end).contains("of")) base * operand else operand
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                value = when (peek()) {
                    '*' -> { i++; value * power() }
                    '/' -> {
                        i++
                        val d = power()
                        require(d != 0.0)
                        value / d
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double = when {
            peek() == '-' -> { i++; -unary() }
            peek() == '+' -> { i++; unary() }
            take("sqrt") -> {
                val v = unary()
                require(v >= 0)
                sqrt(v)
            }
            else -> postfix()
        }

        private fun postfix(): Double {
            var value = primary()
            if (peek() == '%') {
                i++
                value /= 100.0
                if (take("of")) value *= unary()
            }
            return value
        }

        private fun primary(): Double {
            val c = peek() ?: error("end")
            if (c == '(') {
                i++
                val v = expression()
                require(peek() == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }
}

/** "5 km in miles", "72f to c", "2.5 kg = lb", "100 mph in km/h", "3 cups to ml", "2 gb in mb". */
object UnitConverter {

    private enum class Kind { LENGTH, MASS, VOLUME, SPEED, DATA, TEMPERATURE, AREA, TIME }

    /** [factor] converts one of this unit to the kind's base unit. */
    private data class Unit(val symbol: String, val kind: Kind, val factor: Double, val names: List<String>)

    private val units: List<Unit> = listOf(
        // Length (base: metre)
        Unit("mm", Kind.LENGTH, 0.001, listOf("mm", "millimeter", "millimeters", "millimetre", "millimetres")),
        Unit("cm", Kind.LENGTH, 0.01, listOf("cm", "centimeter", "centimeters", "centimetre", "centimetres")),
        Unit("m", Kind.LENGTH, 1.0, listOf("m", "meter", "meters", "metre", "metres")),
        Unit("km", Kind.LENGTH, 1000.0, listOf("km", "kms", "kilometer", "kilometers", "kilometre", "kilometres")),
        Unit("in", Kind.LENGTH, 0.0254, listOf("in", "inch", "inches", "\"")),
        Unit("ft", Kind.LENGTH, 0.3048, listOf("ft", "foot", "feet", "'")),
        Unit("yd", Kind.LENGTH, 0.9144, listOf("yd", "yard", "yards")),
        Unit("mi", Kind.LENGTH, 1609.344, listOf("mi", "mile", "miles")),
        // Mass (base: kilogram)
        Unit("mg", Kind.MASS, 1e-6, listOf("mg", "milligram", "milligrams")),
        Unit("g", Kind.MASS, 0.001, listOf("g", "gram", "grams", "gm", "gms")),
        Unit("kg", Kind.MASS, 1.0, listOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms")),
        Unit("t", Kind.MASS, 1000.0, listOf("tonne", "tonnes", "ton", "tons")),
        Unit("oz", Kind.MASS, 0.028349523125, listOf("oz", "ounce", "ounces")),
        Unit("lb", Kind.MASS, 0.45359237, listOf("lb", "lbs", "pound", "pounds")),
        Unit("st", Kind.MASS, 6.35029318, listOf("st", "stone", "stones")),
        // Volume (base: litre)
        Unit("ml", Kind.VOLUME, 0.001, listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres")),
        Unit("l", Kind.VOLUME, 1.0, listOf("l", "liter", "liters", "litre", "litres", "ltr")),
        Unit("tsp", Kind.VOLUME, 0.00492892, listOf("tsp", "teaspoon", "teaspoons")),
        Unit("tbsp", Kind.VOLUME, 0.0147868, listOf("tbsp", "tablespoon", "tablespoons")),
        Unit("cup", Kind.VOLUME, 0.236588, listOf("cup", "cups")),
        Unit("fl oz", Kind.VOLUME, 0.0295735, listOf("fl oz", "floz", "fluid ounce", "fluid ounces")),
        Unit("pt", Kind.VOLUME, 0.473176, listOf("pt", "pint", "pints")),
        Unit("gal", Kind.VOLUME, 3.78541, listOf("gal", "gallon", "gallons")),
        // Speed (base: metres per second)
        Unit("km/h", Kind.SPEED, 1 / 3.6, listOf("km/h", "kmh", "kph", "kmph", "kilometers per hour", "kilometres per hour")),
        Unit("mph", Kind.SPEED, 0.44704, listOf("mph", "miles per hour")),
        Unit("m/s", Kind.SPEED, 1.0, listOf("m/s", "mps", "meters per second", "metres per second")),
        Unit("kn", Kind.SPEED, 0.514444, listOf("knot", "knots", "kn")),
        // Data (base: byte, decimal units as phones show storage)
        Unit("B", Kind.DATA, 1.0, listOf("b", "byte", "bytes")),
        Unit("KB", Kind.DATA, 1e3, listOf("kb", "kilobyte", "kilobytes")),
        Unit("MB", Kind.DATA, 1e6, listOf("mb", "megabyte", "megabytes")),
        Unit("GB", Kind.DATA, 1e9, listOf("gb", "gigabyte", "gigabytes")),
        Unit("TB", Kind.DATA, 1e12, listOf("tb", "terabyte", "terabytes")),
        // Area (base: square metre)
        Unit("m²", Kind.AREA, 1.0, listOf("m2", "m²", "sq m", "sqm", "square meter", "square meters", "square metre", "square metres")),
        Unit("ft²", Kind.AREA, 0.09290304, listOf("ft2", "ft²", "sq ft", "sqft", "square foot", "square feet")),
        Unit("acre", Kind.AREA, 4046.8564224, listOf("acre", "acres")),
        Unit("ha", Kind.AREA, 10000.0, listOf("ha", "hectare", "hectares")),
        Unit("km²", Kind.AREA, 1e6, listOf("km2", "km²", "sq km", "square kilometer", "square kilometers", "square kilometre", "square kilometres")),
        // Time (base: second)
        Unit("s", Kind.TIME, 1.0, listOf("sec", "secs", "second", "seconds")),
        Unit("min", Kind.TIME, 60.0, listOf("min", "mins", "minute", "minutes")),
        Unit("h", Kind.TIME, 3600.0, listOf("h", "hr", "hrs", "hour", "hours")),
        Unit("days", Kind.TIME, 86400.0, listOf("day", "days")),
        Unit("weeks", Kind.TIME, 604800.0, listOf("week", "weeks", "wk", "wks")),
        // Temperature (handled separately)
        Unit("°C", Kind.TEMPERATURE, 1.0, listOf("c", "°c", "celsius", "centigrade", "degc", "deg c", "degrees c", "degrees celsius")),
        Unit("°F", Kind.TEMPERATURE, 1.0, listOf("f", "°f", "fahrenheit", "degf", "deg f", "degrees f", "degrees fahrenheit")),
        Unit("K", Kind.TEMPERATURE, 1.0, listOf("k", "kelvin")),
    )

    private val byName: Map<String, Unit> = buildMap { units.forEach { u -> u.names.forEach { put(it, u) } } }

    private val pattern = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*([a-z°²/'" ]+?)\s+(?:in|to|into|as|=|in to)\s+([a-z°²/'" ]+?)\s*\??\s*$""")

    fun convert(query: String): QuickAnswer? {
        val m = pattern.find(query.lowercase(Locale.ROOT).replace("convert ", "")) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = byName[m.groupValues[2].trim()] ?: return null
        val to = byName[m.groupValues[3].trim()] ?: return null
        if (from.kind != to.kind || from == to) return null
        val value = if (from.kind == Kind.TEMPERATURE) temperature(amount, from.symbol, to.symbol) else amount * from.factor / to.factor
        val shown = QuickAnswers.format(roundForDisplay(value))
        return QuickAnswer(
            question = "${QuickAnswers.format(amount)} ${from.symbol}",
            answer = "$shown ${to.symbol}",
            copyText = shown,
        )
    }

    private fun temperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "°F" -> (value - 32) * 5 / 9
            "K" -> value - 273.15
            else -> value
        }
        return when (to) {
            "°F" -> celsius * 9 / 5 + 32
            "K" -> celsius + 273.15
            else -> celsius
        }
    }

    /** Four significant decimals is plenty for a conversion. */
    private fun roundForDisplay(value: Double): Double {
        if (value == 0.0) return 0.0
        return BigDecimal(value).round(MathContext(6, RoundingMode.HALF_UP)).toDouble()
    }
}

/** A system settings page the command bar can open ("wifi", "battery", "dark mode"). */
data class SettingsShortcut(
    val title: String,
    /** android.provider.Settings action (a plain string, so this stays free of Android). */
    val action: String,
    val keywords: List<String>,
)

object SettingsShortcuts {

    val all: List<SettingsShortcut> = listOf(
        SettingsShortcut("Wi-Fi", "android.settings.WIFI_SETTINGS", listOf("wifi", "wi-fi", "wireless", "internet", "network")),
        SettingsShortcut("Bluetooth", "android.settings.BLUETOOTH_SETTINGS", listOf("bluetooth", "pair", "headphones", "earbuds")),
        SettingsShortcut("Mobile data", "android.settings.DATA_ROAMING_SETTINGS", listOf("mobile data", "data", "roaming", "sim", "cellular")),
        SettingsShortcut("Hotspot & tethering", "android.settings.TETHER_SETTINGS", listOf("hotspot", "tethering", "share internet")),
        SettingsShortcut("Airplane mode", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("airplane", "aeroplane", "flight mode", "flight")),
        SettingsShortcut("Display", "android.settings.DISPLAY_SETTINGS", listOf("display", "brightness", "dark mode", "dark theme", "screen timeout", "font size")),
        SettingsShortcut("Sound & vibration", "android.settings.SOUND_SETTINGS", listOf("sound", "volume", "ringtone", "vibration", "vibrate")),
        SettingsShortcut("Battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("battery", "battery usage", "power", "battery saver")),
        SettingsShortcut("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("storage", "space", "free up", "memory card")),
        SettingsShortcut("Notifications", "android.settings.NOTIFICATION_SETTINGS", listOf("notifications", "notification")),
        SettingsShortcut("Do Not Disturb", "android.settings.ZEN_MODE_SETTINGS", listOf("do not disturb", "dnd", "silent", "quiet")),
        SettingsShortcut("Apps", "android.settings.MANAGE_APPLICATIONS_SETTINGS", listOf("apps", "applications", "app settings", "default apps")),
        SettingsShortcut("Location", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("location", "gps")),
        SettingsShortcut("Security", "android.settings.SECURITY_SETTINGS", listOf("security", "screen lock", "fingerprint", "face unlock", "pin", "password")),
        SettingsShortcut("Privacy", "android.settings.PRIVACY_SETTINGS", listOf("privacy", "permissions", "permission manager")),
        SettingsShortcut("Accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("accessibility", "talkback")),
        SettingsShortcut("Language & input", "android.settings.LOCALE_SETTINGS", listOf("language", "languages", "locale")),
        SettingsShortcut("Keyboard", "android.settings.INPUT_METHOD_SETTINGS", listOf("keyboard", "input")),
        SettingsShortcut("Date & time", "android.settings.DATE_SETTINGS", listOf("date", "time zone", "timezone", "date and time")),
        SettingsShortcut("NFC", "android.settings.NFC_SETTINGS", listOf("nfc", "tap to pay", "contactless")),
        SettingsShortcut("Cast", "android.settings.CAST_SETTINGS", listOf("cast", "screen cast", "mirror")),
        SettingsShortcut("VPN", "android.settings.VPN_SETTINGS", listOf("vpn")),
        SettingsShortcut("Accounts", "android.settings.SYNC_SETTINGS", listOf("accounts", "sync")),
        SettingsShortcut("Developer options", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("developer", "developer options", "usb debugging")),
        SettingsShortcut("About phone", "android.settings.DEVICE_INFO_SETTINGS", listOf("about phone", "about", "android version", "build number")),
        SettingsShortcut("System update", "android.settings.SYSTEM_UPDATE_SETTINGS", listOf("update", "system update", "software update")),
        SettingsShortcut("Wallpaper", "android.intent.action.SET_WALLPAPER", listOf("wallpaper", "background")),
        SettingsShortcut("All settings", "android.settings.SETTINGS", listOf("settings", "phone settings", "system settings")),
    )

    /** Settings pages whose keywords start with what was typed (a word of it), best first. */
    fun search(query: String, limit: Int = 2): List<SettingsShortcut> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 3) return emptyList()
        return all.mapNotNull { s ->
            val score = s.keywords.maxOf { k ->
                when {
                    k == q -> 3
                    k.startsWith(q) -> 2
                    q.length >= 4 && k.split(' ').any { it.startsWith(q) } -> 1
                    q.startsWith("$k ") && q.contains("setting") -> 2
                    else -> 0
                }
            }
            if (score > 0) s to score else null
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}
