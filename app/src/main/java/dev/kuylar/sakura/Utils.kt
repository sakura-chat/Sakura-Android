package dev.kuylar.sakura

import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

object Utils {
	fun suspendThread(block: suspend (() -> Unit)) {
		thread {
			runBlocking {
				block.invoke()
			}
		}
	}
}