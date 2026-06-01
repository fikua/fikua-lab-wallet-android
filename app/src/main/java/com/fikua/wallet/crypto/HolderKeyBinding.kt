package com.fikua.wallet.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey

/**
 * Hardware-backed holder key binding.
 *
 * This is the native upgrade over the PWA's WebAuthn-PRF approach (see prf.ts /
 * crypto.ts in fikua-lab-wallet). There, the proof-of-possession key was derived
 * from a passkey's PRF secret and the comment honestly noted "NOT a certified
 * WSCD". Here the P-256 holder key is generated *inside* the Android Keystore —
 * ideally StrongBox (a dedicated secure element) — and the private key material
 * never enters app memory. Every signature is gated by user authentication
 * (biometric / device credential), which is the device-side analogue of the
 * ARF's "user presence on every WSCD operation".
 *
 * The key is what binds an issued credential (mdoc / SD-JWT VC) to *this* device:
 * the public key goes into the credential at issuance (OID4VCI proof-of-possession),
 * and the private key signs the holder challenge at presentation (OID4VP).
 *
 * Boundary note, same spirit as prf.ts: Keystore/StrongBox is strong hardware
 * binding but is still not a formally certified WSCD. Document that for the lab.
 */
object HolderKeyBinding {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DEFAULT_ALIAS = "fikua-holder-binding-key"

    /** P-256 over SHA-256 — matches the PWA's ECDSA/P-256 holder key. */
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    data class KeyInfo(
        val alias: String,
        val isStrongBoxBacked: Boolean,
        val requiresUserAuth: Boolean,
    )

    /**
     * Ensure a holder binding key exists for [alias], creating it if absent.
     *
     * @param requireUserAuth gate every signature behind biometric / device credential.
     * @param preferStrongBox try the dedicated secure element first, fall back to TEE.
     */
    fun ensureKey(
        alias: String = DEFAULT_ALIAS,
        requireUserAuth: Boolean = true,
        preferStrongBox: Boolean = true,
    ): KeyInfo {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(alias)) {
            generateKey(alias, requireUserAuth, preferStrongBox)
        }
        return inspect(alias)
    }

    /** Public key (for OID4VCI proof-of-possession / credential issuance). */
    fun publicKey(alias: String = DEFAULT_ALIAS): ECPublicKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(alias)
            ?: error("No holder key for alias '$alias' — call ensureKey() first")
        return cert.publicKey as ECPublicKey
    }

    /**
     * Sign [data] with the holder private key (for OID4VP presentation).
     *
     * If the key was created with [requireUserAuth] = true, the caller must have
     * unlocked it via a BiometricPrompt CryptoObject wrapping this same Signature;
     * see UI layer. Returns a DER-encoded ECDSA signature — convert to raw r||s
     * for COSE_Sign1 just like crypto.ts#derToRaw does in the PWA.
     */
    fun sign(data: ByteArray, alias: String = DEFAULT_ALIAS): ByteArray {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(alias, null) as? PrivateKey
            ?: error("No holder private key for alias '$alias'")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    /** Build a Signature initialised for signing, to wrap in a BiometricPrompt.CryptoObject. */
    fun signatureForAuth(alias: String = DEFAULT_ALIAS): Signature {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(alias, null) as PrivateKey
        return Signature.getInstance(SIGNATURE_ALGORITHM).apply { initSign(privateKey) }
    }

    fun deleteKey(alias: String = DEFAULT_ALIAS) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun generateKey(alias: String, requireUserAuth: Boolean, preferStrongBox: Boolean) {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        ).apply {
            setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256)
            if (requireUserAuth) {
                setUserAuthenticationRequired(true)
                // Auth valid for one use (per-signature presence); 0 == "every use".
                setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            }
            if (strongBox) setIsStrongBoxBacked(true)
        }.build()

        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        try {
            gen.initialize(spec(preferStrongBox))
            gen.generateKeyPair()
        } catch (e: Exception) {
            // StrongBox unavailable on this device — retry in the TEE.
            if (!preferStrongBox) throw e
            gen.initialize(spec(strongBox = false))
            gen.generateKeyPair()
        }
    }

    private fun inspect(alias: String): KeyInfo {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(alias, null) as PrivateKey
        val factory = java.security.KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
        val keyInfo = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo::class.java)
        @Suppress("DEPRECATION")
        val strongBox = keyInfo.securityLevel == android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX
        return KeyInfo(
            alias = alias,
            isStrongBoxBacked = runCatching { strongBox }.getOrDefault(false),
            requiresUserAuth = keyInfo.isUserAuthenticationRequired,
        )
    }
}
