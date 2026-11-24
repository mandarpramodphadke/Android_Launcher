package com.ailauncher.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Actionable details found in a notification without running the LLM. */
@Serializable
sealed interface SmartExtraction {
    /** Text the home-screen pill shows. */
    val value: String

    @Serializable
    @SerialName("otp")
    data class Otp(val code: String) : SmartExtraction {
        override val value: String get() = code
    }

    @Serializable
    @SerialName("meeting")
    data class MeetingLink(val url: String, val provider: String) : SmartExtraction {
        override val value: String get() = url
    }

    @Serializable
    @SerialName("tracking")
    data class TrackingNumber(val number: String, val carrier: String, val trackingUrl: String? = null) : SmartExtraction {
        override val value: String get() = number
    }
}

/** A notification kept (on the phone only, for at most 24 hours) for the digest and badges. */
data class NotificationDigestItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postedAtMillis: Long,
    /** Still shown in the notification shade (counts towards the app's badge). */
    val isActive: Boolean,
    val extractions: List<SmartExtraction>,
)

/** A home-screen pill built from a notification's smart extraction. */
data class SmartPill(
    val notificationKey: String,
    val appLabel: String,
    val extraction: SmartExtraction,
    val postedAtMillis: Long,
)

enum class ContactActionType(val wireName: String, val verb: String) {
    CALL("call", "Call"),
    SMS("sms", "Text"),
    WHATSAPP("whatsapp", "WhatsApp"),
    ;

    companion object {
        fun fromWire(value: String?): ContactActionType? = when (value?.trim()?.lowercase()) {
            "call", "phone", "dial", "ring", "voice_call" -> CALL
            "sms", "text", "message", "msg", "send_sms", "send_text" -> SMS
            "whatsapp", "wa", "whats_app", "whats app" -> WHATSAPP
            else -> null
        }
    }
}

data class ContactMatch(
    val displayName: String,
    val phoneNumber: String,
    /** "Mobile", "Work", … */
    val phoneLabel: String?,
)

/** What the phone is connected to, used to bias app suggestions (music on headphones, maps in the car). */
enum class AudioRoute { SPEAKER, HEADPHONES, BLUETOOTH_AUDIO, CAR }
