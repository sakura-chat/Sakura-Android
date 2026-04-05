package dev.kuylar.sakura.glide

import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class MxcDataFetcher(val model: Uri, val width: Int, val height: Int) : DataFetcher<ByteBuffer> {
	override fun loadData(
		priority: Priority,
		callback: DataFetcher.DataCallback<in ByteBuffer>
	) {
		@Suppress("DEPRECATION")
		val client = Matrix.getClient()
		runBlocking {
			val res = client.getMedia(model, width, height)
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