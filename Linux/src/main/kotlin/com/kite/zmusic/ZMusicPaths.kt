package com.kite.zmusic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ZMusicPaths {
    fun configDir(): Path = resolve("XDG_CONFIG_HOME", ".config")

    fun dataDir(): Path = resolve("XDG_DATA_HOME", ".local/share")

    private fun resolve(env: String, fallback: String): Path {
        val override = System.getProperty("zmusic.home")?.trim().orEmpty()
        if (override.isNotEmpty()) {
            val root = Path.of(override).createDirectories()
            return root.resolve(env.lowercase()).createDirectories()
        }
        val xdg = System.getenv(env)?.trim().orEmpty()
        val base = if (xdg.isNotEmpty()) {
            Path.of(xdg)
        } else {
            Path.of(System.getProperty("user.home"), fallback)
        }
        return base.resolve("zmusic").createDirectories()
    }

    fun restrictToOwner(path: Path) {
        if (!path.exists()) return
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
