package dev.kuylar.sakura.ui.fragment.verification

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.kuylar.sakura.databinding.FragmentVerificationMessageBinding

class VerificationMessageFragment : Fragment() {
	private lateinit var binding: FragmentVerificationMessageBinding
	private var state: Boolean? = null
	private var title: Int? = null
	private var subtitle: Int? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		arguments?.let {
			state = it.getBoolean("state")
			title = it.getInt("title")
			subtitle = it.getInt("subtitle")
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentVerificationMessageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (state != null) {
			// TODO: Set shield color to green or red depending on state
		}

		title?.takeIf { it > 0 }?.let { binding.title.setText(it) }
		subtitle?.takeIf { it > 0 }?.let { binding.subtitle.setText(it) }
	}
}