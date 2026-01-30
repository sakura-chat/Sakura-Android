package dev.kuylar.sakura.ui.models

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import dev.kuylar.sakura.Utils.asFlow
import kotlinx.coroutines.flow.Flow

data class AttachmentInfo(
	val contentUri: Uri,
	val contentType: String,
	val size: Long,
	val name: String,
	var mxcId: String? = null
) {
	fun getAsFlow(context: Context): Flow<ByteArray> {
		val attachmentStream = context.contentResolver?.openInputStream(contentUri)
		return attachmentStream!!.asFlow()
	}

	companion object {
		@SuppressLint("Range")
		fun from(uri: Uri, context: Context): AttachmentInfo? {
			return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
				if (!cursor.moveToFirst()) return@use null
				AttachmentInfo(
					uri,
					cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)),
					cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE))
						.toLongOrNull() ?: 0,
					cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
				)
			}
		}
	}
}