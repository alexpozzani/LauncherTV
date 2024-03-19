/*
 * Simple TV Launcher
 * Copyright 2024 Alexandre Del Bigio
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

package com.alexpozzani.tvlauncher;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;


public class AppInfo {
	private final Drawable mIcon;
	private String mName;
	private final String mPackageName;

	public AppInfo(PackageManager packageManager, ResolveInfo resolveInfo) {
		mPackageName = resolveInfo.activityInfo.packageName;

		//mIcon = resolveInfo.loadIcon(packageManager);
		mIcon = getApplicationIcon(packageManager, resolveInfo);
		try {
			mName = resolveInfo.loadLabel(packageManager).toString();
		} catch (Exception e) {
			mName = mPackageName;
		}
	}

	@NonNull
	public String getName() {
		if (mName != null)
			return mName;
		return ("");
	}

	public Drawable getIcon() {
		return mIcon;
	}

	public String getPackageName() {
		return mPackageName;
	}

	private Drawable getApplicationIcon(PackageManager packageManager, ResolveInfo resolveInfo) {
		Drawable appIcon = null;

		/*
		try {
			//https://developer.android.com/reference/android/content/pm/PackageManager#getApplicationBanner(java.lang.String)

			resolveInfo.activityInfo.loadBanner(packageManager);
			Drawable banner = resolveInfo.activityInfo.loadBanner(packageManager);
			if (banner == null) {
				banner = resolveInfo.activityInfo.applicationInfo.loadBanner(packageManager);
			}

			appIcon = banner;
		} catch (Exception e) {
			Log.e("check", "error getting Banner :", e);
		}
		*/

		if (appIcon == null) {
			try {
				ApplicationInfo applicationInfo = resolveInfo.activityInfo.applicationInfo;
				Resources resourcesForApplication = packageManager.getResourcesForApplication(applicationInfo);
				appIcon = resourcesForApplication.getDrawableForDensity(applicationInfo.icon, DisplayMetrics.DENSITY_XXXHIGH, null);
			} catch (Exception e) {
				Log.e("check", "error getting Hi Res Icon :", e);
				appIcon = resolveInfo.activityInfo.applicationInfo.loadIcon(packageManager);
			}
		}

		return appIcon;
	}
}
