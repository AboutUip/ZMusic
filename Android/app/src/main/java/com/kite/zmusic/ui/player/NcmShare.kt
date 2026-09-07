package com.kite.zmusic.ui.player

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.kite.zmusic.data.TrackRow

internal enum class NcmShareTarget {
    WeChatMoments,
    WeChatFriend,
    Qzone,
    QqFriend,
    CopyLink,
}

internal object NcmShare {
    private const val TAG = "ZMusicShare"
    private const val PKG_WECHAT = "com.tencent.mm"
    private const val PKG_QQ = "com.tencent.mobileqq"
    private const val PKG_QZONE = "com.qzone"

    fun songPageUrl(songId: Long): String? {
        if (songId <= 0L) return null
        return "https://music.163.com/song?id=$songId"
    }

    fun copyLink(context: Context, track: TrackRow): Boolean {
        val url = songPageUrl(track.id) ?: return false
        return copyUrl(context, url)
    }

    fun send(context: Context, track: TrackRow, target: NcmShareTarget): NcmShareResult {
        if (target != NcmShareTarget.CopyLink) return NcmShareResult.Failed
        return if (copyLink(context, track)) {
            NcmShareResult.Copied
        } else {
            NcmShareResult.NoLink
        }
    }

    fun sendImage(
        context: Context,
        imageUri: Uri,
        target: NcmShareTarget,
    ): NcmShareResult {
        if (target == NcmShareTarget.CopyLink) return NcmShareResult.Failed
        val launched = when (target) {
            NcmShareTarget.WeChatFriend -> launchImage(
                context,
                imageUri,
                PKG_WECHAT,
                "com.tencent.mm.ui.tools.ShareImgUI",
            )
            NcmShareTarget.WeChatMoments -> launchImage(
                context,
                imageUri,
                PKG_WECHAT,
                "com.tencent.mm.ui.tools.ShareToTimeLineUI",
            )
            NcmShareTarget.QqFriend -> launchImage(
                context,
                imageUri,
                PKG_QQ,
                "com.tencent.mobileqq.activity.JumpActivity",
                "com.tencent.mobileqq.activity.qfileJumpActivity",
            )
            NcmShareTarget.Qzone -> launchImage(
                context,
                imageUri,
                PKG_QZONE,
                "com.qzonex.module.operation.ui.QZonePublishMoodActivity",
            ) || launchImage(
                context,
                imageUri,
                PKG_QQ,
                "cooperation.qzone.QzoneShareActivity",
                "cooperation.qzone.QzoneJumpActivity",
            )
            NcmShareTarget.CopyLink -> false
        }
        Log.i(TAG, "sendImage target=$target launched=$launched")
        if (launched) return NcmShareResult.Opened
        return when (target) {
            NcmShareTarget.WeChatFriend, NcmShareTarget.WeChatMoments ->
                if (!isInstalled(context, PKG_WECHAT)) {
                    NcmShareResult.MissingApp("微信")
                } else {
                    NcmShareResult.Failed
                }
            NcmShareTarget.QqFriend ->
                if (!isInstalled(context, PKG_QQ)) {
                    NcmShareResult.MissingApp("QQ")
                } else {
                    NcmShareResult.Failed
                }
            NcmShareTarget.Qzone ->
                if (!isInstalled(context, PKG_QZONE) && !isInstalled(context, PKG_QQ)) {
                    NcmShareResult.MissingApp("QQ")
                } else {
                    NcmShareResult.Failed
                }
            NcmShareTarget.CopyLink -> NcmShareResult.Failed
        }
    }

    private fun launchImage(
        context: Context,
        imageUri: Uri,
        packageName: String,
        vararg classes: String,
    ): Boolean {
        context.grantUriPermission(
            packageName,
            imageUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        for (cls in classes) {
            val intent = imageIntent(imageUri).apply {
                component = ComponentName(packageName, cls)
            }
            if (start(context, intent)) return true
        }
        val packaged = imageIntent(imageUri).apply {
            setPackage(packageName)
        }
        return start(context, packaged)
    }

    private fun imageIntent(imageUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newRawUri("share", imageUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            removeExtra(Intent.EXTRA_TEXT)
            removeExtra(Intent.EXTRA_TITLE)
            removeExtra(Intent.EXTRA_SUBJECT)
        }
    }

    private fun copyUrl(context: Context, url: String): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("网易云链接", url))
        return true
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}

internal sealed class NcmShareResult {
    data object Opened : NcmShareResult()
    data object Copied : NcmShareResult()
    data object NoLink : NcmShareResult()
    data object Failed : NcmShareResult()
    data object CopiedFailed : NcmShareResult()
    data object MomentsPaste : NcmShareResult()
    data class MissingApp(val appName: String) : NcmShareResult()
    data class CopiedMissing(val appName: String) : NcmShareResult()
}
