package com.atlas.connected

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals a batch of contributions on the phone, so that only the holder of the project's private key can read it. Each
 * batch gets a new X25519 key pair. Its shared secret with the project's public key gives an AES-256-GCM key through
 * HKDF-SHA256. Everything here is built into Android, with no new library.
 *
 * The sealed form: "TKC1", the batch's X25519 public key (32 bytes), the nonce (12 bytes), then the gzipped text,
 * encrypted, with its 16-byte tag. The first 36 bytes are authenticated too. tools/contributions/open.ts opens it.
 */
internal object Seal {
    val MAGIC = "TKC1".toByteArray(Charsets.US_ASCII)
    private val INFO = "titankeys contributions v1".toByteArray(Charsets.US_ASCII)
    private val X25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00)
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    const val HEADER_BYTES = 4 + 32 + NONCE_BYTES

    fun seal(plain: ByteArray, recipientSpki: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val recipient = KeyFactory.getInstance("XDH").generatePublic(X509EncodedKeySpec(recipientSpki))
        // XDH makes X25519 keys unless told otherwise, on Android and in the JVM alike.
        val ephemeral = KeyPairGenerator.getInstance("XDH").generateKeyPair()
        val ephemeralRaw = raw(ephemeral.public.encoded)
        val agreement = KeyAgreement.getInstance("XDH")
        agreement.init(ephemeral.private)
        agreement.doPhase(recipient, true)
        val key = key(agreement.generateSecret(), ephemeralRaw, raw(recipientSpki))
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + ephemeralRaw
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + nonce + cipher.doFinal(gzip(plain))
    }

    /** Opens a sealed batch. The app never does this: it is for the tests, which hold a test key. */
    fun open(sealed: ByteArray, recipientPkcs8: ByteArray, recipientSpki: ByteArray): ByteArray {
        require(sealed.size > HEADER_BYTES && sealed.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a sealed batch" }
        val ephemeralRaw = sealed.copyOfRange(4, 36)
        val factory = KeyFactory.getInstance("XDH")
        val ephemeral = factory.generatePublic(X509EncodedKeySpec(X25519_SPKI_PREFIX + ephemeralRaw))
        val private: PrivateKey = factory.generatePrivate(PKCS8EncodedKeySpec(recipientPkcs8))
        val agreement = KeyAgreement.getInstance("XDH")
        agreement.init(private)
        agreement.doPhase(ephemeral, true)
        val key = key(agreement.generateSecret(), ephemeralRaw, raw(recipientSpki))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed.copyOfRange(36, HEADER_BYTES)))
        cipher.updateAAD(sealed.copyOfRange(0, 36))
        return gunzip(cipher.doFinal(sealed.copyOfRange(HEADER_BYTES, sealed.size)))
    }

    /** The 32-byte key at the end of an X.509 SubjectPublicKeyInfo for X25519. */
    fun raw(spki: ByteArray): ByteArray {
        require(spki.size == X25519_SPKI_PREFIX.size + 32 && spki.copyOfRange(0, X25519_SPKI_PREFIX.size).contentEquals(X25519_SPKI_PREFIX)) {
            "Not an X25519 public key"
        }
        return spki.copyOfRange(X25519_SPKI_PREFIX.size, spki.size)
    }

    /** HKDF-SHA256 (RFC 5869) with the two public keys as salt, for one 32-byte key. */
    private fun key(secret: ByteArray, ephemeralRaw: ByteArray, recipientRaw: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(ephemeralRaw + recipientRaw, "HmacSHA256"))
        val prk = extract.doFinal(secret)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        expand.update(INFO)
        expand.update(1)
        return expand.doFinal()
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
