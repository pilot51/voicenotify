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

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.*

class NotifyList internal constructor(context: Context?) : ListView(context) {
	private val res: Resources = resources

	private interface OnListChangeListener {
		fun onListChange()
	}

	init {
		divider = res.getDrawable(R.drawable.divider)
		adapter = Adapter()
	}

	private inner class Adapter : BaseAdapter() {
		private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

		init {
			listener = object : OnListChangeListener {
				override fun onListChange() {
					(context as Activity).runOnUiThread { notifyDataSetChanged() }
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
			val view = convertView ?: inflater.inflate(R.layout.notify_log_item, parent, false)
			val holder: ViewHolder
			if (convertView == null) {
				holder = ViewHolder()
				holder.time = view.findViewById(R.id.time)
				holder.title = view.findViewById(R.id.title)
				holder.message = view.findViewById(R.id.message)
				holder.ignoreReasons = view.findViewById(R.id.ignore_reasons)
				view.tag = holder
			} else {
				holder = view.tag as ViewHolder
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
				holder.ignoreReasons.text = item.getIgnoreReasonsAsText(view.context)
				if (item.isSilenced) holder.ignoreReasons.setTextColor(Color.YELLOW) else holder.ignoreReasons.setTextColor(Color.RED)
				holder.ignoreReasons.visibility = VISIBLE
			} else holder.ignoreReasons.visibility = GONE
			view.setOnLongClickListener {
				AlertDialog.Builder(context)
					.setTitle(res.getString(if (item.app.enabled) R.string.ignore_app else R.string.unignore_app,
						item.app.label))
					.setPositiveButton(R.string.yes) { _, _ ->
						item.app.setEnabled(!item.app.enabled, true)
						Toast.makeText(context, res.getString(if (item.app.enabled) R.string.app_is_not_ignored else R.string.app_is_ignored,
							item.app.label),
							Toast.LENGTH_SHORT).show()
					}
					.setNegativeButton(android.R.string.cancel, null)
					.show()
				false
			}
			return view
		}
	}

	companion object {
		private const val HISTORY_LIMIT = 20
		private val list: MutableList<NotificationInfo> = ArrayList(HISTORY_LIMIT)
		private var listener: OnListChangeListener? = null
		@JvmStatic
		fun refresh() {
			if (listener != null) {
				listener!!.onListChange()
			}
		}

		@JvmStatic
		fun addNotification(info: NotificationInfo) {
			if (list.size == HISTORY_LIMIT) {
				list.removeAt(list.size - 1)
			}
			list.add(0, info)
			refresh()
		}
	}
}