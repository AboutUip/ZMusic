package com.kite.zmusic.data

object AppUpdateLogic {
    fun compareVersions(left: String, right: String): Int {
        val a = parts(ChangelogRoster.normalizeVersion(left))
        val b = parts(ChangelogRoster.normalizeVersion(right))
        for (i in 0..2) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(remote: String, local: String): Boolean =
        compareVersions(remote, local) > 0

    fun kindEligible(kind: String, testPlan: Boolean): Boolean {
        if (kind.equals("Test", ignoreCase = true)) return testPlan
        return true
    }

    /** 已忽略该版本后，只再提示比它更高的号。 */
    fun blockedByIgnore(version: String, ignored: String?): Boolean {
        val skip = ignored?.let { ChangelogRoster.normalizeVersion(it) }.orEmpty()
        if (skip.isEmpty()) return false
        return compareVersions(version, skip) <= 0
    }

    fun canPrompt(
        offer: AppUpdateOffer,
        localVersion: String,
        testPlan: Boolean,
        ignored: String?,
    ): Boolean {
        if (!isNewer(offer.version, localVersion)) return false
        if (!kindEligible(offer.entry.kind, testPlan)) return false
        if (blockedByIgnore(offer.version, ignored)) return false
        return offer.apk.sizeBytes > 0L && offer.apk.sha256.isNotEmpty() && offer.apk.url.isNotEmpty()
    }

    fun decideFromLatest(
        localVersion: String,
        testPlan: Boolean,
        ignored: String?,
        latest: LatestRelease?,
    ): LatestDecision {
        if (latest == null) return LatestDecision.None
        if (!isNewer(latest.version, localVersion)) return LatestDecision.None
        if (blockedByIgnore(latest.version, ignored)) return LatestDecision.None
        if (!kindEligible(latest.kind, testPlan)) return LatestDecision.ScanRange
        return LatestDecision.FetchExact
    }

    fun pickFromOffers(
        localVersion: String,
        testPlan: Boolean,
        ignored: String?,
        offers: List<AppUpdateOffer>,
    ): AppUpdateOffer? =
        offers.firstOrNull { canPrompt(it, localVersion, testPlan, ignored) }

    fun pickExact(offers: List<AppUpdateOffer>, version: String): AppUpdateOffer? {
        val want = ChangelogRoster.normalizeVersion(version)
        return offers.firstOrNull {
            ChangelogRoster.normalizeVersion(it.version) == want
        }
    }

    fun progressMessage(version: String, received: Long, total: Long): String {
        if (total > 0L) {
            val pct = ((received * 100L) / total).toInt().coerceIn(0, 100)
            return "正在下载 $version · $pct%"
        }
        return "正在下载 $version"
    }

    fun downloadFailMessage(err: Throwable): String {
        val raw = generateSequence(err) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf { msg -> msg.isNotEmpty() } }
            .firstOrNull()
            .orEmpty()
        return when {
            raw.contains("ApkDownloadForbidden", ignoreCase = true) ->
                "安装包无法从对象存储直接下载"
            raw.contains("sha256", ignoreCase = true) -> "更新包校验失败"
            raw.contains("size mismatch", ignoreCase = true) -> "更新包大小不符"
            else -> "更新下载失败"
        }
    }

    fun dialogTitle(version: String): String =
        "ZMusic新版本v${ChangelogRoster.normalizeVersion(version)}"

    private fun parts(version: String): IntArray {
        val out = intArrayOf(0, 0, 0)
        val bits = version.split('.')
        for (i in 0 until minOf(3, bits.size)) {
            out[i] = bits[i].takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return out
    }
}
