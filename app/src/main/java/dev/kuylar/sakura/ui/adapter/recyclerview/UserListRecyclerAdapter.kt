package dev.kuylar.sakura.ui.adapter.recyclerview

import android.os.Handler
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ItemUserBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

class UserListRecyclerAdapter(val fragment: Fragment, val roomId: String) :
	RecyclerView.Adapter<UserListRecyclerAdapter.ViewHolder>() {
	private val client = Matrix.getClient()
	private val layoutInflater = fragment.layoutInflater
	private var users = mapOf<UserId, Flow<RoomUser?>>()

	init {
		suspendThread {
			client.getRoom(roomId)?.let {
				client.client.user.getAll(RoomId(roomId)).collect { users ->
					fragment.activity?.runOnUiThread {
						this.users = users
						// TODO: Replace this with inserted/removed callbacks
						notifyDataSetChanged()
					}
				}
			}
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	) = ViewHolder(ItemUserBinding.inflate(layoutInflater, parent, false))

	override fun onBindViewHolder(
		holder: ViewHolder,
		position: Int
	) {
		holder.bind(users.values.elementAt(position))
	}

	override fun onViewRecycled(holder: ViewHolder) {
		holder.dispose()
		super.onViewRecycled(holder)
	}

	override fun getItemCount() = users.size

	class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
		var job: Job? = null

		fun bind(userFlow: Flow<RoomUser?>) {
			job?.cancel()
			job = CoroutineScope(Dispatchers.Main).launch {
				userFlow.collect { user ->
					if (user == null) return@collect
					Handler(binding.root.context.mainLooper).post {
						Glide.with(binding.root)
							.load(user.avatarUrl)
							.into(binding.avatar)

						binding.name.text = user.name
						binding.status.visibility = View.GONE
					}
				}
			}
		}

		fun dispose() {
			job?.cancel()
			job = null
		}
	}
}