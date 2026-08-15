package com.kite.zmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.kite.zmusic.MainActivity
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 薄宿主：ExoPlayer + MediaSession 在此；通知 / FGS 完全交给 Media3。
 * 通知栏在「上一首」左侧额外展示播放模式（列表循环 / 单曲循环 / 随机）。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var coordinator: PlaylistCoordinator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cyclePlaybackModeCommand =
        SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)

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
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(
                ImmutableList.of(playbackModeButton(coord.ui.value.playbackMode)),
            )
            .build()
        bridge.onBindSessionPlayer = { player ->
            mediaSession?.let { session ->
                if (session.player !== player) session.player = player
            }
        }

        val notificationProvider = ModeFirstMediaNotificationProvider(
            context = this,
            channelId = CHANNEL_ID,
            channelNameResourceId = R.string.playback_notification_channel_name,
        )
        notificationProvider.setSmallIcon(R.drawable.ic_notification_small)
        setMediaNotificationProvider(notificationProvider)

        serviceScope.launch {
            combine(
                coord.ui.map { it.playbackMode },
                app.mvPlayback.ui.map { it.active to it.playbackMode },
            ) { songMode, mv ->
                if (mv.first) mv.second else songMode
            }.distinctUntilChanged()
                .collect { mode ->
                    mediaSession?.setMediaButtonPreferences(
                        ImmutableList.of(playbackModeButton(mode)),
                    )
                }
        }
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
        app.playbackBridge.onBindSessionPlayer = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun playbackModeButton(mode: PlaybackMode): CommandButton {
        val (icon, iconRes, nameRes) = when (mode) {
            PlaybackMode.ORDER -> Triple(
                CommandButton.ICON_REPEAT_ALL,
                R.drawable.ic_media_repeat,
                R.string.playback_mode_order,
            )
            PlaybackMode.REPEAT_ONE -> Triple(
                CommandButton.ICON_REPEAT_ONE,
                R.drawable.ic_media_repeat_one,
                R.string.playback_mode_repeat_one,
            )
            PlaybackMode.SHUFFLE -> Triple(
                CommandButton.ICON_SHUFFLE_ON,
                R.drawable.ic_media_shuffle,
                R.string.playback_mode_shuffle,
            )
        }
        return CommandButton.Builder(icon)
            .setDisplayName(getString(nameRes))
            .setCustomIconResId(iconRes)
            .setSessionCommand(cyclePlaybackModeCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(cyclePlaybackModeCommand)
                        .build(),
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CYCLE_PLAYBACK_MODE) {
                val mv = (application as ZMusicApplication).mvPlayback
                if (mv.ui.value.active) mv.cyclePlaybackMode()
                else coordinator?.cyclePlaybackMode()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /**
     * 默认顺序为 prev / play / next / overflow；
     * 将播放模式按钮挪到最前，使通知栏呈现：模式 · 上一首 · 播放 · 下一首。
     */
    private class ModeFirstMediaNotificationProvider(
        context: android.content.Context,
        channelId: String,
        channelNameResourceId: Int,
    ) : DefaultMediaNotificationProvider(
        context,
        { DEFAULT_NOTIFICATION_ID },
        channelId,
        channelNameResourceId,
    ) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            showPauseButton: Boolean,
        ): ImmutableList<CommandButton> {
            val buttons = super.getMediaButtons(
                session,
                playerCommands,
                mediaButtonPreferences,
                showPauseButton,
            )
            val modeIndex = buttons.indexOfFirst {
                it.sessionCommand?.customAction == ACTION_CYCLE_PLAYBACK_MODE
            }
            if (modeIndex <= 0) return buttons
            val builder = ImmutableList.builder<CommandButton>()
            builder.add(buttons[modeIndex])
            for (i in buttons.indices) {
                if (i != modeIndex) builder.add(buttons[i])
            }
            return builder.build()
        }
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
                    else future.set(fallbackArtwork(context))
                } catch (e: Exception) {
                    runCatching { future.set(fallbackArtwork(context)) }
                        .onFailure { future.setException(e) }
                }
            }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                try {
                    val bmp = when (uri.scheme) {
                        "android.resource" -> decodeResourceUri(context, uri)
                            ?: fallbackArtwork(context)
                        else -> ArtworkLoader.loadBitmap(context, uri.toString(), maxEdge = 256)
                            ?: fallbackArtwork(context)
                    }
                    future.set(bmp)
                } catch (e: Exception) {
                    runCatching { future.set(fallbackArtwork(context)) }
                        .onFailure { future.setException(e) }
                }
            }
            return future
        }

        private fun decodeResourceUri(
            context: android.content.Context,
            uri: Uri,
        ): Bitmap? {
            val path = uri.pathSegments
            val id = when {
                path.size >= 2 && path[0] == "drawable" ->
                    context.resources.getIdentifier(path[1], "drawable", context.packageName)
                path.size == 1 -> path[0].toIntOrNull() ?: 0
                else -> uri.lastPathSegment?.toIntOrNull() ?: 0
            }.takeIf { it != 0 } ?: return null
            return android.graphics.BitmapFactory.decodeResource(context.resources, id)
        }

        private fun fallbackArtwork(context: android.content.Context): Bitmap {
            // 绝不回退到 getApplicationIcon：OEM/缓存失败时会变成系统小机器人
            val bmp = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                R.drawable.ic_notification_artwork,
            )
            if (bmp != null) return bmp
            return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.BLACK)
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "zmusic_playback"
        const val ACTION_CYCLE_PLAYBACK_MODE = "com.kite.zmusic.CYCLE_PLAYBACK_MODE"
    }
}
