package com.fikua.wallet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.Signature

/**
 * Robolectric exercises the Keystore code paths on the JVM (no device needed).
 * StrongBox and real biometric gating can only be verified on hardware, so here
 * we use requireUserAuth = false / preferStrongBox = false and assert the rest:
 * key creation, public-key export, and that a signature verifies.
 */
@RunWith(RobolectricTestRunner::class)
class HolderKeyBindingTest {

    private val alias = "test-holder-key"

    @Test
    fun `ensureKey creates a P-256 key and exposes its public key`() {
        HolderKeyBinding.deleteKey(alias)
        val info = HolderKeyBinding.ensureKey(alias, requireUserAuth = false, preferStrongBox = false)

        assertEquals(alias, info.alias)
        val pub = HolderKeyBinding.publicKey(alias)
        assertNotNull(pub)
        assertEquals("EC", pub.algorithm)
    }

    @Test
    fun `signature produced by holder key verifies against its public key`() {
        HolderKeyBinding.deleteKey(alias)
        HolderKeyBinding.ensureKey(alias, requireUserAuth = false, preferStrongBox = false)

        val challenge = "oid4vp-holder-challenge".toByteArray()
        val der = HolderKeyBinding.sign(challenge, alias)

        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(HolderKeyBinding.publicKey(alias))
            update(challenge)
            verify(der)
        }
        assertTrue("Holder signature must verify against exported public key", verified)
    }

    @Test
    fun `ensureKey is idempotent — same public key across calls`() {
        HolderKeyBinding.deleteKey(alias)
        HolderKeyBinding.ensureKey(alias, requireUserAuth = false, preferStrongBox = false)
        val first = HolderKeyBinding.publicKey(alias).encoded

        HolderKeyBinding.ensureKey(alias, requireUserAuth = false, preferStrongBox = false)
        val second = HolderKeyBinding.publicKey(alias).encoded

        assertTrue("Re-ensuring must not rotate the key", first.contentEquals(second))
    }
}
