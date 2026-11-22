package dev.mazzagotte.strike3.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import dev.mazzagotte.strike3.data.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Media {
    /** Finished posts. Turn on backup for this folder in Google Photos. */
    const val POSTS_DIR = "Pictures/Strike III"
    /** Original card photos taken in the app. */
    const val ORIGINALS_DIR = "Pictures/Strike III Originals"
    /** White-background photos for selling or trading. */
    const val LISTINGS_DIR = "Pictures/Strike III Listings"

    fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun saveJpeg(context: Context, bmp: Bitmap, name: String, dir: String = POSTS_DIR): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, dir)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the image file")
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ?: error("Couldn't write the image file")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Removes an image the app saved earlier (ignored if it's already gone). */
    fun delete(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    /** Empty gallery entry for the system camera to write a photo into. */
    fun newCameraUri(context: Context): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "card-${stamp()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, ORIGINALS_DIR)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    /** Shares several images at once; Instagram turns them into a carousel. */
    fun shareCarousel(context: Context, images: List<Uri>, caption: String) {
        copy(context, caption)
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(images))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val ig = Intent(send).setPackage("com.instagram.android")
        val target = if (ig.resolveActivity(context.packageManager) != null) ig
        else Intent.createChooser(send, "Share carousel")
        context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Regular share sheet (for listings: Messages, eBay, Facebook, etc.). */
    fun shareAnywhere(context: Context, image: Uri, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun thumbnail(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val s = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
            if (s < 1f) d.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
        }
    } catch (_: Exception) {
        null
    }

    /** Caption in the style last picked on a card (see [Captions]). */
    fun caption(context: Context, card: Card): String = Captions.build(card, Captions.defaultStyle(context))

    fun copy(context: Context, text: String, label: String = "Caption") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    /** Copies the caption, then opens Instagram's share options (or the share sheet). */
    fun shareToInstagram(context: Context, image: Uri, caption: String) {
        copy(context, caption)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val ig = Intent(send).setPackage("com.instagram.android")
        val target = if (ig.resolveActivity(context.packageManager) != null) ig
        else Intent.createChooser(send, "Share post")
        context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
