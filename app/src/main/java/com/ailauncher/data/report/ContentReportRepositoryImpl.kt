package com.ailauncher.data.report

import android.content.Context
import androidx.core.content.edit
import androidx.work.WorkManager
import com.ailauncher.BuildConfig
import com.ailauncher.data.report.ReportFormTemplate.Field
import com.ailauncher.domain.model.ContentReport
import com.ailauncher.domain.model.ReportOutcome
import com.ailauncher.domain.repository.ContentReportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-initiated reports of assistant replies (Google Play's AI-generated content policy). The
 * reply is hidden on this phone right away. With `report.form.url` configured the report is posted
 * in the background to the developer's Google Form (in-app, retried until online); otherwise an
 * email draft to `support.email` is offered. Nothing is sent unless the user taps "Send report".
 */
@Singleton
class ContentReportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
) : ContentReportRepository {

    private val prefs = context.getSharedPreferences("content_reports", Context.MODE_PRIVATE)
    private val form: ReportFormTemplate? = ReportFormTemplate.parse(BuildConfig.REPORT_FORM_URL)

    private val reported = MutableStateFlow(loadReported())
    override val reportedMessageIds: Flow<Set<Long>> = reported.asStateFlow()

    override suspend fun submit(report: ContentReport): ReportOutcome = withContext(Dispatchers.IO) {
        markReported(report.messageId)
        val template = form
        when {
            template != null -> {
                val body = template.encode(
                    mapOf(
                        Field.REASON to report.reason.label,
                        Field.DETAILS to report.details.orEmpty().take(MAX_DETAILS),
                        Field.QUESTION to report.question.orEmpty().take(MAX_QUESTION),
                        Field.REPLY to report.reply.take(MAX_REPLY),
                        Field.APP to report.appInfo,
                    ),
                )
                // The body goes to a file: WorkManager input data is limited to 10 KB.
                val dir = File(context.noBackupFilesDir, "reports").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.form").apply { writeText(body) }
                ReportUploadWorker.enqueue(workManager, template.actionUrl, file)
                ReportOutcome.Queued
            }
            BuildConfig.SUPPORT_EMAIL.isNotBlank() -> ReportOutcome.Email(
                to = BuildConfig.SUPPORT_EMAIL,
                subject = "AI reply report: ${report.reason.label}",
                body = buildString {
                    appendLine("Reason: ${report.reason.label}")
                    report.details?.takeIf { it.isNotBlank() }?.let { appendLine("Details: ${it.take(MAX_DETAILS)}") }
                    appendLine("App: ${report.appInfo}")
                    report.question?.let { appendLine().appendLine("Question:").appendLine(it.take(MAX_QUESTION)) }
                    appendLine().appendLine("Reply:").appendLine(report.reply.take(MAX_REPLY))
                },
            )
            else -> ReportOutcome.SavedOnly
        }
    }

    private fun loadReported(): Set<Long> =
        prefs.getStringSet(KEY_REPORTED, emptySet()).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

    private fun markReported(messageId: Long) {
        val updated = reported.value + messageId
        prefs.edit { putStringSet(KEY_REPORTED, updated.mapTo(HashSet()) { it.toString() }) }
        reported.value = updated
    }

    private companion object {
        const val KEY_REPORTED = "reported_message_ids"
        const val MAX_DETAILS = 1_000
        const val MAX_QUESTION = 1_500
        const val MAX_REPLY = 6_000
    }
}
