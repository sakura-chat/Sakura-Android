package dev.kuylar.sakura.ui.fragment.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.utils.toByteArrayFlow
import dev.kuylar.recyclerviewbuilder.ExtensibleRecyclerAdapter
import dev.kuylar.recyclerviewbuilder.RecyclerViewBuilder
import dev.kuylar.sakura.CrashHandler
import dev.kuylar.sakura.R
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentSettingsCrashReportsBinding
import dev.kuylar.sakura.databinding.ItemCrashReportBinding
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.io.path.Path

@AndroidEntryPoint
class SettingsCrashReportsFragment : Fragment(), MenuProvider {
	private lateinit var binding: FragmentSettingsCrashReportsBinding
	private lateinit var adapter: ExtensibleRecyclerAdapter

	@Inject
	lateinit var client: Matrix

	private val exportFilePicker = registerForActivityResult(
		ActivityResultContracts.CreateDocument("application/zip")
	) { treeUri ->
		if (treeUri != null) {
			requireContext().contentResolver.takePersistableUriPermission(
				treeUri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			)
			exportCrashReportsToDirectory(treeUri)
		}
	}

	@SuppressLint("SetTextI18n")
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		adapter = RecyclerViewBuilder(requireContext())
			.addView<Pair<String, CrashHandler.CrashReport>, ItemCrashReportBinding> { binding, item, _ ->
				val cause = getDeepestCause(item.second)
				binding.title.text = cause.exceptionClass + " @ " + item.second.timestamp
				binding.summary.text = cause.message + "\n\n" + cause.stackTrace

				binding.upload.setOnClickListener { upload(item, it) }
				binding.delete.setOnClickListener { delete(item) }
			}
			.setMaterialDivider()
			.build(binding.root)
		refreshItems()

		val menuHost: MenuHost = requireActivity()
		menuHost.addMenuProvider(
			this,
			viewLifecycleOwner,
			Lifecycle.State.RESUMED
		)
	}

	private fun refreshItems() {
		adapter.clearItems()
		getAllFiles()
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

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.crash_logs, menu)
	}

	private fun getAllFiles() = File(requireContext().filesDir, "crashes").apply { mkdirs() }
		.listFiles { it.nameWithoutExtension.startsWith("crash_") && it.extension == "json" }

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.export -> {
				val files = getAllFiles()
				if (!files.isNullOrEmpty()) exportFilePicker.launch("sakura_crashes.zip")
				true
			}

			R.id.clear_all -> {
				getAllFiles()?.forEach { it.delete() }
				refreshItems()
				true
			}

			else -> false
		}
	}

	private fun exportCrashReportsToDirectory(uri: Uri) {
		val files = getAllFiles()
		if (files.isNullOrEmpty()) return

		context?.contentResolver?.openOutputStream(uri)?.use { output ->
			ZipOutputStream(output).use { zip ->
				files.forEach { file ->
					val entry = ZipEntry(file.name)
					zip.putNextEntry(entry)
					FileInputStream(file).use { fileStream -> fileStream.copyTo(zip) }
					entry.creationTime = FileTime.fromMillis(file.lastModified())
					entry.lastModifiedTime = FileTime.fromMillis(file.lastModified())
					zip.closeEntry()
				}
			}
		}
	}
}