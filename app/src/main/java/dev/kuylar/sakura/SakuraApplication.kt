package dev.kuylar.sakura

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import dev.kuylar.sakura.client.Matrix
import javax.inject.Inject

@HiltAndroidApp
class SakuraApplication : Application(), Configuration.Provider {
	@Inject
	lateinit var client: Matrix

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	override fun onCreate() {
		super.onCreate()
		//Backend.set(DefaultBackend)
		DynamicColors.applyToActivitiesIfAvailable(this)
		CrashHandler.install(this)
	}

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()
}