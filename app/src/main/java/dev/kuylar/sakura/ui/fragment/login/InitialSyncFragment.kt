package dev.kuylar.sakura.ui.fragment.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentInitialSyncBinding
import dev.kuylar.sakura.ui.activity.InitialSyncCompleteListener
import net.folivo.trixnity.client.MatrixClient

class InitialSyncFragment : Fragment() {
	private lateinit var binding: FragmentInitialSyncBinding
	private lateinit var client: Matrix

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		client = Matrix.getClient()
	}

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