package dev.kuylar.sakura.ui.models

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
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

	@SuppressLint("Range")
	class ContentUri(
		override var contentUri: Uri,
		context: Context
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