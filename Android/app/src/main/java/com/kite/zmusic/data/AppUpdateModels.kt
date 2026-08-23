package com.kite.zmusic.data

import java.io.File

data class LatestRelease(
    val version: String,
    val kind: String,
    val id: String,
)

data class AppUpdateApk(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
)

data class AppUpdateOffer(
    val entry: ChangelogEntry,
    val apk: AppUpdateApk,
) {
    val version: String get() = entry.version
}

sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data class Prompt(val offer: AppUpdateOffer) : AppUpdateUiState()
    data class Downloading(
        val offer: AppUpdateOffer,
        val received: Long,
        val total: Long,
    ) : AppUpdateUiState()
    data class ReadyToInstall(
        val offer: AppUpdateOffer,
        val file: File,
        val needsPermission: Boolean,
    ) : AppUpdateUiState()
}

enum class LatestDecision {
    None,
    FetchExact,
    ScanRange,
}

object AppUpdateParse {
    fun parseLatest(snapshot: Any?): LatestRelease? {
        val root = snapshot as? Map<*, *> ?: return null
        if (root["ok"] == false) return null
        val version = ChangelogRoster.normalizeVersion(catalogString(root["version"]))
        if (version.isEmpty()) return null
        return LatestRelease(
            version = version,
            kind = catalogString(root["kind"]).ifBlank { "Release" },
            id = catalogString(root["id"]),
        )
    }

    fun parseOffer(raw: Any?): AppUpdateOffer? {
        val o = raw as? Map<*, *> ?: return null
        val entry = ChangelogRoster.parseRelease(o, requireItems = false) ?: return null
        val apk = parseApk(o["apk"]) ?: return null
        return AppUpdateOffer(entry, apk)
    }

    fun parseOfferPage(snapshot: Any?): List<AppUpdateOffer> =
        parseCatalogArray(snapshot, "releases", ::parseOffer).entries

    fun parseApk(raw: Any?): AppUpdateApk? {
        val o = raw as? Map<*, *> ?: return null
        val size = catalogLong(o["size_bytes"]) ?: return null
        if (size <= 0L) return null
        val sha = catalogString(o["sha256"]).lowercase()
        if (!SHA256_HEX.matches(sha)) return null
        val url = catalogString(o["url"])
        if (url.isEmpty()) return null
        val name = catalogString(o["name"]).ifBlank { "ZMusic.apk" }
        return AppUpdateApk(name = name, sizeBytes = size, sha256 = sha, url = url)
    }

    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
}
