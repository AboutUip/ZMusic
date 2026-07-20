package com.kite.zmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.kite.zmusic.MainActivity
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 薄宿主：ExoPlayer + MediaSession 在此；通知 / FGS 完全交给 Media3。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var coordinator: PlaylistCoordinator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        val app = application as ZMusicApplication
        val bridge = app.playbackBridge

        val coord = PlaylistCoordinator(
            context = applicationContext,
            sessionRepository = bridge.sessionRepository(),
            stateStore = bridge.stateStore(),
            lyricRepository = bridge.lyricRepository(),
            likedPlaylistRepository = app.likedPlaylistRepository,
            onClearAndStopService = {
                pauseAllPlayersAndStopSelf()
            },
        )
        coordinator = coord
        bridge.attachCoordinator(coord)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, coord.player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(ArtworkBitmapLoader(applicationContext, serviceScope))
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_small)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Media3 默认：播放中保持；否则停止。与官方一致。
        if (!isPlaybackOngoing()) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        val app = application as ZMusicApplication
        val coord = coordinator
        if (coord != null) {
            app.playbackBridge.detachCoordinator(coord)
            coord.release()
            coordinator = null
        }
        mediaSession?.run {
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private class ArtworkBitmapLoader(
        private val context: android.content.Context,
        private val scope: CoroutineScope,
    ) : BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch(Dispatchers.IO) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) future.set(bmp)
                    else future.setException(IllegalStateException("decode failed"))
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                try {
                    val bmp = ArtworkLoader.loadBitmap(context, uri.toString())
                    if (bmp != null) future.set(bmp)
                    else future.setException(IllegalStateException("artwork missing"))
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "zmusic_playback"
    }
}
