package dev.kuylar.sakura.ui.fragment.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentInitialSyncBinding
import dev.kuylar.sakura.ui.activity.InitialSyncCompleteListener
import javax.inject.Inject

@AndroidEntryPoint
class InitialSyncFragment : Fragment() {
	private lateinit var binding: FragmentInitialSyncBinding
	@Inject lateinit var client: Matrix

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentInitialSyncBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (activity !is InitialSyncCompleteListener) {
			context?.let {
				Toast.makeText(
					it,
					"Activity ${activity?.javaClass?.name} does not inherit from InitialSyncCompleteListener! We will be stuck here forever!!",
					Toast.LENGTH_LONG
				).show()
			}
		}
		suspendThread {
			val start = System.currentTimeMillis()
			client.startSync()
			val end = System.currentTimeMillis()
			Log.i("InitialSyncFragment", "Initial sync took ${end - start}ms")
			client.client.initialSyncDone.collect { complete ->
				if (complete) {
					activity?.runOnUiThread {
						(activity as? InitialSyncCompleteListener)?.onInitialSyncComplete()
					}
				}
			}
		}
	}
}