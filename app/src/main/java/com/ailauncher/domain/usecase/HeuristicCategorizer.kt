package com.ailauncher.domain.usecase

import com.ailauncher.domain.model.AppCategory
import java.util.Locale

/**
 * Instant, offline categorization used before (or instead of) the LLM pass: well-known package
 * prefixes first, then keywords in the package name and label. It deliberately returns null when
 * unsure so the model gets to decide those apps.
 */
object HeuristicCategorizer {

    private val knownPackages: List<Pair<String, AppCategory>> = listOf(
        "com.whatsapp" to AppCategory.COMMUNICATION,
        "org.telegram" to AppCategory.COMMUNICATION,
        "org.thoughtcrime.securesms" to AppCategory.COMMUNICATION,
        "com.google.android.apps.messaging" to AppCategory.COMMUNICATION,
        "com.google.android.dialer" to AppCategory.COMMUNICATION,
        "com.android.dialer" to AppCategory.COMMUNICATION,
        "com.samsung.android.dialer" to AppCategory.COMMUNICATION,
        "com.google.android.gm" to AppCategory.PRODUCTIVITY,
        "com.microsoft.office" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs" to AppCategory.PRODUCTIVITY,
        "com.google.android.keep" to AppCategory.PRODUCTIVITY,
        "com.google.android.calendar" to AppCategory.PRODUCTIVITY,
        "com.todoist" to AppCategory.PRODUCTIVITY,
        "com.notion" to AppCategory.PRODUCTIVITY,
        "com.slack" to AppCategory.PRODUCTIVITY,
        "com.instagram" to AppCategory.SOCIAL,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.twitter" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
        "com.snapchat" to AppCategory.SOCIAL,
        "com.reddit" to AppCategory.SOCIAL,
        "com.linkedin" to AppCategory.SOCIAL,
        "com.spotify" to AppCategory.MEDIA,
        "com.google.android.youtube" to AppCategory.MEDIA,
        "com.netflix" to AppCategory.MEDIA,
        "com.amazon.avod" to AppCategory.MEDIA,
        "com.google.android.apps.youtube.music" to AppCategory.MEDIA,
        "com.google.android.apps.photos" to AppCategory.PHOTOGRAPHY,
        "com.google.android.GoogleCamera" to AppCategory.PHOTOGRAPHY,
        "com.niksoftware.snapseed" to AppCategory.PHOTOGRAPHY,
        "com.adobe.lrmobile" to AppCategory.PHOTOGRAPHY,
        "com.google.android.apps.maps" to AppCategory.TRAVEL,
        "com.ubercab" to AppCategory.TRAVEL,
        "com.airbnb" to AppCategory.TRAVEL,
        "com.waze" to AppCategory.TRAVEL,
        "com.google.android.apps.fitness" to AppCategory.HEALTH,
        "com.strava" to AppCategory.HEALTH,
        "com.duolingo" to AppCategory.EDUCATION,
        "com.google.android.apps.magazines" to AppCategory.NEWS,
        "com.amazon.mShop" to AppCategory.SHOPPING,
        "in.amazon.mShop" to AppCategory.SHOPPING,
        "com.paypal" to AppCategory.FINANCE,
        "com.phonepe" to AppCategory.FINANCE,
        "net.one97.paytm" to AppCategory.FINANCE,
        "com.google.android.apps.nbu.paisa" to AppCategory.FINANCE,
        "com.android.chrome" to AppCategory.UTILITIES,
        "org.mozilla.firefox" to AppCategory.UTILITIES,
        "com.android.settings" to AppCategory.UTILITIES,
        "com.google.android.deskclock" to AppCategory.UTILITIES,
        "com.google.android.calculator" to AppCategory.UTILITIES,
        "com.google.android.apps.nbu.files" to AppCategory.UTILITIES,
        "com.android.vending" to AppCategory.UTILITIES,
    )

    private val keywordRules: List<Pair<Regex, AppCategory>> = listOf(
        Regex("\\b(game|games|puzzle|chess|casino|racing|arcade|clash|craft|saga)\\b") to AppCategory.GAMES,
        Regex("\\b(bank|banking|pay|wallet|upi|finance|invest|trading|crypto|money|budget|stocks?)\\b") to AppCategory.FINANCE,
        Regex("\\b(shop|shopping|store|mart|market|deals|food|delivery|grocery|eats)\\b") to AppCategory.SHOPPING,
        Regex("\\b(camera|cam|photo|photos|gallery|editor|selfie|collage)\\b") to AppCategory.PHOTOGRAPHY,
        Regex("\\b(music|radio|podcast|podcasts|video|tv|movies|stream|player|audiobooks?)\\b") to AppCategory.MEDIA,
        Regex("\\b(maps?|navigation|metro|transit|rail|flights?|travel|hotels?|ride|cab|taxi)\\b") to AppCategory.TRAVEL,
        Regex("\\b(fit|fitness|health|workout|run|running|yoga|meditat\\w*|sleep|steps)\\b") to AppCategory.HEALTH,
        Regex("\\b(learn|learning|language|dictionary|course|courses|school|study|classroom)\\b") to AppCategory.EDUCATION,
        Regex("\\b(news|times|post|herald|magazine|weather|reader|kindle|books)\\b") to AppCategory.NEWS,
        Regex("\\b(messenger|messages|chat|sms|dialer|phone|call|contacts)\\b") to AppCategory.COMMUNICATION,
        Regex("\\b(mail|email|notes|docs|office|sheets|slides|calendar|tasks|todo|drive|scanner)\\b") to AppCategory.PRODUCTIVITY,
        Regex("\\b(settings|files|file manager|calculator|clock|browser|vpn|cleaner|security|launcher|keyboard|flashlight|compass)\\b") to AppCategory.UTILITIES,
    )

    fun categorize(packageName: String, label: String): AppCategory? {
        knownPackages.firstOrNull { (prefix, _) -> packageName.startsWith(prefix) }?.let { return it.second }
        val haystack = (label + " " + packageName.replace('.', ' ').replace('_', ' ')).lowercase(Locale.ROOT)
        return keywordRules.firstOrNull { (regex, _) -> regex.containsMatchIn(haystack) }?.second
    }
}
