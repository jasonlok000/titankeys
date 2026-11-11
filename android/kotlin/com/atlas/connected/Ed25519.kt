package com.atlas.connected

import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Checks Ed25519 signatures, the way the announcement feed is signed. The platform's own verifier (Conscrypt on Android,
 * the JDK in tests) comes first. This file's own plain version of RFC 8032, section 5.1.7, runs only where the platform
 * has no Ed25519 at all, never as a second opinion: a signature that the platform refuses is refused. It only verifies,
 * and never holds a private key.
 */
internal object Ed25519 {
    enum class Verifier { PLATFORM, OWN }

    /** Which verifier checked the last signature, for the debug log. */
    @Volatile
    var lastVerifier: Verifier? = null
        private set

    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val L = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val SQRT_M1 = BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)
    private val TWO = BigInteger.valueOf(2)

    /** A point in extended coordinates: x = X/Z, y = Y/Z, x * y = T/Z. */
    private class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    private val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    private val BASE: Point = run {
        val y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
        val x = recoverX(y, 0)!!
        Point(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    /** The 32-byte key inside an X.509 SubjectPublicKeyInfo for Ed25519, as openssl writes it. */
    fun publicKeyFromSpki(spki: ByteArray): ByteArray {
        require(spki.size == SPKI_PREFIX.size + 32 && spki.copyOfRange(0, SPKI_PREFIX.size).contentEquals(SPKI_PREFIX)) { "Not an Ed25519 public key" }
        return spki.copyOfRange(SPKI_PREFIX.size, spki.size)
    }

    /** Checks with the platform's verifier, or with this file's own one if the platform has no Ed25519. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val platform = try {
            verifyPlatform(publicKey, message, signature)
        } catch (error: NoSuchAlgorithmException) {
            null
        }
        lastVerifier = if (platform == null) Verifier.OWN else Verifier.PLATFORM
        return platform ?: verifyOwn(publicKey, message, signature)
    }

    /** The platform's verifier. Throws [NoSuchAlgorithmException] if it has no Ed25519; any other problem is a refusal. */
    fun verifyPlatform(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val factory = KeyFactory.getInstance("Ed25519")
        val verifier = Signature.getInstance("Ed25519")
        return try {
            verifier.initVerify(factory.generatePublic(X509EncodedKeySpec(SPKI_PREFIX + publicKey)))
            verifier.update(message)
            verifier.verify(signature)
        } catch (error: GeneralSecurityException) {
            false
        } catch (error: RuntimeException) {
            false
        }
    }

    /** This file's own verifier, for a platform without Ed25519. */
    fun verifyOwn(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val a = decompress(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decompress(rBytes) ?: return false
        val s = littleEndian(signature.copyOfRange(32, 64))
        if (s >= L) return false
        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(rBytes)
        digest.update(publicKey)
        digest.update(message)
        val h = littleEndian(digest.digest()).mod(L)
        return equal(multiply(s, BASE), add(r, multiply(h, a)))
    }

    private fun add(p: Point, q: Point): Point {
        val a = p.y.subtract(p.x).multiply(q.y.subtract(q.x)).mod(P)
        val b = p.y.add(p.x).multiply(q.y.add(q.x)).mod(P)
        val c = TWO.multiply(p.t).multiply(q.t).multiply(D).mod(P)
        val d = TWO.multiply(p.z).multiply(q.z).mod(P)
        val e = b.subtract(a)
        val f = d.subtract(c)
        val g = d.add(c)
        val h = b.add(a)
        return Point(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun multiply(scalar: BigInteger, point: Point): Point {
        var result = IDENTITY
        var addend = point
        var s = scalar
        while (s.signum() > 0) {
            if (s.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            s = s.shiftRight(1)
        }
        return result
    }

    private fun equal(p: Point, q: Point): Boolean =
        p.x.multiply(q.z).subtract(q.x.multiply(p.z)).mod(P).signum() == 0
            && p.y.multiply(q.z).subtract(q.y.multiply(p.z)).mod(P).signum() == 0

    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val y2 = y.multiply(y)
        val x2 = y2.subtract(BigInteger.ONE).multiply(D.multiply(y2).add(BigInteger.ONE).modInverse(P)).mod(P)
        if (x2.signum() == 0) return if (sign == 1) null else BigInteger.ZERO
        var x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) x = x.multiply(SQRT_M1).mod(P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) return null
        if ((if (x.testBit(0)) 1 else 0) != sign) x = P.subtract(x)
        return x
    }

    private fun decompress(bytes: ByteArray): Point? {
        val value = littleEndian(bytes)
        val sign = if (value.testBit(255)) 1 else 0
        val y = value.clearBit(255)
        val x = recoverX(y, sign) ?: return null
        return Point(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun littleEndian(bytes: ByteArray): BigInteger = BigInteger(1, bytes.reversedArray())
}
