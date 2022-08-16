/*
 * Copyright 2012-2022 Mark Injerd
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

import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.ListFragment
import androidx.lifecycle.Lifecycle
import com.pilot51.voicenotify.AppDatabase.Companion.db
import com.pilot51.voicenotify.Common.getPrefs
import com.pilot51.voicenotify.databinding.AppListItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class AppListFragment : ListFragment(), MenuProvider {
	private val prefs by lazy { getPrefs(requireContext()) }
	private val adapter by lazy { Adapter() }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Common.init(requireActivity())
		defEnable = prefs.getBoolean(KEY_DEFAULT_ENABLE, true)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
		val lv = listView
		lv.isTextFilterEnabled = true
		lv.isFastScrollEnabled = true
		listAdapter = adapter
		lv.onItemClickListener = OnItemClickListener { _, _, position, _ ->
			setIgnore(adapter.getItem(position) as App, IGNORE_TOGGLE)
			adapter.notifyDataSetChanged()
		}
		updateAppsList()
	}

	private fun updateAppsList() {
		if (isUpdating) {
			adapter.setData(apps)
			return
		}
		setListShown(false)
		isUpdating = true
		CoroutineScope(Dispatchers.IO).launch {
			SYNC_APPS.withLock {
				apps = db.appDao.getAll().toMutableList()
				onListUpdated()
				val isFirstLoad = apps.isEmpty()
				val packMan = requireContext().packageManager

				// Remove uninstalled
				for (a in apps.indices.reversed()) {
					val app = apps[a]
					try {
						packMan.getApplicationInfo(app.packageName, 0)
					} catch (e: PackageManager.NameNotFoundException) {
						if (!isFirstLoad) app.remove()
						apps.removeAt(a)
						onListUpdated()
					}
				}

				// Add new
				inst@ for (appInfo in packMan.getInstalledApplications(0)) {
					for (app in apps) {
						if (app.packageName == appInfo.packageName) {
							continue@inst
						}
					}
					val app = App(
						packageName = appInfo.packageName,
						label = appInfo.loadLabel(packMan).toString(),
						isEnabled = defEnable
					)
					apps.add(app)
					onListUpdated()
					if (!isFirstLoad) app.updateDb()
				}
				apps.sortWith { app1, app2 -> app1.label.compareTo(app2.label, ignoreCase = true) }
				onListUpdated()
				if (isFirstLoad) db.appDao.upsert(apps)
			}
			isUpdating = false
			CoroutineScope(Dispatchers.Main).launch {
				setListShown(true)
			}
		}
	}

	private fun onListUpdated() {
		CoroutineScope(Dispatchers.Main).launch {
			adapter.setData(apps)
		}
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.app_list, menu)
		val menuFilter = menu.findItem(R.id.filter)!!
		val searchView = (menuFilter.actionView as SearchView).apply {
			setIconifiedByDefault(false)
			setOnQueryTextFocusChangeListener { view, hasFocus ->
				if (hasFocus) view.post {
					ContextCompat.getSystemService(
						view.context, InputMethodManager::class.java
					)!!.showSoftInput(view.findFocus(), 0)
				}
			}
		}
		menuFilter.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
			override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
				searchView.run {
					requestFocus()
					setOnQueryTextListener(object : SearchView.OnQueryTextListener {
						override fun onQueryTextSubmit(query: String) = false
						override fun onQueryTextChange(newText: String): Boolean {
							adapter.filter.filter(newText)
							return true
						}
					})
				}
				return true
			}
			override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
				searchView.setQuery(null, true)
				return true
			}
		})
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		when (menuItem.itemId) {
			R.id.ignore_all -> {
				setDefaultEnable(false)
				massIgnore(IGNORE_ALL)
				return true
			}
			R.id.ignore_none -> {
				setDefaultEnable(true)
				massIgnore(IGNORE_NONE)
				return true
			}
		}
		return false
	}

	private fun massIgnore(ignoreType: Int) {
		for (app in apps) {
			setIgnore(app, ignoreType)
		}
		adapter.notifyDataSetChanged()
		CoroutineScope(Dispatchers.IO).launch {
			db.appDao.upsert(apps)
		}
	}

	private fun setIgnore(app: App, ignoreType: Int) {
		if (!app.enabled && (ignoreType == IGNORE_TOGGLE || ignoreType == IGNORE_NONE)) {
			app.setEnabled(true, ignoreType == IGNORE_TOGGLE)
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(requireContext(), getString(R.string.app_is_not_ignored, app.label), Toast.LENGTH_SHORT).show()
			}
		} else if (app.enabled && (ignoreType == IGNORE_TOGGLE || ignoreType == IGNORE_ALL)) {
			app.setEnabled(false, ignoreType == IGNORE_TOGGLE)
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(requireContext(), getString(R.string.app_is_ignored, app.label), Toast.LENGTH_SHORT).show()
			}
		}
	}

	/** Set the default enabled value for new apps. */
	private fun setDefaultEnable(enable: Boolean) {
		defEnable = enable
		prefs.edit().putBoolean(KEY_DEFAULT_ENABLE, defEnable).apply()
	}

	private inner class Adapter : BaseAdapter(), Filterable {
		private val baseData: MutableList<App> = ArrayList()
		private val adapterData: MutableList<App> = ArrayList()
		private val inflater = requireContext().getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
		private val appFilter by lazy { SimpleFilter() }

		fun setData(list: List<App>?) {
			baseData.clear()
			baseData.addAll(list!!)
			refresh()
		}

		private fun refresh() {
			adapterData.clear()
			adapterData.addAll(baseData)
			notifyDataSetChanged()
		}

		override fun getCount(): Int {
			return adapterData.size
		}

		override fun getItem(position: Int): Any {
			return adapterData[position]
		}

		override fun getItemId(position: Int): Long {
			return position.toLong()
		}

		private inner class ViewHolder {
			lateinit var appLabel: TextView
			lateinit var appPackage: TextView
			lateinit var checkbox: CheckBox
		}

		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			lateinit var holder: ViewHolder
			val binding = convertView?.let {
				AppListItemBinding.bind(it).apply {
					holder = root.tag as ViewHolder
				}
			} ?: AppListItemBinding.inflate(inflater, parent, false).apply {
				holder = ViewHolder()
				holder.appLabel = appLabel
				holder.appPackage = appPackage
				holder.checkbox = checkbox
				root.tag = holder
			}
			holder.appLabel.text = adapterData[position].label
			holder.appPackage.text = adapterData[position].packageName
			holder.checkbox.isChecked = adapterData[position].enabled
			return binding.root
		}

		override fun getFilter() = appFilter

		inner class SimpleFilter : Filter() {
			override fun performFiltering(prefix: CharSequence?): FilterResults {
				val results = FilterResults()
				if (prefix.isNullOrEmpty()) {
					results.values = baseData
					results.count = baseData.size
				} else {
					val prefixString = prefix.toString().lowercase()
					val newValues: MutableList<App> = ArrayList()
					for (app in baseData) {
						if (app.label.lowercase().contains(prefixString)
							|| app.packageName.lowercase().contains(prefixString)) {
							newValues.add(app)
						}
					}
					results.values = newValues
					results.count = newValues.size
				}
				return results
			}

			override fun publishResults(constraint: CharSequence?, results: FilterResults) {
				adapterData.clear()
				adapterData.addAll(results.values as List<App>)
				if (results.count > 0) notifyDataSetChanged() else notifyDataSetInvalidated()
			}
		}
	}

	companion object {
		private lateinit var apps: MutableList<App>
		private var defEnable = false
		private const val KEY_DEFAULT_ENABLE = "defEnable"
		private const val IGNORE_TOGGLE = 0
		private const val IGNORE_ALL = 1
		private const val IGNORE_NONE = 2
		private val SYNC_APPS = Mutex()
		private var isUpdating = false

		/**
		 * @param pkg Package name used to find [App] in current list or create a new one from system.
		 * @param ctx Context required to get default enabled preference and to get package manager for searching system.
		 * @return Found or created [App], otherwise null if app not found on system.
		 */
		fun findOrAddApp(pkg: String, ctx: Context): App? {
			return runBlocking(Dispatchers.IO) {
				SYNC_APPS.withLock {
					if (!::apps.isInitialized) {
						defEnable = getPrefs(ctx).getBoolean(KEY_DEFAULT_ENABLE, true)
						apps = db.appDao.getAll().toMutableList()
					}
					for (app in apps) {
						if (app.packageName == pkg) {
							return@runBlocking app
						}
					}
					return@runBlocking try {
						val packMan = ctx.packageManager
						val app = App(
							packageName = pkg,
							label = packMan.getApplicationInfo(pkg, 0).loadLabel(packMan).toString(),
							isEnabled = defEnable
						)
						apps.add(app.updateDb())
						app
					} catch (e: PackageManager.NameNotFoundException) {
						e.printStackTrace()
						null
					}
				}
			}
		}
	}
}
