package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.Job
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomModel(
	val id: RoomId,
	var snapshot: Room,
	val client: Matrix,
	var onChange: (() -> Unit)? = null
) {
	private var collectJob: Job? = null
	private var receiptJob: Job? = null
	private var pushRuleJob: Job? = null
	private var mentionsJob: Job? = null
	var lastMessage: TimelineEvent? = null
	var isUnread = false
	var mentions = 0
	var muted = false

	init {
		collectJob = suspendThread {
			client.client.room.getById(id).collect {
				snapshot = it ?: snapshot
				snapshot.lastRelevantEventId?.let { eventId ->
					lastMessage = client.getEvent(id, eventId)
				}
				onChange?.invoke()
			}
		}
		receiptJob = suspendThread {
			client.client.notification.isUnread(id).collect {
				isUnread = it
				onChange?.invoke()
			}
		}
		mentionsJob = suspendThread {
			client.client.notification.getCount(id).collect {
				mentions = it
				onChange?.invoke()
			}
		}
		/*
		pushRuleJob = suspendThread {
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
				val mergedRule = overrideRule ?: roomRule
				mergedRule?.let { rule ->
					muted = rule.actions.any { action -> action.name == "dont_notify" }
				}
			}
		}
		 */
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
		receiptJob?.cancel()
		receiptJob = null
		pushRuleJob?.cancel()
		pushRuleJob = null
		mentionsJob?.cancel()
		mentionsJob = null
	}
}