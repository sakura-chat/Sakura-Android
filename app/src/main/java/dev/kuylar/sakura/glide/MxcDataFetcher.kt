package dev.kuylar.sakura.glide

import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

class MxcDataFetcher(val model: Uri, val width: Int, val height: Int) : DataFetcher<ByteBuffer> {
	override fun loadData(
		priority: Priority,
		callback: DataFetcher.DataCallback<in ByteBuffer>
	) {
		val isFullMedia = !model.getBooleanQueryParameter("thumbnail", true) || width < 0 || height < 0
		val isEncrypted = model.host == "sakuraNative" && model.path == "/encrypted"

		@Suppress("DEPRECATION")
		val client = Matrix.getClient()
		runBlocking {
			val res = if (isEncrypted) {
				client.client.media.getEncryptedMedia(
					Json.decodeFromString<EncryptedFile>(
						model.getQueryParameter("data")!!
					)
				)
			} else if (isFullMedia) {
				client.client.media.getMedia(model.toString().split("?")[0])
			} else {
				client.client.media.getThumbnail(
					model.toString().split("?")[0],
					model.getQueryParameter("width")?.toLongOrNull() ?: width.toLong(),
					model.getQueryParameter("height")?.toLongOrNull() ?: height.toLong(),
					ThumbnailResizingMethod.SCALE,
					// TODO: Get default animated previews from settings
					model.getQueryParameter("animated")?.toBooleanStrictOrNull() ?: true,
					saveToCache = false
				)
			}
			try {
				val image = res.getOrThrow()
				callback.onDataReady(ByteBuffer.wrap(image.toByteArray() ?: ByteArray(0)))
			} catch (e: Exception) {
				callback.onLoadFailed(e)
			}
		}
	}

	override fun cleanup() {}

	override fun cancel() {}

	override fun getDataClass() = ByteBuffer::class.java

	override fun getDataSource() = DataSource.LOCAL
}