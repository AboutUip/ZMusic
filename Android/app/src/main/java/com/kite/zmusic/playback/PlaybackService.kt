package com.kite.zmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
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
 * 通知栏：最左播放模式，最右歌词开关（锁定后该槽变为取消锁定）。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var coordinator: PlaylistCoordinator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cyclePlaybackModeCommand =
        SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)
    private val toggleLyricsOverlayCommand =
        SessionCommand(ACTION_TOGGLE_LYRICS_OVERLAY, Bundle.EMPTY)
    private val unlockLyricsOverlayCommand =
        SessionCommand(ACTION_UNLOCK_LYRICS_OVERLAY, Bundle.EMPTY)

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
            audioQualityStore = app.audioQualityStore,
            downloadAccelStore = app.downloadAccelStore,
            downloadAccelIndex = app.downloadAccelIndex,
            realtimeCacheStore = app.realtimeCacheStore,
            realtimeCache = app.realtimeCache,
            persistentPlaybackStore = app.persistentPlaybackStore,
            userClient = app.ncmUserClient,
            audioOutputController = app.audioOutputController,
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
                action = MainActivity.ACTION_OPEN_PLAYER
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, coord.player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(ArtworkBitmapLoader(applicationContext, serviceScope))
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(
                overlayMediaButtons(
                    songMode = coord.ui.value.playbackMode,
                    overlayEnabled = app.lyricOverlayStore.current().enabled,
                    overlayLocked = app.lyricOverlayStore.current().locked,
                ),
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
        // 暂停 / 缓冲未开播时也保留通知（冷启动预热依赖此行为）
        setShowNotificationForIdlePlayer(
            MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS,
        )

        serviceScope.launch {
            combine(
                coord.ui.map { it.playbackMode },
                app.mvPlayback.ui.map { it.active to it.playbackMode },
                app.lyricOverlayStore.prefsFlow.map { it.enabled to it.locked },
            ) { songMode, mv, overlay ->
                val mode = if (mv.first) mv.second else songMode
                Triple(mode, overlay.first, overlay.second)
            }.distinctUntilChanged()
                .collect { (mode, overlayOn, overlayLocked) ->
                    mediaSession?.setMediaButtonPreferences(
                        overlayMediaButtons(mode, overlayOn, overlayLocked),
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

    private fun overlayMediaButtons(
        songMode: PlaybackMode,
        overlayEnabled: Boolean,
        overlayLocked: Boolean,
    ): ImmutableList<CommandButton> {
        val builder = ImmutableList.builder<CommandButton>()
        builder.add(playbackModeButton(songMode))
        if (overlayEnabled && overlayLocked) {
            builder.add(unlockLyricsButton())
        } else {
            builder.add(lyricsOverlayButton(overlayEnabled))
        }
        return builder.build()
    }

    private fun lyricsOverlayButton(enabled: Boolean): CommandButton {
        val iconRes = if (enabled) R.drawable.ic_media_lyrics else R.drawable.ic_media_lyrics_off
        val nameRes = if (enabled) R.string.playback_lyrics_overlay_on else R.string.playback_lyrics_overlay_off
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(nameRes))
            .setCustomIconResId(iconRes)
            .setSessionCommand(toggleLyricsOverlayCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun unlockLyricsButton(): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(R.string.playback_lyrics_overlay_unlock))
            .setCustomIconResId(R.drawable.ic_media_lock_open)
            .setSessionCommand(unlockLyricsOverlayCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun toggleLyricsOverlay() {
        val app = application as ZMusicApplication
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
            return
        }
        val store = app.lyricOverlayStore
        store.setEnabled(!store.current().enabled)
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
                        .add(toggleLyricsOverlayCommand)
                        .add(unlockLyricsOverlayCommand)
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
            if (customCommand.customAction == ACTION_TOGGLE_LYRICS_OVERLAY) {
                toggleLyricsOverlay()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_UNLOCK_LYRICS_OVERLAY) {
                (application as ZMusicApplication).lyricOverlayStore.setLocked(false)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /**
     * 展开通知：模式 · 上一首 · 播放 · 下一首 · 歌词（锁定时该槽位变为取消锁定）。
     * 折叠紧凑行：模式 · 播放 · 歌词/解锁。
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
            val mode = buttons.find { it.sessionCommand?.customAction == ACTION_CYCLE_PLAYBACK_MODE }
            val lyrics = buttons.find { it.sessionCommand?.customAction == ACTION_TOGGLE_LYRICS_OVERLAY }
            val unlock = buttons.find { it.sessionCommand?.customAction == ACTION_UNLOCK_LYRICS_OVERLAY }
            val rest = buttons.filter { btn ->
                val action = btn.sessionCommand?.customAction
                action != ACTION_CYCLE_PLAYBACK_MODE &&
                    action != ACTION_TOGGLE_LYRICS_OVERLAY &&
                    action != ACTION_UNLOCK_LYRICS_OVERLAY
            }
            if (mode == null && lyrics == null && unlock == null) return buttons
            val builder = ImmutableList.builder<CommandButton>()
            if (mode != null) builder.add(mode)
            rest.forEach { builder.add(it) }
            when {
                unlock != null -> builder.add(unlock)
                lyrics != null -> builder.add(lyrics)
            }
            return builder.build()
        }

        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            builder: androidx.core.app.NotificationCompat.Builder,
            actionFactory: MediaNotification.ActionFactory,
        ): IntArray {
            super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
            if (mediaButtons.isEmpty()) return intArrayOf()
            val playIdx = mediaButtons.indices.firstOrNull { i ->
                mediaButtons[i].playerCommand == Player.COMMAND_PLAY_PAUSE
            } ?: (mediaButtons.size / 2)
            val last = mediaButtons.size - 1
            return intArrayOf(0, playIdx, last).distinct().toIntArray()
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
        const val ACTION_TOGGLE_LYRICS_OVERLAY = "com.kite.zmusic.TOGGLE_LYRICS_OVERLAY"
        const val ACTION_UNLOCK_LYRICS_OVERLAY = "com.kite.zmusic.UNLOCK_LYRICS_OVERLAY"
    }
}
