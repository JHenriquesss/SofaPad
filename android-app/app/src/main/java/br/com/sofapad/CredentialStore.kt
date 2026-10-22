package br.com.sofapad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

class CredentialStore(context: Context, private val alias: String = "sofapad-paired-pc-v1", fileName: String = "paired-pc.enc") {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): ConnectionSettings? {
        val bytes = try {
            file.openRead().use { input ->
                val buffer = ByteArray(4097)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read <= 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
        } catch (_: java.io.FileNotFoundException) { return null }
        require(bytes.size in 29..4096)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("SofaPad paired PC v1".toByteArray())
        val value = Json.parseToJsonElement(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)).jsonObject
        require(value.getValue("v").jsonPrimitive.int == 1)
        return ConnectionSettings(value.getValue("host").jsonPrimitive.content, value.getValue("port").jsonPrimitive.int,
            value.getValue("fingerprint").jsonPrimitive.content, value.getValue("token").jsonPrimitive.content, true)
    }
    @Synchronized fun save(config: ConnectionSettings) {
        require(config.stableIdentity)
        val clear = buildJsonObject {
            put("v", 1); put("host", config.host); put("port", config.port)
            put("fingerprint", config.fingerprint); put("token", config.token)
        }.toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("SofaPad paired PC v1".toByteArray())
        require(cipher.iv.size == 12)
        val bytes = cipher.iv + cipher.doFinal(clear)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        finally { clear.fill(0) }
    }
    @Synchronized fun forget() {
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
