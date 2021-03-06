
package com.veken0m.bitcoinium;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veken0m.mining.bitminter.BitMinterData;
import com.veken0m.mining.btcguild.BTCGuild;
import com.veken0m.mining.deepbit.DeepBitData;
import com.veken0m.mining.eligius.Eligius;
import com.veken0m.mining.eligius.EligiusBalance;
import com.veken0m.mining.emc.EMC;
import com.veken0m.mining.fiftybtc.FiftyBTC;
import com.veken0m.mining.fiftybtc.Worker;
import com.veken0m.mining.slush.Slush;
import com.veken0m.mining.slush.Workers;
import com.veken0m.utils.CurrencyUtils;
import com.veken0m.utils.Utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.InputStreamReader;
import java.util.List;

//import com.veken0m.utils.KarmaAdsUtils;

public class MinerWidgetProvider extends BaseWidgetProvider {

    private static float hashRate = 0.0F;
    private static float btcBalance = 0.0F;

    @Override
    public void onReceive(Context context, Intent intent) {

        if (REFRESH.equals(intent.getAction()))
            onUpdate(context, null, null);

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        setMinerWidgetAlarm(context);
    }

    /**
     * This class lets us refresh the widget whenever we want to
     */
    public static class MinerUpdateService extends IntentService {

