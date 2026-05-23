package com.noexcs.indolent.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.noexcs.indolent.agent.tools.interact.ContentType
import com.noexcs.indolent.agent.tools.interact.DisplayContent
import com.noexcs.indolent.ui.theme.MarkdownContent
import java.io.File

@Composable
fun ContentDisplaySheet(
    content: DisplayContent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
    ) {
        if (!content.title.isNullOrBlank()) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        when (content.type) {
            ContentType.IMAGE -> ImageContent(content)
            ContentType.TEXT -> TextContent(content)
            ContentType.PDF -> PdfContent(content)
            ContentType.WEB -> Text(
                text = "Web page opened in Chrome",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.padding(bottom = 32.dp))
    }
}

@Composable
private fun ImageContent(content: DisplayContent) {
    val context = LocalContext.current
    val path = content.path

    if (path == null) {
        Text(
            text = "No image path provided",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    var loadError by remember { mutableStateOf<String?>(null) }

    if (loadError != null) {
        Text(
            text = loadError!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        },
        update = { imageView ->
            try {
                val bitmap = when {
                    path.startsWith("content://") -> {
                        val uri = Uri.parse(path)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                    else -> {
                        val file = File(path)
                        if (!file.exists()) {
                            loadError = "File not found: ${file.name}"
                            return@AndroidView
                        }
                        BitmapFactory.decodeFile(path)
                    }
                }
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    loadError = "Failed to decode image"
                }
            } catch (e: SecurityException) {
                loadError = "Permission denied: ${e.message}"
            } catch (e: Exception) {
                loadError = "Failed to load image: ${e.message}"
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextContent(content: DisplayContent) {
    if (content.textContent.isNullOrBlank()) {
        Text(
            text = "(empty)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        MarkdownContent(
            content = content.textContent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PdfContent(content: DisplayContent) {
    val context = LocalContext.current
    val path = content.path
    val file = remember(path) {
        path?.let { File(it) }
    }

    if (file == null || !file.exists()) {
        Text(
            text = "PDF file not found",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var renderError by remember { mutableStateOf<String?>(null) }

    // Page navigation
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { if (currentPage > 0) currentPage-- },
            enabled = currentPage > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page", modifier = Modifier.size(18.dp))
        }
        Text(
            text = "Page ${currentPage + 1} of ${if (pageCount > 0) pageCount else "?"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { if (currentPage < pageCount - 1) currentPage++ },
            enabled = currentPage < pageCount - 1,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page", modifier = Modifier.size(18.dp))
        }
    }

    if (renderError != null) {
        Text(
            text = renderError!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    setBackgroundColor(Color.WHITE)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
            },
            update = { imageView ->
                try {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd)
                    try {
                        pageCount = renderer.pageCount
                        val page = renderer.openPage(currentPage.coerceIn(0, pageCount - 1))
                        try {
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            imageView.setImageBitmap(bitmap)
                        } finally {
                            page.close()
                        }
                    } finally {
                        renderer.close()
                        fd.close()
                    }
                } catch (e: Exception) {
                    renderError = "Failed to render PDF: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

