package com.example.filemanager.ui.viewers

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.FileType
import com.example.filemanager.utils.FileUtils
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    file: File,
    fileType: FileType,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            text = "${FileItem.formatBytes(file.length())} • ${FileUtils.getMimeType(file.name)}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { FileUtils.shareFile(context, file) }) {
                        Icon(Icons.Default.Share, contentDescription = "Chia sẻ", tint = TextPrimary)
                    }
                    IconButton(onClick = { FileUtils.openWithExternalApp(context, file) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Mở bằng ứng dụng khác", tint = ElectricBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (fileType) {
                FileType.IMAGE -> ImageViewer(file)
                FileType.AUDIO -> AudioPlayerViewer(file)
                else -> GenericMediaViewer(file)
            }
        }
    }
}

@Composable
fun ImageViewer(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + offsetChange else Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .transformable(state = state),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun AudioPlayerViewer(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(file) {
        val player = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                duration = this.duration
            } catch (e: Exception) {
                // error
            }
        }
        mediaPlayer = player

        onDispose {
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition = it.currentPosition
                } else {
                    isPlaying = false
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(CharcoalSurfaceHighlight, RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(54.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(file.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
            Spacer(modifier = Modifier.height(8.dp))

            // Time & Slider
            val curSec = currentPosition / 1000
            val durSec = duration / 1000
            Text(
                text = "%02d:%02d / %02d:%02d".format(curSec / 60, curSec % 60, durSec / 60, durSec % 60),
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { frac ->
                    val pos = (frac * duration).toInt()
                    mediaPlayer?.seekTo(pos)
                    currentPosition = pos
                },
                colors = SliderDefaults.colors(
                    thumbColor = ElectricBlue,
                    activeTrackColor = ElectricBlue,
                    inactiveTrackColor = CharcoalSurfaceHighlight
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            IconButton(
                onClick = {
                    val mp = mediaPlayer ?: return@IconButton
                    if (isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        mp.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .background(ElectricBlue, RoundedCornerShape(32.dp))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun GenericMediaViewer(file: File) {
    val context = LocalContext.current
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(file.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dung lượng: ${FileItem.formatBytes(file.length())}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "Đường dẫn: ${file.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { FileUtils.openWithExternalApp(context, file) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mở bằng ứng dụng hệ thống")
            }
        }
    }
}
