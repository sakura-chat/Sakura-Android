package dev.kuylar.sakura.glide

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import java.nio.ByteBuffer

@GlideModule
class SakuraAppGlideModule: AppGlideModule() {
	override fun registerComponents(
		context: Context,
		glide: Glide,
		registry: Registry
	) {
		registry.append(String::class.java, ByteBuffer::class.java, MxcModelLoaderFactory())
	}
}