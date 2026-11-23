package com.ailauncher.data.backup

import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based authenticated encryption for launcher backups:
 * PBKDF2-HMAC-SHA256 (600k iterations, OWASP 2023) -> AES-256-GCM, with the format/version bound
 * as additional authenticated data. The same envelope is used for local files and for Firestore,
 * so cloud copies are end-to-end encrypted: neither Firebase nor the Google UID unlocks them.
 */
object BackupCrypto {
    const val FORMAT = "ailauncher-backup"
    const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    const val DEFAULT_ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val AAD = "$FORMAT:v$VERSION".toByteArray()

    @Serializable
    data class Envelope(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val kdf: String = KDF,
        val iterations: Int,
        val salt: String,
        val iv: String,
        val ciphertext: String,
    )

    class WrongPassphraseException : GeneralSecurityException("Wrong passphrase or corrupted backup.")

    fun encrypt(plaintext: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): Envelope {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(AAD)
        }
        val encoder = Base64.getEncoder()
        return Envelope(
            iterations = iterations,
            salt = encoder.encodeToString(salt),
            iv = encoder.encodeToString(iv),
            ciphertext = encoder.encodeToString(cipher.doFinal(plaintext)),
        )
    }

    fun decrypt(envelope: Envelope, passphrase: CharArray): ByteArray {
        require(envelope.format == FORMAT) { "Not an AI Launcher backup" }
        require(envelope.version <= VERSION) { "Backup was made by a newer version of AI Launcher" }
        require(envelope.kdf == KDF) { "Unsupported key derivation ${envelope.kdf}" }
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(envelope.salt)
        val iv = decoder.decode(envelope.iv)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, envelope.iterations), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(AAD)
        }
        return try {
            cipher.doFinal(decoder.decode(envelope.ciphertext))
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
