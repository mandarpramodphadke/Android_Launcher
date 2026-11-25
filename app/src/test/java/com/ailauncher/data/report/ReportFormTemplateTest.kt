package com.ailauncher.data.report

import com.ailauncher.data.report.ReportFormTemplate.Field
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReportFormTemplateTest {

    private val link = "https://docs.google.com/forms/d/e/1FAIpQLSdExample/viewform?usp=pp_url" +
        "&entry.111=REASON&entry.222=DETAILS&entry.333=QUESTION&entry.444=REPLY&entry.555=APP"

    @Test
    fun prefilledLinkMapsPlaceholdersToEntries() {
        val form = ReportFormTemplate.parse(link)!!
        assertThat(form.actionUrl).isEqualTo("https://docs.google.com/forms/d/e/1FAIpQLSdExample/formResponse")
        assertThat(form.fields).containsExactlyElementsIn(Field.entries)
    }

    @Test
    fun placeholdersAreCaseInsensitiveAndOtherAnswersAreIgnored() {
        val form = ReportFormTemplate.parse(
            "https://docs.google.com/forms/d/e/abc/viewform?usp=pp_url&entry.9=Hello+there&entry.4=reply&entry.1=%20Reason%20#top",
        )!!
        assertThat(form.fields).containsExactly(Field.REPLY, Field.REASON)
        assertThat(form.encode(mapOf(Field.REASON to "Hateful", Field.REPLY to "x"))).isEqualTo("entry.4=x&entry.1=Hateful")
    }

    @Test
    fun valuesAreFormEncodedInLinkOrderAndMissingOnesSentEmpty() {
        val form = ReportFormTemplate.parse(link)!!
        val body = form.encode(
            mapOf(
                Field.REPLY to "a&b = c",
                Field.REASON to "Wrong or misleading",
                Field.QUESTION to "Café? 100%",
                Field.APP to "AI Launcher 1.3.0 (play) · qwen3",
            ),
        )
        assertThat(body).isEqualTo(
            "entry.111=Wrong+or+misleading&entry.222=&entry.333=Caf%C3%A9%3F+100%25" +
                "&entry.444=a%26b+%3D+c&entry.555=AI+Launcher+1.3.0+%28play%29+%C2%B7+qwen3",
        )
    }

    @Test
    fun rejectsLinksThatCannotReceiveReports() {
        assertThat(ReportFormTemplate.parse(null)).isNull()
        assertThat(ReportFormTemplate.parse("")).isNull()
        // Not Google Forms, not HTTPS, a short link, the editor URL, and no REPLY field.
        assertThat(ReportFormTemplate.parse(link.replace("docs.google.com", "example.com"))).isNull()
        assertThat(ReportFormTemplate.parse(link.replace("https://", "http://"))).isNull()
        assertThat(ReportFormTemplate.parse("https://forms.gle/AbCdEf")).isNull()
        assertThat(ReportFormTemplate.parse(link.replace("/viewform", "/edit"))).isNull()
        assertThat(ReportFormTemplate.parse(link.replace("entry.444=REPLY", "entry.444=ANSWER"))).isNull()
    }
}
