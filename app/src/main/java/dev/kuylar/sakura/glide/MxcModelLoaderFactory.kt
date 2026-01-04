package dev.kuylar.sakura.glide

import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.nio.ByteBuffer

class MxcModelLoaderFactory : ModelLoaderFactory<String, ByteBuffer> {
	override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, ByteBuffer> {
		return MxcModelLoader()
	}

	override fun teardown() {}
}