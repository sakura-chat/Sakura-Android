package dev.kuylar.sakura.service

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.work.NotificationWorker
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.writeText

@AndroidEntryPoint
class SakuraFirebaseMessagingService : FirebaseMessagingService() {
	@Inject
	lateinit var client: Matrix

	override fun onNewToken(token: String) {
		super.onNewToken(token)
		Log.i("SakuraFirebaseMessagingService", "Refreshed token: $token")
		Path(applicationContext.filesDir.absolutePath, "fcm_token").writeText("$token\n")
		suspendThread {
			try {
				client.registerFcmPusher(token)
			} catch (e: Exception) {
				Log.e("MainActivity", "Failed to register FCM pusher!", e)
			}
		}
	}

	override fun onMessageReceived(message: RemoteMessage) {
		super.onMessageReceived(message)
		Log.i("SakuraFirebaseMessagingService", "Received message")
		val wm = WorkManager.getInstance(this)
		val request = OneTimeWorkRequest.Builder(NotificationWorker::class).apply {
			this.setInputData(Data.Builder().apply {
				this.putString("eventId", message.data["eventId"])
				this.putString("roomId", message.data["roomId"])
				this.putString("unread", message.data["unread"])
				this.putString("missedCalls", message.data["missedCalls"])
			}.build())
		}.build()
		wm.enqueue(request)
	}
}