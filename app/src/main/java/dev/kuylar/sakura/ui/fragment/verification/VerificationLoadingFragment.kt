package dev.kuylar.sakura.ui.fragment.verification

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.databinding.FragmentVerificationLoadingBinding

@AndroidEntryPoint
class VerificationLoadingFragment : Fragment() {
	private lateinit var binding: FragmentVerificationLoadingBinding

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentVerificationLoadingBinding.inflate(inflater, container, false)
		return binding.root
	}
}