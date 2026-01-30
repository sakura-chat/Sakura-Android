package dev.kuylar.sakura

import android.app.Application
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SakuraApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		//Backend.set(DefaultBackend)
		DynamicColors.applyToActivitiesIfAvailable(this)
	}
}