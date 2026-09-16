package com.kite.zmusic.ui.library

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.CoverPlaceholderVinyl
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainChromePlate
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.notice.showIslandNotice
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileEditScreen(
    contentBottomInset: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val home by app.libraryHomeRepository.snapshot.collectAsStateWithLifecycle()
    val profile = home.profile
    if (profile == null || home.isGuest) {
        Column(
            Modifier
                .fillMaxSize()
                .chromePage()
                .statusBarsPadding(),
        ) {
            ProfileEditTopBar(onBack = onBack, saving = false, onSave = {})
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("登录后可编辑资料", color = MainPalette.Secondary, fontSize = 14.sp)
            }
        }
        return
    }

    var nickname by remember(profile.userId) { mutableStateOf(profile.nickname) }
    var signature by remember(profile.userId) { mutableStateOf(profile.signature.orEmpty()) }
    var gender by remember(profile.userId) { mutableIntStateOf(profile.gender.coerceIn(0, 2)) }
    var birthdayMs by remember(profile.userId) { mutableLongStateOf(profile.birthdayMs) }
    var pendingAvatar by remember(profile.userId) { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { copyPickedAvatar(context, uri) }
            if (file == null) {
                context.showIslandNotice("无法读取这张图片")
            } else {
                pendingAvatar = file
            }
        }
    }

    fun save() {
        if (saving) return
        val name = nickname.trim()
        if (name.isEmpty()) {
            context.showIslandNotice("请填写昵称")
            return
        }
        saving = true
        scope.launch {
            val repo = app.libraryHomeRepository
            val avatarFile = pendingAvatar
            if (avatarFile != null) {
                val avatarAck = withContext(Dispatchers.IO) { repo.uploadSelfAvatar(avatarFile) }
                if (!avatarAck.ok) {
                    saving = false
                    context.showIslandNotice(avatarAck.message.ifBlank { "头像更新失败" })
                    return@launch
                }
            }
            val nickChanged = name != profile.nickname.trim()
            if (nickChanged) {
                val check = withContext(Dispatchers.IO) { repo.checkNicknameAvailable(name) }
                if (!check.ok) {
                    saving = false
                    context.showIslandNotice(check.message.ifBlank { "昵称不可用" })
                    return@launch
                }
            }
            val fieldsChanged = nickChanged ||
                signature != profile.signature.orEmpty() ||
                gender != profile.gender.coerceIn(0, 2) ||
                birthdayMs != profile.birthdayMs
            if (fieldsChanged) {
                val ack = withContext(Dispatchers.IO) {
                    repo.updateSelfProfile(
                        nickname = name,
                        signature = signature,
                        gender = gender,
                        birthdayMs = birthdayMs,
                    )
                }
                if (!ack.ok) {
                    saving = false
                    context.showIslandNotice(ack.message.ifBlank { "保存失败" })
                    return@launch
                }
            } else if (avatarFile == null) {
                saving = false
                onBack()
                return@launch
            }
            saving = false
            context.showIslandNotice("资料已保存")
            onBack()
        }
    }

    val previewUrl = pendingAvatar?.let { Uri.fromFile(it).toString() } ?: profile.avatarUrl

    Box(
        Modifier
            .fillMaxSize()
            .chromePage(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ProfileEditTopBar(onBack = onBack, saving = saving, onSave = ::save)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = contentBottomInset + 24.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                ProfileEditAvatar(
                    url = previewUrl,
                    enabled = !saving,
                    onClick = {
                        pickAvatar.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                Spacer(Modifier.height(22.dp))
                ProfileEditLabel("昵称")
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = "填写昵称",
                    maxLength = 30,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(18.dp))
                ProfileEditLabel("签名")
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = signature,
                    onValueChange = { signature = it },
                    placeholder = "介绍一下自己",
                    maxLength = 100,
                    singleLine = false,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(18.dp))
                ProfileEditLabel("性别")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "保密", 1 to "男", 2 to "女").forEach { (value, label) ->
                        ProfileEditChip(
                            label = label,
                            selected = gender == value,
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                            onClick = { gender = value },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                ProfileEditLabel("生日")
                Spacer(Modifier.height(8.dp))
                ProfileEditRow(
                    value = formatBirthday(birthdayMs),
                    enabled = !saving,
                    onClick = {
                        openBirthdayPicker(context, birthdayMs) { birthdayMs = it }
                    },
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MainPalette.Accent)
                        .clickable(
                            enabled = !saving,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { save() },
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) "保存中…" else "保存",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (saving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MainPalette.Page.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun ProfileEditTopBar(
    onBack: () -> Unit,
    saving: Boolean,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !saving,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Back,
                contentDescription = "返回",
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "编辑资料",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = "保存",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    enabled = !saving,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSave,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = TextStyle(
                color = if (saving) MainPalette.Hint else MainPalette.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun ProfileEditAvatar(
    url: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNullOrBlank()) {
                CoverPlaceholderVinyl(Modifier.fillMaxSize())
            } else {
                UrlImage(
                    url = url,
                    contentDescription = "头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    maxPx = UrlImageCache.THUMB_MAX_PX,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MainPalette.Ink.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Camera,
                    contentDescription = "更换头像",
                    tint = MainPalette.Page,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "点击更换头像",
            color = MainPalette.Secondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ProfileEditLabel(text: String) {
    Text(
        text = text,
        color = MainPalette.Secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ProfileEditChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(MainPalette.Accent.copy(alpha = 0.16f))
                } else {
                    Modifier.wallpaperItemChrome(shape)
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MainPalette.Accent else MainPalette.Ink,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ProfileEditRow(
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .wallpaperItemChrome(shape, MainPalette.Card)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            color = if (value == "未设置") MainPalette.Hint else MainPalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = ZIcons.ChevronRight,
            contentDescription = null,
            tint = MainPalette.Hint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ProfileMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(width = 44.dp, height = 36.dp)
            .mainChromePlate(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ZIcons.Menu,
            contentDescription = "编辑资料",
            tint = MainPalette.Ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatBirthday(ms: Long): String {
    if (ms <= 0L) return "未设置"
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(ms))
}

private fun openBirthdayPicker(
    context: Context,
    currentMs: Long,
    onPicked: (Long) -> Unit,
) {
    val cal = Calendar.getInstance()
    if (currentMs > 0L) cal.timeInMillis = currentMs
    DatePickerDialog(
        context,
        { _, year, month, day ->
            cal.set(year, month, day, 12, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onPicked(cal.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun copyPickedAvatar(context: Context, uri: Uri): File? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bmp = decodeSampled(bytes, 800) ?: return null
    val out = File(context.cacheDir, "avatar_upload.jpg")
    out.outputStream().use { stream ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, stream)
    }
    if (!bmp.isRecycled) bmp.recycle()
    return out.takeIf { it.isFile && it.length() > 0L }
}

private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (w / sample > maxPx || h / sample > maxPx) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
