package dev.kuylar.sakura.glide

import androidx.core.net.toUri
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import java.nio.ByteBuffer

class MxcModelLoader : ModelLoader<String, ByteBuffer> {
	override fun buildLoadData(
		model: String,
		width: Int,
		height: Int,
		options: Options
	): ModelLoader.LoadData<ByteBuffer?> {
		val diskCacheKey = ObjectKey(model)
		return ModelLoader.LoadData(diskCacheKey, MxcDataFetcher(model.toUri(), width, height))
	}

	override fun handles(model: String): Boolean {
		return try {
			val uri = model.toUri()
			return uri.scheme == "mxc" && uri.host != null && uri.path != null
		} catch (_: Exception) {
			false
		}
	}
}