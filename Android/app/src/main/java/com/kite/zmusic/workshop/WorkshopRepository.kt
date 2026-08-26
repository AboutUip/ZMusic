package com.kite.zmusic.workshop

import com.kite.zmusic.plugin.PluginEngine
import com.kite.zmusic.plugin.PluginRecord
import com.kite.zmusic.plugin.PluginRegisterResult
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkshopRepository(
    private val client: WorkshopClient,
    private val downloader: WorkshopDownloader,
    private val pluginEngine: PluginEngine,
    private val auth: WorkshopAuthStore,
    private val notices: IslandNoticeCenter,
    private val cacheDir: File,
) {
    fun hasSession(): Boolean = auth.hasToken()

    fun session() = auth.current()

    suspend fun listPlugins(page: Int, q: String = ""): WorkshopPage<WorkshopPluginCard> =
        client.listPlugins(page = page, q = q)

    suspend fun detail(id: String): WorkshopPluginDetail = client.pluginDetail(id)

    suspend fun rate(id: String, stars: Int): WorkshopRatingResult =
        client.putRating(id, stars)

    suspend fun clearRating(id: String): WorkshopRatingResult =
        client.deleteRating(id)

    fun modules(): List<PluginRecord> = pluginEngine.listModules()

    fun findModule(id: String): PluginRecord? =
        pluginEngine.listModules().find { it.id == id }

    fun modulesRevision() = pluginEngine.modulesRevision

    fun setModuleEnabled(id: String, enabled: Boolean) {
        pluginEngine.setModuleEnabled(id, enabled)
    }

    fun uninstallModule(id: String): Boolean = pluginEngine.uninstallModule(id)

    /**
     * 下载 → 严格验签 → 解压注册（默认不启用）。
     * 进度用 sticky 灵动岛（与 App 更新下载同一套），结束再换成短通知。
     */
    suspend fun downloadAndInstall(detail: WorkshopPluginDetail): Result<PluginRecord> =
        withContext(Dispatchers.IO) {
            val id = detail.card.id
            val dest = File(cacheDir, "workshop-${id.replace('.', '_')}-${detail.card.version}.zpp")
            var finishedOk = false
            try {
                notices.setSticky("正在下载 ${detail.card.name}… 0%")
                val file = downloader.download(id, detail, dest) { received, total ->
                    val pct = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 100) else 0
                    notices.setSticky("正在下载 ${detail.card.name}… $pct%")
                }.getOrElse { err ->
                    handleErr(err)
                    return@withContext Result.failure(err)
                }
                notices.setSticky("正在安装 ${detail.card.name}…")
                val installed = applyInstallResult(pluginEngine.installWorkshopZpp(file))
                finishedOk = installed.isSuccess
                installed
            } finally {
                dest.delete()
                if (!finishedOk) notices.clearSticky()
            }
        }

    /**
     * 模块页：从本机 `.zpp` 注册。新装默认不启用。不能覆盖内置探针。
     */
    suspend fun installFromLocalZpp(zpp: File): Result<PluginRecord> =
        withContext(Dispatchers.IO) {
            var finishedOk = false
            try {
                notices.setSticky("正在安装…")
                val installed = applyInstallResult(pluginEngine.installWorkshopZpp(zpp))
                finishedOk = installed.isSuccess
                installed
            } finally {
                if (!finishedOk) notices.clearSticky()
            }
        }

    private fun applyInstallResult(result: PluginRegisterResult): Result<PluginRecord> =
        when (result) {
            is PluginRegisterResult.Installed -> {
                notices.clearSticky()
                notices.show("已安装「${result.record.name}」，默认未启用")
                Result.success(result.record)
            }
            is PluginRegisterResult.Replaced -> {
                notices.clearSticky()
                notices.show("已更新「${result.record.name}」")
                Result.success(result.record)
            }
            is PluginRegisterResult.Skipped -> {
                notices.clearSticky()
                notices.show(result.reason)
                Result.failure(IllegalStateException(result.reason))
            }
        }

    private fun handleErr(err: Throwable) {
        when (err) {
            is WorkshopApiError.Unauthorized -> notices.show("需要重新确认社区身份")
            is WorkshopApiError.RateLimited -> notices.show("请求太频繁，稍后再试")
            is WorkshopApiError.Missing -> notices.show("插件不存在或已下架")
            else -> notices.show(err.message?.takeIf { it.isNotBlank() } ?: "下载失败")
        }
    }
}
