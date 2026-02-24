package dev.kuylar.sakura.ui.fragment.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
	private val matrix: Matrix
) : ViewModel() {
	@OptIn(ExperimentalCoroutinesApi::class)
	val notifications = matrix.getNotifications()
		.flatMapLatest { flows ->
			if (flows.isEmpty()) flowOf(emptyList())
			else combine(flows) { it.filterNotNull().toList() }
		}
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = emptyList()
		)
}