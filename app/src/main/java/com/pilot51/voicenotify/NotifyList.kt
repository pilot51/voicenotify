/*
 * Copyright 2012 Mark Injerd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pilot51.voicenotify

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.pilot51.voicenotify.databinding.NotifyLogItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class NotifyList(context: Context?) : ListView(context) {
	private interface OnListChangeListener {
		fun onListChange()
	}

	init {
		adapter = Adapter()
	}

	private inner class Adapter : BaseAdapter() {
		private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

		init {
			listener = object : OnListChangeListener {
				override fun onListChange() {
					CoroutineScope(Dispatchers.Main).launch {
						notifyDataSetChanged()
					}
				}
			}
		}

		override fun getCount(): Int {
			return list.size
		}

		override fun getItem(position: Int): Any {
			return list[position]
		}

		override fun getItemId(position: Int): Long {
			return position.toLong()
		}

		private inner class ViewHolder {
			lateinit var time: TextView
			lateinit var title: TextView
			lateinit var message: TextView
			lateinit var ignoreReasons: TextView
		}

		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			lateinit var holder: ViewHolder
			val binding = convertView?.let {
				NotifyLogItemBinding.bind(it).apply {
					holder = root.tag as ViewHolder
				}
			} ?: NotifyLogItemBinding.inflate(inflater, parent, false).apply {
				holder = ViewHolder()
				holder.time = time
				holder.title = title
				holder.message = message
				holder.ignoreReasons = ignoreReasons
				root.tag = holder
			}
			val item = list[position]
			holder.time.text = item.time
			holder.title.text = item.app!!.label
			val logMessage = item.logMessage
			if (logMessage.isNotEmpty()) {
				holder.message.text = logMessage
				holder.message.visibility = VISIBLE
			} else holder.message.visibility = GONE
			if (item.getIgnoreReasons().isNotEmpty()) {
				holder.ignoreReasons.text = item.getIgnoreReasonsAsText(context)
				if (item.isSilenced) {
					holder.ignoreReasons.setTextColor(Color.YELLOW)
				} else holder.ignoreReasons.setTextColor(Color.RED)
				holder.ignoreReasons.visibility = VISIBLE
			} else holder.ignoreReasons.visibility = GONE
			binding.root.setOnLongClickListener {
				AlertDialog.Builder(context)
					.setTitle(resources.getString(
						if (item.app.enabled) R.string.ignore_app else R.string.unignore_app,
						item.app.label
					))
					.setPositiveButton(R.string.yes) { _, _ ->
						item.app.setEnabled(!item.app.enabled, true)
						Toast.makeText(context,
							resources.getString(
								if (item.app.enabled) R.string.app_is_not_ignored else R.string.app_is_ignored,
								item.app.label
							),
							Toast.LENGTH_SHORT
						).show()
					}
					.setNegativeButton(android.R.string.cancel, null)
					.show()
				false
			}
			return binding.root
		}
	}

	companion object {
		private const val HISTORY_LIMIT = 20
		private val list: MutableList<NotificationInfo> = ArrayList(HISTORY_LIMIT)
		private var listener: OnListChangeListener? = null

		fun refresh() {
			listener?.onListChange()
		}

		fun addNotification(info: NotificationInfo) {
			if (list.size == HISTORY_LIMIT) {
				list.removeAt(list.size - 1)
			}
			list.add(0, info)
			refresh()
		}
	}
}
