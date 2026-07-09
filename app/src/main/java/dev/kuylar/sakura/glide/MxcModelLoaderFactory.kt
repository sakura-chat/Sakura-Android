package dev.kuylar.sakura.glide

import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.nio.ByteBuffer

class MxcModelLoaderFactory<T : Any> : ModelLoaderFactory<T, ByteBuffer> {
	override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<T, ByteBuffer> {
		return MxcModelLoader()
	}

	override fun teardown() {}
}