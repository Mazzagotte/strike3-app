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

    /** Saves a file to Downloads/Strike III (no picker needed) and returns it, ready to open or share. */
    fun saveDownload(context: Context, fileName: String, mime: String, bytes: ByteArray): Uri =
        saveDownloadStream(context, fileName, mime) { it.write(bytes) }

    /** Like [saveDownload], but writes straight into the file (for big files like a photo zip). */
    fun saveDownloadStream(context: Context, fileName: String, mime: String, write: (java.io.OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Strike III")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("couldn't create the file in Downloads")
        try {
            resolver.openOutputStream(uri, "w")?.use { write(it) } ?: error("couldn't write the file")
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Opens the share sheet for a saved file (Drive, Gmail, Messages…). */
    fun shareFile(context: Context, uri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens a saved file in an app that can view it (Excel, Sheets…). */
    fun openFile(context: Context, uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
