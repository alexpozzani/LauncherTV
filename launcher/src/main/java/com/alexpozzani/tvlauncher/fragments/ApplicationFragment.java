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

package com.alexpozzani.tvlauncher.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alexpozzani.tvlauncher.R;
import com.alexpozzani.tvlauncher.AppInfo;
import com.alexpozzani.tvlauncher.Setup;
import com.alexpozzani.tvlauncher.Utils;
import com.alexpozzani.tvlauncher.activities.ApplicationList;
import com.alexpozzani.tvlauncher.views.ApplicationView;
import com.alexpozzani.tvlauncher.activities.Preferences;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

@SuppressWarnings("PointlessBooleanExpression")
public class ApplicationFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
	public static final String TAG = "ApplicationFragment";
	private static final String PREFERENCES_NAME = "applications";
	private static final int REQUEST_CODE_APPLICATION_LIST = 0x1E;
	private static final int REQUEST_CODE_WALLPAPER = 0x1F;
	private static final int REQUEST_CODE_APPLICATION_START = 0x20;
	private static final int REQUEST_CODE_PREFERENCES = 0x21;

	private static final String[] DEFAULT_APPS = {
			"com.android.settings",
			"com.android.tv.settings",
			"com.android.vending",
			"com.android.chrome",
			"com.google.android.youtube",
			"com.alexpozzani.paineldigital",
	};

	private TextView mClock;
	private TextView mDate;
	private DateFormat mTimeFormat;
	private DateFormat mDateFormat;
	private TextView mBatteryLevel;
	private ImageView mBatteryIcon;
	private BroadcastReceiver mBatteryChangedReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent) {
			final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			mBatteryLevel.setText(
					String.format(getResources().getString(R.string.battery_level_text), level)
			);
			final int batteryIconId = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, 0);
			mBatteryIcon.setImageDrawable(getResources().getDrawable(batteryIconId, null));
		}
	};
	private boolean mBatteryChangedReceiverRegistered = false;

	private final Handler mHandler = new Handler();
	private final Runnable mTimerTick = new Runnable() {
		@Override
		public void run() {
			setClock();
		}
	};

	private int mGridX = 5;
	private int mGridY = 3;
	private LinearLayout mContainer;
	private ApplicationView[][] mApplications = null;
	private View mSettings;
	private View mGridView;
	private Setup mSetup;


	public ApplicationFragment() {
		// Required empty public constructor
	}

	public static ApplicationFragment newInstance() {
		return new ApplicationFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_application, container, false);

		mSetup = new Setup(getContext());
		mContainer = (LinearLayout) view.findViewById(R.id.container);
		mSettings = view.findViewById(R.id.settings);
		mGridView = view.findViewById(R.id.application_grid);
		mClock = (TextView) view.findViewById(R.id.clock);
		mDate = (TextView) view.findViewById(R.id.date);
		final LinearLayout batteryLayout = (LinearLayout) view.findViewById(R.id.battery_layout);
		mBatteryLevel = (TextView) view.findViewById(R.id.battery_level);
		mBatteryIcon = (ImageView) view.findViewById(R.id.battery_icon);

		mTimeFormat = android.text.format.DateFormat.getTimeFormat(getActivity());
		mDateFormat = android.text.format.DateFormat.getLongDateFormat(getActivity());

		//keep screen on
		mContainer.setKeepScreenOn(mSetup.keepScreenOn());

		//show date
		mDate.setVisibility(mSetup.showDate() ? View.VISIBLE : View.GONE);

		if (mSetup.showBattery()) {
			batteryLayout.setVisibility(View.VISIBLE);
			getActivity().registerReceiver(this.mBatteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			mBatteryChangedReceiverRegistered = true;
		} else {
			batteryLayout.setVisibility(View.INVISIBLE);
			if (mBatteryChangedReceiverRegistered) {
				getActivity().unregisterReceiver(this.mBatteryChangedReceiver);
				mBatteryChangedReceiverRegistered = false;
			}
		}

		mSettings.setOnClickListener(this);
		mGridView.setOnClickListener(this);

		createApplications();

		//check if it's the first time the app is launched, if so, add some default apps
		Setup setup = new Setup(getContext());
		if (setup.isFirstLaunch()) {
			setup.setFirstLaunchDone();
			addDefaultApps();
		}

		return view;
	}

	private void createApplications() {
		mContainer.removeAllViews();

		mGridX = mSetup.getGridX();
		mGridY = mSetup.getGridY();

		if (mGridX < 2)
			mGridX = 2;
		if (mGridY < 1)
			mGridY = 1;

		int marginX = Utils.getPixelFromDp(getContext(), mSetup.getMarginX());
		int marginY = Utils.getPixelFromDp(getContext(), mSetup.getMarginY());

		boolean showNames = mSetup.showNames();

		mApplications = new ApplicationView[mGridY][mGridX];

		int position = 0;
		for (int y = 0; y < mGridY; y++) {
			LinearLayout ll = new LinearLayout(getContext());
			ll.setOrientation(LinearLayout.HORIZONTAL);
			ll.setGravity(Gravity.CENTER_VERTICAL);
			ll.setFocusable(false);
			ll.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
			));

			for (int x = 0; x < mGridX; x++) {
				ApplicationView av = new ApplicationView(getContext());
				av.setOnClickListener(this);
				av.setOnLongClickListener(this);
				av.setOnMenuOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onLongClick(v);
					}
				});
				av.setPosition(position++);
				av.showName(showNames);
				av.setId(View.generateViewId());
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
				lp.setMargins(marginX, marginY, marginX, marginY);
				av.setLayoutParams(lp);
				ll.addView(av);
				mApplications[y][x] = av;
			}
			mContainer.addView(ll);
		}

		updateApplications();
		setApplicationOrder();
	}

	private void setApplicationOrder() {
		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				int upId = R.id.application_grid;
				int downId = R.id.settings;
				int leftId = R.id.application_grid;
				int rightId = R.id.settings;

				if (y > 0)
					upId = mApplications[y - 1][x].getId();

				if (y + 1 < mGridY)
					downId = mApplications[y + 1][x].getId();

				if (x > 0)
					leftId = mApplications[y][x - 1].getId();
				else if (y > 0)
					leftId = mApplications[y - 1][mGridX - 1].getId();

				if (x + 1 < mGridX)
					rightId = mApplications[y][x + 1].getId();
				else if (y + 1 < mGridY)
					rightId = mApplications[y + 1][0].getId();

				mApplications[y][x].setNextFocusLeftId(leftId);
				mApplications[y][x].setNextFocusRightId(rightId);
				mApplications[y][x].setNextFocusUpId(upId);
				mApplications[y][x].setNextFocusDownId(downId);
			}
		}

		mGridView.setNextFocusLeftId(R.id.settings);
		mGridView.setNextFocusRightId(mApplications[0][0].getId());
		mGridView.setNextFocusUpId(R.id.settings);
		mGridView.setNextFocusDownId(mApplications[0][0].getId());

		mSettings.setNextFocusLeftId(mApplications[mGridY - 1][mGridX - 1].getId());
		mSettings.setNextFocusRightId(R.id.application_grid);
		mSettings.setNextFocusUpId(mApplications[mGridY - 1][mGridX - 1].getId());
		mSettings.setNextFocusDownId(R.id.application_grid);
	}


	private void updateApplications() {
		PackageManager pm = getActivity().getPackageManager();
		SharedPreferences prefs = getActivity().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				ApplicationView app = mApplications[y][x];
				setApplication(pm, app, prefs.getString(app.getPreferenceKey(), null));
			}
		}
	}

	private void restartActivity() {
		if (mBatteryChangedReceiverRegistered) {
			getActivity().unregisterReceiver(mBatteryChangedReceiver);
			mBatteryChangedReceiverRegistered = false;
		}
		Intent intent = getActivity().getIntent();
		getActivity().finish();
		startActivity(intent);
	}


	private void writePreferences(int appNum, String packageName) {
		SharedPreferences prefs = getActivity().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		String key = ApplicationView.getPreferenceKey(appNum);

		if (TextUtils.isEmpty(packageName))
			editor.remove(key);
		else
			editor.putString(key, packageName);

		editor.apply();
	}

	private void setApplication(PackageManager pm, ApplicationView app, String packageName) {
		try {

			if (TextUtils.isEmpty(packageName) == false) {
				PackageInfo pi = pm.getPackageInfo(packageName, 0);
				if (pi != null) {
					AppInfo appInfo = new AppInfo(pm, getResolveInfo(pm, pi.applicationInfo));
					app.setImageDrawable(appInfo.getIcon())
							.setText(appInfo.getName())
							.setPackageName(appInfo.getPackageName());
				}
			} else {
				app.setImageResource(R.drawable.ic_add)
						.setText("")
						.setPackageName(null);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//create a method to get ResolveInfo from ApplicationInfo
	private ResolveInfo getResolveInfo(PackageManager pm, ApplicationInfo applicationInfo) {
		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

		List<ResolveInfo> intentActivities = pm.queryIntentActivities(mainIntent, 0);

		for (ResolveInfo resolveInfo : intentActivities) {
			if (resolveInfo.activityInfo.packageName.equals(applicationInfo.packageName)) {
				return resolveInfo;
			}
		}

		mainIntent = new Intent(Intent.ACTION_MAIN, null);
		intentActivities = pm.queryIntentActivities(mainIntent, 0);

		for (ResolveInfo resolveInfo : intentActivities) {
			if (resolveInfo.activityInfo.packageName.equals(applicationInfo.packageName)) {
				return resolveInfo;
			}
		}

		return null;
	}

	@Override
	public void onStart() {
		super.onStart();
		setClock();
		if (mSetup.showBattery() && !mBatteryChangedReceiverRegistered) {
			getActivity().registerReceiver(this.mBatteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			mBatteryChangedReceiverRegistered = true;
		}
		mHandler.postDelayed(mTimerTick, 1000);
	}

	@Override
	public void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mTimerTick);
		if (mBatteryChangedReceiverRegistered) {
			getActivity().unregisterReceiver(this.mBatteryChangedReceiver);
		}
	}

	private void setClock() {
		Date date = new Date(System.currentTimeMillis());
		mClock.setText(mTimeFormat.format(date));
		mDate.setText(mDateFormat.format(date));
		mHandler.postDelayed(mTimerTick, 1000);
	}

	@Override
	public boolean onLongClick(View v) {
		if (v instanceof ApplicationView) {
			ApplicationView appView = (ApplicationView) v;
			if (appView.hasPackage() && mSetup.iconsLocked()) {
				Toast.makeText(getActivity(), R.string.home_locked, Toast.LENGTH_SHORT).show();
			} else {
				openApplicationList(ApplicationList.VIEW_LIST, appView.getPosition(), appView.hasPackage(), REQUEST_CODE_APPLICATION_LIST);
			}
			return (true);
		}
		return (false);
	}

	@Override
	public void onClick(View v) {
		if (v instanceof ApplicationView) {
			openApplication((ApplicationView) v);
		}
		else {
			switch (v.getId()) {
				case R.id.application_grid: {
					openApplicationList(ApplicationList.VIEW_GRID, 0, false, REQUEST_CODE_APPLICATION_START);
				}
				break;

				case R.id.settings:
					startActivityForResult(new Intent(getContext(), Preferences.class), REQUEST_CODE_PREFERENCES);
					break;
			}

		}
	}

	private void openApplication(ApplicationView v) {
		if (v.hasPackage() == false) {
			openApplicationList(ApplicationList.VIEW_LIST, v.getPosition(), false, REQUEST_CODE_APPLICATION_LIST);
		} else {
			openApplication(v.getPackageName());
		}
	}

	private void openApplication(String packageName) {
		try {
			Log.i(TAG, "openApplication: " + packageName);
			Intent startApp = getLaunchIntentForPackage(packageName);
			startActivity(startApp);
		} catch (Exception e) {
			Toast.makeText(getActivity(), packageName + " : " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void openApplicationList(int viewType, int appNum, boolean showDelete, int requestCode) {
		Intent intent = new Intent(getActivity(), ApplicationList.class);
		intent.putExtra(ApplicationList.APPLICATION_NUMBER, appNum);
		intent.putExtra(ApplicationList.VIEW_TYPE, viewType);
		intent.putExtra(ApplicationList.SHOW_DELETE, showDelete);
		startActivityForResult(intent, requestCode);
	}
	
	private Intent getLaunchIntentForPackage(String packageName) {
		PackageManager pm = getActivity().getPackageManager();
		Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
		
		if(launchIntent == null) {
			launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName);
		}
		
		return launchIntent;			
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch (requestCode) {
			case REQUEST_CODE_WALLPAPER:
				break;
			case REQUEST_CODE_PREFERENCES:
				restartActivity();
				break;
			case REQUEST_CODE_APPLICATION_START:
				if (intent != null)
					openApplication(intent.getExtras().getString(ApplicationList.PACKAGE_NAME));
				break;
			case REQUEST_CODE_APPLICATION_LIST:
				if (resultCode == Activity.RESULT_OK) {
					Bundle extra = intent.getExtras();
					int appNum = intent.getExtras().getInt(ApplicationList.APPLICATION_NUMBER);

					if (extra.containsKey(ApplicationList.DELETE) && extra.getBoolean(ApplicationList.DELETE)) {
						writePreferences(appNum, null);
					} else {
						writePreferences(appNum,
								intent.getExtras().getString(ApplicationList.PACKAGE_NAME)
						);
					}
					updateApplications();
				}
				break;
		}
	}

	private void addDefaultApps() {
		Executors.newSingleThreadExecutor().execute(() -> {

			Handler mainHandler = new Handler(Looper.getMainLooper());

			PackageManager pm = getActivity().getPackageManager();
			int currentApp = 0;

			for (int i = 0; i < DEFAULT_APPS.length; i++) {
				try {
					PackageInfo pi = pm.getPackageInfo(DEFAULT_APPS[i], 0);

					if (pi != null && getLaunchIntentForPackage(pi.packageName) != null) {
						writePreferences(currentApp, pi.packageName);
						currentApp++;
					}
				} catch (PackageManager.NameNotFoundException e) {
					e.printStackTrace();
				}
			}

			mainHandler.post(() -> {
				updateApplications();
			});

		});
	}
}
