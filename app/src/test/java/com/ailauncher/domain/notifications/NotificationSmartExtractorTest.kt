package com.ailauncher.domain.notifications

import com.ailauncher.domain.model.SmartExtraction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationSmartExtractorTest {

    private fun otp(text: String) = NotificationSmartExtractor.findOtp(text)?.code

    @Test
    fun findsOneTimeCodesNearAKeyword() {
        assertThat(otp("482913 is your verification code. Don't share it.")).isEqualTo("482913")
        assertThat(otp("Your OTP for login is 7731. Valid for 10 minutes.")).isEqualTo("7731")
        assertThat(otp("G-551234 is your Google verification code")).isEqualTo("551234")
        assertThat(otp("Use code 123 456 to sign in")).isEqualTo("123456")
    }

    @Test
    fun ignoresAmountsTimesAndTextWithoutAKeyword() {
        assertThat(otp("Your order of ₹1499 is confirmed")).isNull()
        assertThat(otp("Paid $2500 to Alex")).isNull()
        assertThat(otp("Meeting moved to 1430")).isNull()
        assertThat(otp("Your code: valid until 10:30 today, amount 45.00")).isNull()
        assertThat(otp("Lunch at 1:15?")).isNull()
    }

    @Test
    fun findsMeetingLinks() {
        val found = NotificationSmartExtractor.extract(
            "Standup in 5 minutes",
            "Join https://meet.google.com/abc-defg-hij or dial in. Backup: https://us02web.zoom.us/j/8123456789?pwd=abc.",
        )
        val links = found.filterIsInstance<SmartExtraction.MeetingLink>()
        assertThat(links.map { it.provider }).containsExactly("Google Meet", "Zoom")
        assertThat(links[1].url).isEqualTo("https://us02web.zoom.us/j/8123456789?pwd=abc")
    }

    @Test
    fun findsTrackingNumbersWithCarrierLinks() {
        val ups = NotificationSmartExtractor.findTrackingNumbers("Your package shipped! Tracking 1Z999AA10123456784")
        assertThat(ups.single().carrier).isEqualTo("UPS")
        assertThat(ups.single().trackingUrl).contains("1Z999AA10123456784")
        val fedex = NotificationSmartExtractor.findTrackingNumbers("FedEx: your shipment 123456789012 is out for delivery")
        assertThat(fedex.single().carrier).isEqualTo("FedEx")
        val generic = NotificationSmartExtractor.findTrackingNumbers("Order dispatched. Tracking number: DL8812AB77IN")
        assertThat(generic.single().number).isEqualTo("DL8812AB77IN")
        // Long numbers without any shipping context are not tracking numbers.
        assertThat(NotificationSmartExtractor.findTrackingNumbers("Your balance is 123456789012")).isEmpty()
    }

    @Test
    fun emptyInputGivesNothing() {
        assertThat(NotificationSmartExtractor.extract(null, "  ")).isEmpty()
    }
}
