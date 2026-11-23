package com.ailauncher.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class BackupCryptoTest {

    // Fewer iterations keep the unit test fast; production uses DEFAULT_ITERATIONS (600k).
    private val iterations = 1_000

    @Test
    fun roundTrip() {
        val plaintext = "{\"dock\":[\"com.android.dialer\"],\"emoji\":\"🚀\"}".toByteArray()
        val envelope = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        assertThat(envelope.format).isEqualTo(BackupCrypto.FORMAT)
        assertThat(envelope.ciphertext).doesNotContain("dock")
        assertThat(BackupCrypto.decrypt(envelope, "correct horse".toCharArray())).isEqualTo(plaintext)
    }

    @Test
    fun saltAndIvAreRandomPerBackup() {
        val a = BackupCrypto.encrypt("same".toByteArray(), "passphrase".toCharArray(), iterations)
        val b = BackupCrypto.encrypt("same".toByteArray(), "passphrase".toCharArray(), iterations)
        assertThat(a.salt).isNotEqualTo(b.salt)
        assertThat(a.iv).isNotEqualTo(b.iv)
        assertThat(a.ciphertext).isNotEqualTo(b.ciphertext)
    }

    @Test
    fun wrongPassphraseIsDetected() {
        val envelope = BackupCrypto.encrypt("secret".toByteArray(), "right one".toCharArray(), iterations)
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(envelope, "wrong one".toCharArray())
        }
    }

    @Test
    fun tamperingIsDetected() {
        val envelope = BackupCrypto.encrypt("secret payload".toByteArray(), "passphrase".toCharArray(), iterations)
        val bytes = Base64.getDecoder().decode(envelope.ciphertext)
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        val tampered = envelope.copy(ciphertext = Base64.getEncoder().encodeToString(bytes))
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(tampered, "passphrase".toCharArray())
        }
    }

    @Test
    fun foreignFilesAreRejected() {
        val envelope = BackupCrypto.encrypt("x".toByteArray(), "passphrase".toCharArray(), iterations)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(envelope.copy(format = "something-else"), "passphrase".toCharArray())
        }
    }
}
