package com.example.calculators.presentation

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import java.io.File
import java.io.InputStream

/**
 * Phase 9: Lightweight LruCache-backed thumbnail loader and composable that renders mechanical part
 * images while maintaining their original aspect ratio (`ContentScale.Fit`).
 */
object MechanicalBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(32) {}

    fun get(uri: String): ImageBitmap? = synchronized(cache) { cache.get(uri) }

    fun put(uri: String, bitmap: ImageBitmap) = synchronized(cache) { cache.put(uri, bitmap) }

    fun loadDownsampledBitmap(
        context: Context,
        imageUri: String,
        targetMaxDimensionPx: Int = 256
    ): ImageBitmap? {
        get(imageUri)?.let { return it }

        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openUriStream(context, imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                targetMaxDimensionPx,
                targetMaxDimensionPx
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }

            val bitmap = openUriStream(context, imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val imageBitmap = bitmap.asImageBitmap()
            put(imageUri, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun openUriStream(context: Context, uriString: String): InputStream? {
        return when {
            uriString.startsWith("file:///android_asset/") -> {
                val assetPath = uriString.removePrefix("file:///android_asset/")
                try {
                    context.assets.open(assetPath)
                } catch (_: Exception) {
                    val fileName = assetPath.substringAfterLast('/')
                    val fallbackCandidates = listOf(
                        File("app/src/main/assets/$assetPath"),
                        File("public/assets/aistudio/$fileName")
                    )
                    fallbackCandidates.firstOrNull { it.exists() }?.inputStream()
                }
            }
            uriString.startsWith("file://") -> {
                val file = File(Uri.parse(uriString).path ?: return null)
                if (file.exists()) file.inputStream() else null
            }
            uriString.startsWith("content://") -> {
                context.contentResolver.openInputStream(Uri.parse(uriString))
            }
            else -> {
                val file = File(uriString)
                if (file.exists()) file.inputStream() else null
            }
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

@Composable
fun MechanicalPartThumbnail(
    imageUri: String?,
    partName: String,
    needsConfirmation: Boolean = false,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasAssociatedUri = !imageUri.isNullOrBlank()
    val loadedBitmap = remember(imageUri) {
        if (hasAssociatedUri) {
            MechanicalBitmapCache.loadDownsampledBitmap(context, imageUri!!)
        } else {
            null
        }
    }

    val accessibilityDescription = if (hasAssociatedUri) {
        stringResource(id = R.string.content_desc_part_image, partName)
    } else {
        stringResource(id = R.string.content_desc_no_part_image, partName)
    }

    val shape = MaterialTheme.shapes.small
    val borderColor = if (needsConfirmation) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(BorderStroke(1.dp, borderColor), shape)
            .then(clickableModifier)
            .semantics { contentDescription = accessibilityDescription },
        contentAlignment = Alignment.Center
    ) {
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            )
        } else if (hasAssociatedUri) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.HideImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (needsConfirmation && hasAssociatedUri) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PriorityHigh,
                        contentDescription = stringResource(id = R.string.label_image_needs_confirmation),
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}
