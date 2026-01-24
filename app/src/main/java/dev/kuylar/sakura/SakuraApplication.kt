package dev.kuylar.sakura

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend

@HiltAndroidApp
class SakuraApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		Backend.set(DefaultBackend)
		Backend.createLogger("Sakura").fatal {
			"Logger initialized"
		}
	}
}