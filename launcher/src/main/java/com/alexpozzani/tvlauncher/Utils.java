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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Utils {
	public static List<AppInfo> loadApplications(Context context) {
		PackageManager packageManager = context.getPackageManager();
		
		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> intentActivities = packageManager.queryIntentActivities(mainIntent, 0);
		
		mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
		intentActivities.addAll(packageManager.queryIntentActivities(mainIntent, 0));
				
		Set<String> knownPackages = new HashSet<>();
		List<AppInfo> entries = new ArrayList<>();

		for (ResolveInfo resolveInfo : intentActivities) {
			if (!context.getPackageName().equals(resolveInfo.activityInfo.packageName) &&
					!knownPackages.contains(resolveInfo.activityInfo.packageName)) {
				entries.add(new AppInfo(packageManager, resolveInfo));
				knownPackages.add(resolveInfo.activityInfo.packageName);
			}
		}

		Collections.sort(entries, new Comparator<AppInfo>() {
			@Override
			public int compare(AppInfo lhs, AppInfo rhs) {
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});
		return entries;
	}

	public static int getPixelFromDp(Context context, int dp) {
		Resources r = context.getResources();
		return ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
	}
}
