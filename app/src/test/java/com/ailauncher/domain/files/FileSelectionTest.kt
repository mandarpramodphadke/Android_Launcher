package com.ailauncher.domain.files

import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.FileTypeFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileSelectionTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun file(name: String, mime: String?, size: Long, modifiedDaysAgo: Int?, path: String = "DCIM/") = FileEntry(
        uri = "content://tree/$path$name",
        name = name,
        mimeType = mime,
        sizeBytes = size,
        modifiedAtMillis = modifiedDaysAgo?.let { now - it * day },
        addedAtMillis = null,
        relativePath = path,
        isDownload = path.contains("download", ignoreCase = true),
    )

    private val files = listOf(
        file("trip.mp4", "video/mp4", 900, 3),
        file("old-clip.mkv", "application/octet-stream", 700, 400),
        file("photo.jpg", "image/jpeg", 50, 1),
        file("scan.pdf", "application/pdf", 30, 200, "Documents/"),
        file("backup.zip", null, 500, 500, "Documents/"),
        file("song.mp3", "audio/mpeg", 80, 30, "Music/"),
        file("empty.txt", "text/plain", 0, 1, "Documents/"),
        file("installer.apk", "application/vnd.android.package-archive", 120, 2, "Documents/Download/"),
    )

    @Test
    fun typeRulesUseMimeTypeThenExtension() {
        assertThat(FileTypes.matches("video/mp4", "a.mp4", FileTypeFilter.VIDEO)).isTrue()
        assertThat(FileTypes.matches("application/octet-stream", "a.MKV", FileTypeFilter.VIDEO)).isTrue()
        assertThat(FileTypes.matches(null, "notes.docx", FileTypeFilter.DOCUMENT)).isTrue()
        assertThat(FileTypes.matches("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x", FileTypeFilter.DOCUMENT)).isTrue()
        assertThat(FileTypes.matches(null, "backup.tar", FileTypeFilter.ARCHIVE)).isTrue()
        // A real MIME type wins over a misleading extension.
        assertThat(FileTypes.matches("image/png", "fake.mp4", FileTypeFilter.VIDEO)).isFalse()
        assertThat(FileTypes.matches("text/plain", "readme.txt", FileTypeFilter.ALL)).isTrue()
    }

    @Test
    fun largestSkipsEmptyFilesAndHonoursTypeAndLimit() {
        assertThat(FileSelection.largest(files, FileTypeFilter.ALL, 3).map { it.name })
            .containsExactly("trip.mp4", "old-clip.mkv", "backup.zip").inOrder()
        assertThat(FileSelection.largest(files, FileTypeFilter.VIDEO, 10).map { it.name })
            .containsExactly("trip.mp4", "old-clip.mkv").inOrder()
        assertThat(FileSelection.largest(files, FileTypeFilter.DOCUMENT, 10).map { it.name }).containsExactly("scan.pdf")
    }

    @Test
    fun modifiedBetweenIsNewestFirstWithAnExclusiveEnd() {
        val start = now - 7 * day
        assertThat(FileSelection.modifiedBetween(files, start, now, FileTypeFilter.ALL, 10).map { it.name })
            .containsExactly("photo.jpg", "installer.apk", "trip.mp4").inOrder()
        assertThat(FileSelection.modifiedBetween(files, start, now - day, FileTypeFilter.ALL, 10).map { it.name })
            .containsExactly("installer.apk", "trip.mp4").inOrder()
    }

    @Test
    fun staleFilesAreLargestFirstAndSkipRecentlyOpenedOnes() {
        val cutoff = now - 90 * day
        assertThat(FileSelection.notModifiedSince(files, cutoff, FileTypeFilter.ALL, 10).map { it.name })
            .containsExactly("old-clip.mkv", "backup.zip", "scan.pdf").inOrder()
        val opened = setOf("content://tree/Documents/backup.zip")
        assertThat(FileSelection.notModifiedSince(files, cutoff, FileTypeFilter.ALL, 10, opened).map { it.name })
            .containsExactly("old-clip.mkv", "scan.pdf").inOrder()
    }

    @Test
    fun downloadsAreFilesInsideADownloadFolder() {
        assertThat(FileSelection.downloadedBetween(files, now - 30 * day, now, FileTypeFilter.ALL, 10).map { it.name })
            .containsExactly("installer.apk")
        assertThat(FileSelection.downloadedBetween(files, now - 30 * day, now, FileTypeFilter.VIDEO, 10)).isEmpty()
    }
}
