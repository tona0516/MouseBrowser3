package com.tona.mousebrowser3;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.widget.Toast;

public class GeneralPref extends PreferenceActivity {
	/** Called when the activity is first created. */
	private static SharedPreferences sp;
	private static EditTextPreference home;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.general_pref);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		home = (EditTextPreference) findPreference("homepage");
		home.setText(sp.getString("homepage", MainActivity.DEFAULT_HOME));
		home.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String text = (String) newValue;
				if (text.startsWith("http://") || text.startsWith("https://")) {
					sp.edit().putString("homepage", text).commit();
				} else {
					Toast.makeText(getApplicationContext(), "不正なURLです", Toast.LENGTH_SHORT).show();
				}
				return false;
			}
		});
	}
}