        public void buildUpdate() {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
            ComponentName widgetComponent = new ComponentName(this, MinerWidgetProvider.class);
            int[] widgetIds = new int[0];
            if (widgetManager != null)
                widgetIds = widgetManager.getAppWidgetIds(widgetComponent);

            readGeneralPreferences(this);

            if (widgetIds.length > 0 && (!pref_wifiOnly || checkWiFiConnected(this))) {

                for (int appWidgetId : widgetIds) {

                    // Load Widget configuration
                    String miningPool = MinerWidgetConfigureActivity.loadMiningPoolPref(this,
                            appWidgetId);

                    if (miningPool == null) continue; // skip to next widget

                    RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.minerappwidget);
                    setTapBehaviour(appWidgetId, miningPool, views);

                    Boolean pref_minerDownAlert = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            miningPool.toLowerCase() + "AlertPref", false);

                    if (getMinerInfo(miningPool)) {

                        views.setTextViewText(R.id.widgetMinerHashrate, Utils.formatHashrate(hashRate));
                        views.setTextViewText(R.id.widgetMiner, miningPool);
                        views.setTextViewText(R.id.widgetBTCPayout,
                                CurrencyUtils.formatPayout(btcBalance, pref_widgetMiningPayoutUnit));

                        if ((hashRate < 0.01) && pref_minerDownAlert)
                            createMinerDownNotification(this, miningPool);

                        String refreshedTime = "Upd. @ " + Utils.getCurrentTime(this);
                        views.setTextViewText(R.id.refreshtime, refreshedTime);

                        updateWidgetTheme(views);

                    } else {
                        if (pref_enableWidgetCustomization)
                            views.setTextColor(R.id.refreshtime, pref_widgetRefreshFailedColor);
                        else
                            views.setTextColor(R.id.refreshtime, Color.RED);
                    }
                    if (widgetManager != null) widgetManager.updateAppWidget(appWidgetId, views);
                }
            }
        }

        public Boolean getMinerInfo(String sMiningPool) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            HttpClient client = new DefaultHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // reset variables
            btcBalance = 0;
            hashRate = 0;

            try {
                // TODO: fix this ugly mess
                if (sMiningPool.equalsIgnoreCase("DeepBit")) {
                    String pref_apiKey = prefs.getString("deepbitKey", "");

                    HttpGet post = new HttpGet("http://deepbit.net/api/"
                            + pref_apiKey);

                    HttpResponse response = client.execute(post);
                    DeepBitData data = mapper.readValue(new InputStreamReader(response
                            .getEntity().getContent(), "UTF-8"),
                            DeepBitData.class);
                    btcBalance = data.getConfirmed_reward();
                    hashRate = data.getHashrate();

                    return true;

                } else if (sMiningPool.equalsIgnoreCase("BitMinter")) {
                    String pref_apiKey = prefs.getString("bitminterKey", "");

                    HttpGet post = new HttpGet(
                            "https://bitminter.com/api/users" + "?key="
                                    + pref_apiKey);

                    HttpResponse response = client.execute(post);
                    BitMinterData data = mapper.readValue(new InputStreamReader(response
                            .getEntity().getContent(), "UTF-8"),
                            BitMinterData.class);
                    btcBalance = data.getBalances().getBTC();
                    hashRate = data.getHash_rate();
                    return true;

                } else if (sMiningPool.equalsIgnoreCase("EclipseMC")) {

                    String pref_apiKey = prefs.getString("emcKey", "");
                    HttpGet post = new HttpGet(
                            "https://eclipsemc.com/api.php?key=" + pref_apiKey
                                    + "&action=userstats");

                    HttpResponse response = client.execute(post);
                    EMC data = mapper.readValue(new InputStreamReader(response
                            .getEntity().getContent(), "UTF-8"), EMC.class);

                    btcBalance = data.getData().getUser()
                            .getConfirmed_rewards();

                    for (int i = 0; i < data.getWorkers().size(); i++) {
                        String hashRateString = data.getWorkers().get(i).getHash_rate();
                        // EclipseMC hashrate contains units. Strip them off
                        // And convert all GH/s to MH/s
                        float temp_hashRate;
                        if (!hashRateString.contentEquals(" ")) {
                            String hash_rate[] = hashRateString.split(" ");
                            temp_hashRate = Float.parseFloat(hash_rate[0]);
                            if (hash_rate[1].contains("G")) {
                                temp_hashRate *= 1000;
                            }
                        } else {
                            // empty hashrate, set to 0;
                            temp_hashRate = 0;
                        }
                        hashRate += temp_hashRate;
                    }
                    return true;

                } else if (sMiningPool.equalsIgnoreCase("Slush")) {
                    String pref_apiKey = prefs.getString("slushKey", "");

                    HttpGet post = new HttpGet(
                            "https://mining.bitcoin.cz/accounts/profile/json/"
                                    + pref_apiKey);

                    HttpResponse response = client.execute(post);
                    Slush data = mapper.readValue(new InputStreamReader(response
                            .getEntity().getContent(), "UTF-8"), Slush.class);
                    btcBalance = data.getConfirmed_reward();

                    Workers workers = data.getWorkers();

                    for (int i = 0; i < workers.getWorkers().size(); i++) {
                        hashRate += workers.getWorker(i).getHashrate();
                    }
                    return true;

                } else if (sMiningPool.equalsIgnoreCase("50BTC")) {
                    String pref_apiKey = prefs.getString("50BTCKey", "");

                    HttpGet post = new HttpGet("https://50btc.com/en/api/"
                            + pref_apiKey + "?text=1");
                    HttpResponse response = client.execute(post);
                    FiftyBTC data = mapper
                            .readValue(new InputStreamReader(response
                                    .getEntity().getContent(), "UTF-8"),
                                    FiftyBTC.class);
                    btcBalance = data.getUser().getConfirmed_rewards();
                    hashRate = 0.0f;

                    List<Worker> workers = data.getWorkers().getWorkers();
                    for (Worker worker : workers) {
                        hashRate += Float.parseFloat(worker.getHash_rate());
                    }
                    return true;

                } else if (sMiningPool.equalsIgnoreCase("BTCGuild")) {
                    String pref_apiKey = prefs.getString("btcguildKey", "");

                    HttpGet post = new HttpGet("https://www.btcguild.com/api.php?api_key="
                            + pref_apiKey);
                    HttpResponse response = client.execute(post);
                    BTCGuild data = mapper
                            .readValue(new InputStreamReader(response
                                    .getEntity().getContent(), "UTF-8"),
                                    BTCGuild.class);
                    btcBalance = data.getUser().getUnpaid_rewards();
                    hashRate = 0.0f;

                    List<com.veken0m.mining.btcguild.Worker> workers = data.getWorkers()
                            .getWorkers();
                    for (com.veken0m.mining.btcguild.Worker worker : workers) {
                        hashRate += worker.getHash_rate();
                    }
                    return true;
                } else if (sMiningPool.equalsIgnoreCase("Eligius")) {

                    String pref_apiKey = prefs.getString("eligiusKey", "");

                    HttpGet post = new HttpGet(
                            "http://eligius.st/~wizkid057/newstats/hashrate-json.php/"
                                    + pref_apiKey);
                    HttpResponse response = client.execute(post);
                    mapper.setSerializationInclusion(Include.NON_NULL);

                    Eligius data = mapper
                            .readValue(new InputStreamReader(response
                                    .getEntity().getContent(), "UTF-8"),
                                    Eligius.class);

                    hashRate = data.get256().getHashrate() / 1000000;

                    post = new HttpGet("http://eligius.st/~luke-jr/balance.php?addr="
                            + pref_apiKey);

                    EligiusBalance data2 = mapper
                            .readValue(new InputStreamReader(client.execute(post)
                                    .getEntity().getContent(), "UTF-8"),
                                    EligiusBalance.class);

                    btcBalance = data2.getConfirmed() / 100000000;

                    return true;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return false;
        }

        private void setTapBehaviour(int appWidgetId, String poolKey, RemoteViews views) {

            PendingIntent pendingIntent;
            if (pref_tapToUpdate) {
                Intent intent = new Intent(this, MinerWidgetProvider.class);
                intent.setAction(REFRESH);
                pendingIntent = PendingIntent.getBroadcast(this, appWidgetId, intent, 0);
            } else {
                Intent intent = new Intent(this, MinerStatsActivity.class);
                Bundle tabSelection = new Bundle();
                tabSelection.putString("poolKey", poolKey);
                intent.putExtras(tabSelection);
                pendingIntent = PendingIntent.getActivity(
                        this, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            views.setOnClickPendingIntent(R.id.widgetMinerButton,
                    pendingIntent);
        }

        public void updateWidgetTheme(RemoteViews views) {
            // set the color
            if (pref_enableWidgetCustomization) {
                views.setInt(R.id.minerwidget_layout, "setBackgroundColor", pref_backgroundWidgetColor);
                views.setTextColor(R.id.widgetMinerHashrate, pref_mainWidgetTextColor);
                views.setTextColor(R.id.widgetMiner, pref_mainWidgetTextColor);
                views.setTextColor(R.id.refreshtime, pref_widgetRefreshSuccessColor);
                views.setTextColor(R.id.widgetBTCPayout, pref_secondaryWidgetTextColor);
            } else {
                views.setInt(R.id.minerwidget_layout,"setBackgroundColor", getResources().getColor(R.color.widgetBackgroundColor));
                views.setTextColor(R.id.widgetMinerHashrate, getResources().getColor(R.color.widgetMainTextColor));
                views.setTextColor(R.id.widgetMiner, getResources().getColor(R.color.widgetMainTextColor));
                views.setTextColor(R.id.widgetBTCPayout, Color.LTGRAY);
                views.setTextColor(R.id.refreshtime, Color.GREEN);
            }
        }

        public MinerUpdateService() {
            super("MinerWidgetProvider$MinerUpdateService");
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            super.onStartCommand(intent, flags, startId);
            return START_STICKY;
        }

        @Override
        public void onHandleIntent(Intent intent) {
            buildUpdate();
        }
    }
}
