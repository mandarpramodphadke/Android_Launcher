package com.ailauncher.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * Typo-tolerant ranking for app search and the `launch_app` tool. Scores (higher is better):
 * exact > prefix > word prefix > acronym > substring > subsequence > small edit distance.
 * Works on Unicode letters, so non-Latin app names are matched too.
 */
object FuzzyMatcher {

    const val NO_MATCH = 0

    /** Minimum score for `launch_app` to act without asking the user to pick. */
    const val CONFIDENT_MATCH = 600

    private val diacritics = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^\\p{L}\\p{N} ]+")
    private val spaces = Regex("\\s+")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase(Locale.ROOT)
            .replace(nonWord, " ")
            .replace(spaces, " ")
            .trim()

    /** Scores a normalized [query] against a normalized [candidate]. */
    fun score(query: String, candidate: String): Int {
        if (query.isEmpty() || candidate.isEmpty()) return NO_MATCH
        if (candidate == query) return 1000
        if (candidate.startsWith(query)) return 900 - (candidate.length - query.length).coerceAtMost(100)

        val words = candidate.split(' ')
        if (words.any { it.startsWith(query) }) return 800
        val compactCandidate = candidate.replace(" ", "")
        val compactQuery = query.replace(" ", "")
        if (compactCandidate.startsWith(compactQuery)) return 780

        if (words.size > 1 && words.joinToString("") { it.take(1) }.startsWith(compactQuery)) return 750

        val index = compactCandidate.indexOf(compactQuery)
        if (index >= 0) return 700 - index.coerceAtMost(100)

        subsequenceScore(compactQuery, compactCandidate)?.let { return it }

        if (compactQuery.length >= 4) {
            val prefix = compactCandidate.take(compactQuery.length + 1)
            val distance = levenshtein(compactQuery, prefix).coerceAtMost(levenshtein(compactQuery, compactCandidate))
            val allowed = if (compactQuery.length >= 8) 2 else 1
            if (distance <= allowed) return 450 - distance * 50
        }
        return NO_MATCH
    }

    /** Best score of [query] against several names of the same item (label, aliases, package). */
    fun bestScore(query: String, names: List<String>): Int {
        val q = normalize(query)
        return names.maxOfOrNull { score(q, normalize(it)) } ?: NO_MATCH
    }

    fun <T> rank(query: String, items: List<T>, names: (T) -> List<String>): List<Pair<T, Int>> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return items.asSequence()
            .map { item -> item to (names(item).maxOfOrNull { score(q, normalize(it)) } ?: NO_MATCH) }
            .filter { it.second > NO_MATCH }
            .sortedByDescending { it.second }
            .toList()
    }

    private fun subsequenceScore(query: String, candidate: String): Int? {
        var qi = 0
        var gaps = 0
        var last = -1
        for (ci in candidate.indices) {
            if (qi < query.length && candidate[ci] == query[qi]) {
                if (last >= 0 && ci - last > 1) gaps++
                last = ci
                qi++
            }
        }
        if (qi < query.length || query.length < 2) return null
        return (600 - gaps * 25 - (candidate.length - query.length)).coerceIn(460, 599)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
