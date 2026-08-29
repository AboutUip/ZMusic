package com.kite.zmusic.data

import com.kite.zmusic.ZMusicPaths
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoredSession(
    val cookie: String,
    val displayLabel: String?,
    val isGuest: Boolean = false,
)

class SessionStore {
    private val _session = MutableStateFlow<StoredSession?>(null)
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    init {
        _session.value = read()
    }

    fun persist(cookie: String, displayLabel: String?, isGuest: Boolean = false) {
        val stored = StoredSession(cookie.trim(), displayLabel, isGuest)
        write(stored)
        _session.value = stored
    }

    fun clear() {
        val file = ZMusicPaths.dataDir().resolve(FILE)
        runCatching { Files.deleteIfExists(file) }
        _session.value = null
    }

    private fun read(): StoredSession? {
        val file = ZMusicPaths.dataDir().resolve(FILE)
        if (!file.exists()) return null
        return runCatching {
            val blob = file.readBytes()
            if (blob.size < 13) return null
            val iv = blob.copyOfRange(0, 12)
            val cipherBytes = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
            val parts = plain.split('\u001e', limit = 3)
            val cookie = parts.getOrNull(0)?.trim().orEmpty()
            if (cookie.isEmpty()) return null
            StoredSession(
                cookie = cookie,
                displayLabel = parts.getOrNull(1)?.takeIf { it.isNotEmpty() },
                isGuest = parts.getOrNull(2) == "1",
            )
        }.getOrNull()
    }

    private fun write(stored: StoredSession) {
        val plain = listOf(
            stored.cookie,
            stored.displayLabel.orEmpty(),
            if (stored.isGuest) "1" else "0",
        ).joinToString("\u001e")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val out = iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val file = ZMusicPaths.dataDir().resolve(FILE)
        file.writeBytes(out)
        ZMusicPaths.restrictToOwner(file)
    }

    private fun key(): SecretKeySpec {
        val keyFile = ZMusicPaths.configDir().resolve(KEY_FILE)
        if (!keyFile.exists()) {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            keyFile.writeBytes(raw)
            ZMusicPaths.restrictToOwner(keyFile)
        }
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }

    companion object {
        private const val FILE = "session.enc"
        private const val KEY_FILE = "master.key"
    }
}
