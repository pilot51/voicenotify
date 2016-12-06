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

package com.pilot51.voicenotify;

public class App {
	private String packageName, label;
	private boolean enabled;
	
	App(String pkg, String name, boolean enable) {
		packageName = pkg;
		label = name;
		enabled = enable;
	}
	
	/**
	 * Updates self in database.
	 * @return This instance.
	 */
	App updateDb() {
		Database.addOrUpdateApp(this);
		return this;
	}
	
	void setEnabled(boolean enable, boolean updateDb) {
		enabled = enable;
		if (updateDb) Database.updateAppEnable(this);
	}
	
	/** Removes self from database. */
	void remove() {
		Database.removeApp(this);
	}
	
	String getLabel() {
		return label;
	}
	
	String getPackage() {
		return packageName;
	}
	
	boolean getEnabled() {
		return enabled;
	}
}
