package dev.kuylar.sakura.glide

import android.net.Uri
import androidx.core.net.toUri
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import java.nio.ByteBuffer

class MxcModelLoader<T : Any> : ModelLoader<T, ByteBuffer> {
	override fun buildLoadData(
		model: T,
		width: Int,
		height: Int,
		options: Options
	): ModelLoader.LoadData<ByteBuffer?> {
		val modelUri = if (model is String) model.toUri() else model as? Uri
		val diskCacheKey = ObjectKey(modelUri!!.toString())
		return ModelLoader.LoadData(diskCacheKey, MxcDataFetcher(modelUri, width, height))
	}

	override fun handles(model: T): Boolean {
		return try {
			val uri = if (model is String) model.toUri() else model as? Uri ?: return false
			return uri.scheme == "mxc" && uri.host != null && uri.path != null
		} catch (_: Exception) {
			false
		}
	}
}