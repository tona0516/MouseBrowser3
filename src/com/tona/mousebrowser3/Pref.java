package com.tona.mousebrowser3;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Pref extends PreferenceActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.pref);
    }
}