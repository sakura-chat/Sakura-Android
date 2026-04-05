package dev.kuylar.sakura.ui.models

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import de.connect2x.trixnity.utils.toByteArrayFlow
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.asFlow
import dev.kuylar.sakura.Utils.suspendThread
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentLength
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayOutputStream



open class AttachmentInfo {
	open var contentUri = "about:blank".toUri()
	open var contentType = "application/octet-stream"
	open var size = 0L
	open var name = ""
	open var ready = false
	open var onUpdate: (() -> Unit)? = null

	open suspend fun getAsFlow(context: Context): Flow<ByteArray>? {
		return null
	}

	open fun getThumbnail(width: Int, height: Int): Flow<ByteArray>? {
		return null
	}

	open fun getSize(): Pair<Int, Int>? {
		return null
	}

	@SuppressLint("Range")
	class ContentUri(
		override var contentUri: Uri,
		private val context: Context
	) : AttachmentInfo() {
		init {
			context.contentResolver.query(contentUri, null, null, null, null)?.use { cursor ->
				cursor.moveToNext()
				contentType = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
					.getCursorString(cursor)
					?: Utils.getMimeTypeFromExtension(contentUri.lastPathSegment)
				size =
					cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE).getCursorString(cursor)
						?.toLongOrNull() ?: 0
				name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).getCursorString(cursor)
					?: contentUri.lastPathSegment ?: "Attachment"
			}
		}

		private fun Int.getCursorString(cursor: Cursor): String? {
			return if (this < 0) null else try {
				cursor.getString(this)
			} catch (_: Exception) {
				null
			}
		}

		override var ready = true

		override suspend fun getAsFlow(context: Context) =
			context.contentResolver?.openInputStream(contentUri)?.asFlow()

		override fun getThumbnail(width: Int, height: Int): Flow<ByteArray>? {
			return when (contentType.substringBefore("/")) {
				"image" -> {
					context.contentResolver.openInputStream(contentUri)?.use { input ->
						BitmapFactory.decodeStream(input)
					}
				}

				"video" -> {
					val retriever = MediaMetadataRetriever()
					retriever.setDataSource(context, contentUri)
					val thumbnail =
						retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
					retriever.release()
					thumbnail
				}

				else -> null
			}?.let { bitmap ->
				val stream = ByteArrayOutputStream()
				ThumbnailUtils.extractThumbnail(bitmap, width, height).compress(Bitmap.CompressFormat.JPEG, 75, stream)
				stream.toByteArray().toByteArrayFlow()
			}
		}

		override fun getSize(): Pair<Int, Int>? {
			return when (contentType.substringBefore("/")) {
				"image" -> {
					val options = BitmapFactory.Options().apply {
						inJustDecodeBounds = true
					}
					context.contentResolver.openInputStream(contentUri)?.use { input ->
						BitmapFactory.decodeStream(input, null, options)
					}
					Pair(options.outWidth, options.outHeight)
				}

				"video" -> {
					val retriever = MediaMetadataRetriever()
					retriever.setDataSource(context, contentUri)
					val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
					val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
					retriever.release()
					Pair(width, height)
				}

				else -> null
			}
		}
	}

	class HttpUri(override var contentUri: Uri) : AttachmentInfo() {
		private var bodyAsBytes: ByteArray? = null

		init {
			suspendThread {
				val resp = HttpClient().get(contentUri.toString())
				bodyAsBytes = resp.bodyAsBytes()
				name = contentUri.lastPathSegment ?: contentUri.toString()
				contentType = resp.contentType()?.toString() ?: "applicaiton/octet-stream"
				size = resp.contentLength() ?: bodyAsBytes?.size?.toLong() ?: 0
				ready = true
				onUpdate?.invoke()
			}
		}

		override suspend fun getAsFlow(context: Context) = bodyAsBytes?.toByteArrayFlow()
	}
}