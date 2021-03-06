
package com.veken0m.bitcoinium;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.google.analytics.tracking.android.EasyTracker;

public class MinerWidgetConfigureActivity extends PreferenceActivity {

    static final String REFRESH = "com.veken0m.bitcoinium.REFRESH";
    private static final String PREFS_NAME = "com.veken0m.bitcoinium.MinerWidgetProvider";
    private static final String PREF_MININGPOOL_KEY = "miningpool_";
    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    public MinerWidgetConfigureActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_miner_widget);
        addPreferencesFromResource(R.xml.pref_miner);
        addPreferencesFromResource(R.xml.pref_widgets);
        addPreferencesFromResource(R.xml.ok_button);

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if they press the back button.
        setResult(RESULT_CANCELED);

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null)
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

        // If they gave us an intent without the widget id, just bail.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            finish();

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());

        Preference OKpref = findPreference("OKpref");

        if (OKpref != null) {
            OKpref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final Context context = MinerWidgetConfigureActivity.this;

                    String pref_widgetMiningPool = prefs.getString(
                            "widgetMiningPoolPref", getString(R.string.default_miningpool));

                    saveMiningPoolPref(context, mAppWidgetId, pref_widgetMiningPool);

                    // Make sure we pass back the original appWidgetId
                    Intent resultValue = new Intent();
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            mAppWidgetId);
                    setResult(RESULT_OK, resultValue);

                    finish();
                    return true;
                }

            });
        }

    }

    // Write the prefix to the SharedPreferences object for this widget
    private static void saveMiningPoolPref(Context context, int appWidgetId,
                                           String miningPool) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(
                PREFS_NAME, 0).edit();
        prefs.putString(PREF_MININGPOOL_KEY + appWidgetId, miningPool);
        prefs.commit();
    }

    // Read the prefix from the SharedPreferences object for this widget.
    // If there is no preference saved, get the default from a resource
    static String loadMiningPoolPref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_MININGPOOL_KEY + appWidgetId, null);

    }

    @Override
    public void onStart() {
        super.onStart();
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("googleAnalyticsPref", false)) {
            EasyTracker.getInstance(this).activityStart(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Intent intent = new Intent(this, MinerWidgetProvider.class);
        intent.setAction(REFRESH);
        this.sendBroadcast(intent);

        EasyTracker.getInstance(this).activityStop(this);
    }

}
