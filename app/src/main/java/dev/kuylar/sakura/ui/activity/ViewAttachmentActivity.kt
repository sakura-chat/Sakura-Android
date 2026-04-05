package dev.kuylar.sakura.ui.activity

import android.annotation.SuppressLint
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.store.AuthenticationStore
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.toFileSize
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ActivityViewAttachmentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@AndroidEntryPoint
class ViewAttachmentActivity : AppCompatActivity(), Toolbar.OnMenuItemClickListener {
	private lateinit var binding: ActivityViewAttachmentBinding
	private lateinit var uri: Uri
	private lateinit var mime: String
	private lateinit var name: String
	private var size: Long = 0

	@Inject
	lateinit var client: Matrix

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityViewAttachmentBinding.inflate(layoutInflater)
		setContentView(binding.root)

		@Suppress("LocalVariableName") val _uri = intent.extras?.getString("uri")?.toUri()
		@Suppress("LocalVariableName") val _mime = intent.extras?.getString("mime")
		@Suppress("LocalVariableName") val _name = intent.extras?.getString("name")
		@Suppress("LocalVariableName") val _size = intent.extras?.getLong("size") ?: 0

		if (_uri == null || _mime == null) {
			finish()
			return
		}

		uri = _uri
		mime = _mime
		name = _name ?: uri.lastPathSegment ?: uri.toString()
		size = _size

		binding.toolbar.subtitle = name

		when (mime.substringBefore("/")) {
			"image" -> handleImage(uri)
			"video", "audio" -> handleVideo(uri)
			else -> handleOthers(name, mime, size)
		}

		binding.toolbar.setNavigationOnClickListener {
			finish()
		}
		binding.toolbar.setOnMenuItemClickListener(this)

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}
	}

	override fun onMenuItemClick(item: MenuItem?): Boolean {
		return when (item?.itemId) {
			R.id.download -> {
				runBlocking {
					try {
						val res = withContext(Dispatchers.IO) {
							client.getMedia(uri).getOrNull()?.toByteArray()
						} ?: return@runBlocking false
						val values = ContentValues().apply {
							put(MediaStore.MediaColumns.DISPLAY_NAME, name)
							put(MediaStore.MediaColumns.MIME_TYPE, mime)
							put(
								MediaStore.MediaColumns.RELATIVE_PATH,
								Environment.DIRECTORY_DOWNLOADS
							)
						}

						val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
						val itemUri = contentResolver.insert(collection, values)

						itemUri?.let { uri ->
							contentResolver.openOutputStream(uri)?.use { output ->
								output.write(res)
								output.flush()
							}
						}
						Toast.makeText(
							this@ViewAttachmentActivity,
							getString(R.string.attachment_viewer_download_success, name),
							Toast.LENGTH_LONG
						).show()
					} catch (e: Exception) {
						Log.e("ViewAttachmentActivity", "Failed to download attachment", e)
						Toast.makeText(
							this@ViewAttachmentActivity,
							R.string.attachment_viewer_download_fail,
							Toast.LENGTH_LONG
						).show()
					}
					true
				}
			}

			else -> false
		}
	}

	private fun handleImage(uri: Uri) {
		binding.image.visibility = View.VISIBLE
		Glide.with(this)
			.load(uri)
			.into(binding.image)
	}

	@OptIn(UnstableApi::class)
	private fun handleVideo(uri: Uri) {
		binding.video.visibility = View.VISIBLE
		lifecycleScope.launch {
			val (serverName, mediaId) = uri.toString().removePrefix("mxc://")
				.let { it.substringBefore("/") to it.substringAfter("/") }
			val uri = "${client.client.api.baseUrl}/_matrix/client/v1/media/download/$serverName/$mediaId"
			val auth = Json.decodeFromString<JsonObject>(
				client.client.di.get<AuthenticationStore>().getAuthentication()?.providerData
					?: "{}"
			)
			val token = auth["accessToken"]?.jsonPrimitive?.content ?: return@launch
			Log.i("ViewAttachmentActivity", uri)
			Log.i("ViewAttachmentActivity", token)
			runOnUiThread {
				val item = MediaItem.Builder().apply {
					this.setMimeType(mime)
					this.setUri(uri)
				}.build()

				val dataSourceFactory = DefaultHttpDataSource.Factory()
					.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))

				val mediaSourceFactory = DefaultMediaSourceFactory(this@ViewAttachmentActivity)
					.setDataSourceFactory(dataSourceFactory)

				val player = ExoPlayer.Builder(this@ViewAttachmentActivity)
					.setMediaSourceFactory(mediaSourceFactory)
					.build()
				binding.video.player = player
				player.setMediaItem(item)
				player.prepare()
				player.play()
			}
		}
	}

	private fun handleOthers(name: String, mime: String, size: Long) {
		binding.unk.visibility = View.VISIBLE
		binding.filename.text = name
		@SuppressLint("SetTextI18n")
		binding.meta.text = "$mime \u2022 ${size.toFileSize()}"
	}
}