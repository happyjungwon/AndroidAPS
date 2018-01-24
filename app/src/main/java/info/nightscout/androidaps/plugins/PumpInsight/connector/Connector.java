package info.nightscout.androidaps.plugins.PumpInsight.connector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.Log;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpUpdateGui;
import sugar.free.sightparser.handling.ServiceConnectionCallback;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.StatusCallback;
import sugar.free.sightparser.pipeline.Status;

/**
 * Created by jamorham on 23/01/2018.
 *
 * Connects to SightRemote app service using SightParser library
 *
 * SightRemote and SightParser created by Tebbe Ubben
 *
 * Original proof of concept SightProxy by jamorham
 *
 */

public class Connector {

    private static final String TAG = "InsightConnector";
    private static final String COMPANION_APP_PACKAGE = "sugar.free.sightremote";
    private static final String STATUS_RECEIVER = "sugar.free.sightparser.handling.StatusCallback";
    private static volatile Connector instance;
    private volatile SightServiceConnector serviceConnector;
    private volatile Status lastStatus = null;
    private volatile long lastStatusTime = -1;
    private boolean companionAppInstalled = false;
    private StatusCallback statusCallback = new StatusCallback() {
        @Override
        public void onStatusChange(Status status) {

            synchronized (this) {
                log("Status change: " + status);
                lastStatus = status;
                lastStatusTime = tsl();
                switch (status) {
                    // TODO automated reactions to change in status
                }
                MainApp.bus().post(new EventInsightPumpUpdateGui());
            }
        }
    };

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            log("Receiving broadcast!");
            final String str = intent.getStringExtra("STATUS_MESSAGE");
            try {
                statusCallback.onStatusChange(str);
            } catch (RemoteException e) {
                log("Remote exception: " + e);
            }
        }
    };

    private ServiceConnectionCallback connectionCallback = new ServiceConnectionCallback() {
        @Override
        public void onServiceConnected() {
            log("On service connected");
            serviceConnector.connect();
            statusCallback.onStatusChange(safeGetStatus());
        }

        @Override
        public void onServiceDisconnected() {
            log("Disconnected from service");
        }
    };

    private Connector() {
        registerReceiver();
    }

    public static Connector get() {
        if (instance == null) {
            init_instance();
        }
        return instance;
    }

    private synchronized static void init_instance() {
        if (instance == null) {
            instance = new Connector();
        }
    }

    private static long tsl() {
        return System.currentTimeMillis();
    }

    private static boolean isCompanionAppInstalled() {
        return checkPackageExists(MainApp.instance(), COMPANION_APP_PACKAGE);
    }

    private static boolean checkPackageExists(Context context, String packageName) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi.packageName.equals(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            Log.wtf(TAG, "Exception trying to determine packages! " + e);
            return false;
        }
    }

    public static void connectToPump() {
        log("Attempting to connect to pump");
        get().getServiceConnector().connect();
    }

    private static void log(String msg) {
        android.util.Log.e("PUMPPUMP", msg);
    }

    private void registerReceiver() {
        try {
            MainApp.instance().unregisterReceiver(statusReceiver);
        } catch (Exception e) {
            //
        }
        MainApp.instance().registerReceiver(statusReceiver, new IntentFilter(STATUS_RECEIVER));
    }

    public synchronized void init() {
        log("init");
        if (serviceConnector == null) {
            companionAppInstalled = isCompanionAppInstalled();
            if (companionAppInstalled) {
                serviceConnector = new SightServiceConnector(MainApp.instance());
                serviceConnector.addStatusCallback(statusCallback);
                serviceConnector.setConnectionCallback(connectionCallback);
                serviceConnector.connectToService();
                log("Trying to connect");
            } else {
                log("Not trying init due to missing companion app");
            }
        }
    }

    public SightServiceConnector getServiceConnector() {
        init();
        return serviceConnector;
    }

    public String getCurrent() {
        init();
        return safeGetStatus().toString();
    }

    public Status safeGetStatus() {
        if (isConnected()) return serviceConnector.getStatus();
        return Status.DISCONNECTED;
    }

    public Status getLastStatus() {
        return lastStatus;
    }

    public boolean isConnected() {
        return serviceConnector != null && serviceConnector.isConnectedToService();
    }

    public boolean isPumpConnected() {
        //return isConnected() && serviceConnector.isUseable();
        return isConnected() && getLastStatus() == Status.CONNECTED;
    }

    public String getLastStatusMessage() {

        if (!companionAppInstalled) {
            return "Companion app does not appear to be installed!";
        }

        if (!isConnected()) {
            return "Not connected to companion app!";
        }

        if (lastStatus == null) {
            return "Unknown";
        }

        switch (lastStatus) {
            default:
                return lastStatus.toString();
        }
    }

    public boolean lastStatusRecent() {
        return true; // TODO evaluate whether current
    }

}
