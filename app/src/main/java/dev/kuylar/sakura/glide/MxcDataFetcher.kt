package dev.kuylar.sakura.glide

import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.runBlocking
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import okhttp3.OkHttpClient
import java.nio.ByteBuffer

class MxcDataFetcher(val model: Uri, val width: Int, val height: Int) : DataFetcher<ByteBuffer> {
	private val http = OkHttpClient.Builder().apply {
		followRedirects(true)
		followRedirects(true)
	}.build()

	override fun loadData(
		priority: Priority,
		callback: DataFetcher.DataCallback<in ByteBuffer>
	) {
		val isFullMedia = !model.getBooleanQueryParameter("thumbnail", true)
		@Suppress("DEPRECATION")
		val client = Matrix.getClient()
		runBlocking {
			val res =
				if (isFullMedia) {
					client.client.media.getMedia(model.toString().split("?")[0])
				} else {
					client.client.media.getThumbnail(
						model.toString().split("?")[0],
						model.getQueryParameter("width")?.toLongOrNull() ?: width.toLong(),
						model.getQueryParameter("height")?.toLongOrNull() ?: height.toLong(),
						ThumbnailResizingMethod.SCALE
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

	override fun cleanup() {
		http.dispatcher.executorService.shutdown()
	}

	override fun cancel() {}

	override fun getDataClass() = ByteBuffer::class.java

	override fun getDataSource() = DataSource.REMOTE
}