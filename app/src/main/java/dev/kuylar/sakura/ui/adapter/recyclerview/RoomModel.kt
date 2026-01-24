package dev.kuylar.sakura.ui.adapter.recyclerview

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.push.PushCondition
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomModel(
	val id: RoomId,
	var snapshot: Room,
	val client: Matrix,
	var onChange: (() -> Unit)? = null
) {
	private var collectJob: Job? = null
	private var receiptJob: Job? = null
	private var pushRuleJob: Job? = null
	var readReceipt = Pair(EventId(""), 0L)
	var lastMessage: TimelineEvent? = null
	var isUnread = false
	var muted = false

	init {
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.getById(snapshot.roomId).collect {
				snapshot = it ?: snapshot
				snapshot.lastRelevantEventId?.let { eventId ->
					lastMessage = client.getEvent(id, eventId)
				}
				onChange?.invoke()
			}
		}
		// TODO: Handle m.marked_unread
		receiptJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.user.getReceiptsById(snapshot.roomId, client.userId).collect {
				val lastReceipt =
					it?.receipts?.maxBy { r -> r.value.receipt.timestamp } ?: return@collect
				if (lastReceipt.key == ReceiptType.FullyRead) {
					readReceipt = Pair(lastReceipt.value.eventId, Long.MAX_VALUE)
					return@collect
				}
				val receiptEventId = lastReceipt.value.eventId
				val event = client.getEvent(id, receiptEventId)
				readReceipt = Pair(
					receiptEventId,
					event?.originTimestamp ?: lastReceipt.value.receipt.timestamp
				)
				updateIsUnread()
			}
		}
		pushRuleJob = CoroutineScope(Dispatchers.Main).launch {
			client.pushRules.collect {
				val overrideRule = it.override?.firstOrNull { override ->
					override.conditions?.any { condition ->
						condition is PushCondition.EventMatch
								&& condition.key == "room_id"
								&& condition.pattern == id.full
					} ?: false
				}?.takeIf { rule -> rule.enabled }
				val roomRule = it.room?.firstOrNull { rule -> rule.ruleId == id.full }
					?.takeIf { rule -> rule.enabled }
				// TODO: Get if we should show a indicator by default
				val mergedRule = overrideRule ?: roomRule
				mergedRule?.let { rule ->
					muted = rule.actions.any { action -> action.name == "dont_notify" }
				}
			}
		}
	}

	private fun updateIsUnread() {
		isUnread = !muted &&
			readReceipt.second < (snapshot.lastRelevantEventTimestamp?.toEpochMilliseconds() ?: 0)
		onChange?.invoke()
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
		receiptJob?.cancel()
		receiptJob = null
		pushRuleJob?.cancel()
		pushRuleJob = null
	}
}