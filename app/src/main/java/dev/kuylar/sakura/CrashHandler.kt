package dev.kuylar.sakura

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object CrashHandler {
	private val executor = Executors.newSingleThreadExecutor()
	private var previousHandler: Thread.UncaughtExceptionHandler? = null

	@Volatile
	private var installed = false

	fun install(context: Context) {
		if (installed) return
		installed = true

		val appContext = context.applicationContext
		previousHandler = Thread.getDefaultUncaughtExceptionHandler()

		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			try {
				writeCrashReport(appContext, thread, throwable)
			} catch (_: Throwable) {
				// Avoid crashing again while handling the crash.
			} finally {
				previousHandler?.uncaughtException(thread, throwable)
			}
		}
	}

	@Suppress("KotlinConstantConditions")
	private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
		executor.execute {
			val report = CrashReport(
				SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()),
				thread.name,
				thread.id,
				throwable::class.java.name,
				throwable.message,
				throwable.stackTraceToString(),
				throwable.cause?.let { causeToJson(it) },
				CrashReport.Device(
					Build.MANUFACTURER,
					Build.MODEL,
					Build.BRAND,
					Build.DEVICE,
					Build.PRODUCT,
					Build.VERSION.SDK_INT,
					Build.VERSION.RELEASE
				),
				CrashReport.AppInfo(
					BuildConfig.APPLICATION_ID,
					BuildConfig.VERSION_CODE,
					BuildConfig.VERSION_NAME,
					BuildConfig.DEBUG
				),
				null
			)

			val crashesDir = File(context.filesDir, "crashes").apply { mkdirs() }
			val fileName = "crash_${System.currentTimeMillis()}.json"
			File(crashesDir, fileName).writeText(Json.encodeToString(report))
		}
	}

	private fun causeToJson(throwable: Throwable): CrashReport.Cause = CrashReport.Cause(
		throwable::class.java.name,
		throwable.message,
		throwable.stackTraceToString(),
		throwable.cause?.let { causeToJson(it) },
	)

	@Serializable
	data class CrashReport(
		val timestamp: String,
		val threadName: String,
		val threadId: Long,
		val exceptionClass: String,
		val message: String?,
		val stackTrace: String,
		val cause: Cause?,
		val device: Device,
		val app: AppInfo,
		val mxcUri: String?
	) {
		@Serializable
		data class Cause(
			val exceptionClass: String,
			val message: String?,
			val stackTrace: String,
			val cause: Cause?,
		)

		@Serializable
		data class Device(
			val manufacturer: String,
			val model: String,
			val brand: String,
			val device: String,
			val product: String,
			val sdkInt: Int,
			val release: String
		)

		@Serializable
		data class AppInfo(
			val id: String,
			val versionCode: Int,
			val versionName: String,
			val debug: Boolean
		)
	}
}