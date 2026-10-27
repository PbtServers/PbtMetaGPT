package com.pbtservers.pbtmetagpt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM: IV aleatorio por escritura, autenticación y clave no exportable en Android Keystore. */
object KeyCipher {
    private val aad = "PbtMetaGPT/OpenAI/v1".toByteArray(Charsets.UTF_8)
    fun encrypt(key: SecretKey, plain: ByteArray): Pair<ByteArray, ByteArray> = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key); updateAAD(aad); iv to doFinal(plain)
    }
    fun decrypt(key: SecretKey, iv: ByteArray, encrypted: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)); updateAAD(aad); doFinal(encrypted)
    }
}

class KeyVault(context: Context) {
    private val preferences = context.getSharedPreferences("credenciales_cifradas", Context.MODE_PRIVATE)
    private val alias = "PbtMetaGPT.OpenAI.v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey = (store().getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build()); generateKey()
    }
    fun exists() = preferences.contains("ciphertext")
    @Synchronized fun save(value: String) {
        require(value.matches(Regex("sk-[A-Za-z0-9_-]{20,500}"))) { "Introduce una clave API personal válida de OpenAI." }
        val plain = value.toByteArray(Charsets.UTF_8)
        try {
            val (iv, ciphertext) = KeyCipher.encrypt(key(), plain)
            check(preferences.edit().putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit()) { "No se pudo guardar la clave cifrada." }
        } finally { plain.fill(0) }
    }
    @Synchronized fun read(): String {
        check(exists()) { "Configura tu clave API en OpenAI." }
        try {
            val secret = store().getKey(alias, null) as? SecretKey ?: error("Clave de cifrado no disponible")
            val plain = KeyCipher.decrypt(secret, Base64.decode(preferences.getString("iv", ""), Base64.NO_WRAP), Base64.decode(preferences.getString("ciphertext", ""), Base64.NO_WRAP))
            return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
        } catch (_: Exception) { throw IllegalStateException("No se puede descifrar la clave. Elimínala y vuelve a introducirla.") }
    }
    @Synchronized fun delete() {
        check(preferences.edit().clear().commit()) { "No se pudo eliminar la clave cifrada." }
        store().deleteEntry(alias)
    }
}
