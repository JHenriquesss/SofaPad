package br.com.sofapad

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CredentialStoreTest {
    @Test fun keystoreRoundTripAndTamperRejectionWithoutTouchingUserPairing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "sofapad-test-${UUID.randomUUID()}"
        val name = "$alias.enc"
        val store = CredentialStore(context, alias, name)
        val token = "c".repeat(64)
        val config = ConnectionSettings("192.168.1.22", 49375, "a".repeat(64), token, true)
        try {
            assertNull(store.read())
            store.save(config)
            assertEquals(token, store.read()!!.token)
            val file = File(context.noBackupFilesDir, name)
            val bytes = file.readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).contains(token))
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertThrows(Exception::class.java) { store.read() }
            store.forget(); assertNull(store.read())
        } finally {
            store.forget()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
