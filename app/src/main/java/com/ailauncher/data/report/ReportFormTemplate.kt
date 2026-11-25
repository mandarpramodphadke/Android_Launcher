package com.ailauncher.data.report

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A Google Form "pre-filled link" used as the destination of AI-reply reports, so a report is
 * sent without leaving the app and lands in the developer's own form, with no server to run.
 *
 * Create a form with short/paragraph questions, open ⋮ ▸ "Get pre-filled link", type the
 * placeholders REASON, DETAILS, QUESTION, REPLY and APP into the questions you want filled (REPLY is
 * required, the rest optional), and copy the link into `report.form.url`. The placeholders tell the
 * app which `entry.<id>` field receives which value; reports are POSTed to the form's
 * `formResponse` endpoint.
 */
class ReportFormTemplate private constructor(
    /** `https://docs.google.com/forms/d/e/<id>/formResponse`. */
    val actionUrl: String,
    private val entries: Map<Field, String>,
) {
    enum class Field { REASON, DETAILS, QUESTION, REPLY, APP }

    val fields: Set<Field> get() = entries.keys

    /** `application/x-www-form-urlencoded` body for [values] (missing values are sent empty). */
    fun encode(values: Map<Field, String>): String =
        entries.entries.joinToString("&") { (field, entry) -> "${encode(entry)}=${encode(values[field].orEmpty())}" }

    companion object {
        private const val PREFIX = "https://docs.google.com/forms/"

        fun parse(link: String?): ReportFormTemplate? {
            val url = link?.trim().orEmpty()
            if (!url.startsWith(PREFIX)) return null
            val path = url.substringBefore('?').substringBefore('#')
            if (!path.endsWith("/viewform")) return null
            val entries = mutableMapOf<Field, String>()
            url.substringAfter('?', "").substringBefore('#').split('&').forEach { pair ->
                val name = decode(pair.substringBefore('='))
                val value = decode(pair.substringAfter('=', "")).trim().uppercase()
                val field = Field.entries.firstOrNull { it.name == value }
                if (name.startsWith("entry.") && field != null) entries[field] = name
            }
            if (Field.REPLY !in entries) return null
            return ReportFormTemplate(path.removeSuffix("/viewform") + "/formResponse", entries)
        }

        // The String-charset overloads exist on every Android version (the Charset ones need API 33).
        private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
