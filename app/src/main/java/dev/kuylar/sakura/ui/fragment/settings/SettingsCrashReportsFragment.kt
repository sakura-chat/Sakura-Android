package dev.kuylar.sakura.ui.fragment.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.utils.toByteArrayFlow
import dev.kuylar.recyclerviewbuilder.ExtensibleRecyclerAdapter
import dev.kuylar.recyclerviewbuilder.RecyclerViewBuilder
import dev.kuylar.sakura.CrashHandler
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentSettingsCrashReportsBinding
import dev.kuylar.sakura.databinding.ItemCrashReportBinding
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import kotlin.io.path.Path

@AndroidEntryPoint
class SettingsCrashReportsFragment : Fragment() {
	private lateinit var binding: FragmentSettingsCrashReportsBinding
	private lateinit var adapter: ExtensibleRecyclerAdapter

	@Inject
	lateinit var client: Matrix
	private val items = emptyMap<String, CrashHandler.CrashReport>().toMutableMap()

	@SuppressLint("SetTextI18n")
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		adapter = RecyclerViewBuilder(requireContext())
			.addView<Pair<String, CrashHandler.CrashReport>, ItemCrashReportBinding> { binding, item, context ->
				val cause = getDeepestCause(item.second)
				binding.title.text = cause.exceptionClass + " @ " + item.second.timestamp
				binding.summary.text = cause.message + "\n\n" + cause.stackTrace

				binding.upload.setOnClickListener { upload(item, it) }
				binding.delete.setOnClickListener { delete(item) }
			}
			.build(binding.root)
		refreshItems()
	}

	private fun refreshItems() {
		adapter.clearItems()
		File(requireContext().filesDir, "crashes").apply { mkdirs() }
			.listFiles { it.nameWithoutExtension.startsWith("crash_") && it.extension == "json" }
			?.map {
				Pair(
					it.absolutePath,
					Json.decodeFromString<CrashHandler.CrashReport>(it.readText())
				)
			}
			?.let {
				adapter.addItems(it)
			}
	}

	private fun upload(item: Pair<String, CrashHandler.CrashReport>, buttonView: View) {
		val report = item.second
		buttonView.isEnabled = false
		lifecycleScope.launch {
			val uri = if (report.mxcUri != null) report.mxcUri else {
				val json = Json.encodeToString(report).toByteArray().toByteArrayFlow()
				val cacheUrl =
					client.client.media.prepareUploadMedia(json, ContentType.Application.Json)
				val mxcUri = client.client.media.uploadMedia(cacheUrl).getOrThrow()
				update(item.first, report.copy(mxcUri = mxcUri))
				mxcUri
			}

			activity?.runOnUiThread {
				val clipboard =
					requireContext().getSystemService<ClipboardManager>() ?: return@runOnUiThread
				val clip = ClipData.newPlainText("Sakura Crash Report URI", uri)
				clipboard.setPrimaryClip(clip)
				Toast.makeText(
					requireContext(),
					"Matrix URI copied to clipboard.",
					Toast.LENGTH_SHORT
				).show()
				buttonView.isEnabled = true
			}
		}
	}

	private fun update(key: String, report: CrashHandler.CrashReport) {
		Files.write(Path(key), Json.encodeToString(report).toByteArray())
		refreshItems()
	}

	private fun delete(item: Pair<String, CrashHandler.CrashReport>): Boolean {
		val success = Files.deleteIfExists(Path(item.first))
		refreshItems()
		return success
	}

	private fun getDeepestCause(report: CrashHandler.CrashReport): CrashHandler.CrashReport.Cause =
		if (report.cause == null) CrashHandler.CrashReport.Cause(
			report.exceptionClass,
			report.message,
			report.stackTrace,
			report.cause
		) else getDeepestCause(report.cause)

	private fun getDeepestCause(cause: CrashHandler.CrashReport.Cause): CrashHandler.CrashReport.Cause =
		if (cause.cause == null) cause else getDeepestCause(cause.cause)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentSettingsCrashReportsBinding.inflate(inflater, container, false)
		return binding.root
	}
}