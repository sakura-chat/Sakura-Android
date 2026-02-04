package dev.kuylar.sakura

import android.app.Application
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import dev.kuylar.sakura.client.Matrix
import javax.inject.Inject

@HiltAndroidApp
class SakuraApplication : Application() {
	@Inject
	lateinit var client: Matrix

	override fun onCreate() {
		super.onCreate()
		//Backend.set(DefaultBackend)
		DynamicColors.applyToActivitiesIfAvailable(this)
	}
}