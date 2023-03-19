package sk.madzik.android.logcatudp;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import java.net.DatagramSocket;
import java.net.SocketException;

public class LogcatUdpService extends Service {
    public static final String TAG = "LogcatUdpService";
    public static boolean isRunning = false;

    static class Config {
        boolean mSendIds;
        String mDevId;
        String mDestServer;
        int mDestPort;
        boolean mUseFilter;
        String mFilter;
        String mLogFormat;
    }

    private DatagramSocket mSocket = null;
    private LogcatThread mLogcatThread = null;
    private NotificationManagerCompat mNotificationManager = null;
    private static final int SERVICE_NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, TAG + " started");

        // get configuration
        Config mConfig = new Config();
        SharedPreferences settings = getSharedPreferences(LogcatUdpCfg.Preferences.PREFS_NAME, Context.MODE_PRIVATE);
        mConfig.mSendIds = settings.getBoolean(LogcatUdpCfg.Preferences.SEND_IDS, false);
        @SuppressLint("HardwareIds") String android_ID = Secure.getString(this.getContentResolver(), Secure.ANDROID_ID);
        if (TextUtils.isEmpty(android_ID)) {
            android_ID = "emulator";
        }
        mConfig.mDevId = settings.getString(LogcatUdpCfg.Preferences.DEV_ID, android_ID);
        mConfig.mDestServer = settings.getString(LogcatUdpCfg.Preferences.DEST_SERVER, LogcatUdpCfg.DEF_SERVER);
        mConfig.mDestPort = settings.getInt(LogcatUdpCfg.Preferences.DEST_PORT, LogcatUdpCfg.DEF_PORT);
        mConfig.mUseFilter = settings.getBoolean(LogcatUdpCfg.Preferences.USE_FILTER, false);
        mConfig.mFilter = settings.getString(LogcatUdpCfg.Preferences.FILTER_TEXT, "");
        mConfig.mLogFormat = settings.getString(LogcatUdpCfg.Preferences.LOG_FORMAT, "");

        try {
            mSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            Log.e(TAG, "Socket creation failed!");
            stopSelf();
        }
        mLogcatThread = new LogcatThread(mSocket, mConfig);
        mLogcatThread.start();

        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // status bar notification icon manager
        mNotificationManager = NotificationManagerCompat.from(getApplicationContext());
        int icon = R.drawable.ic_stat_notif;
        final String channelId = String.valueOf(SERVICE_NOTIFICATION_ID);
        final int importance = NotificationManagerCompat.IMPORTANCE_NONE;
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(channelId, importance).setName(getString(R.string.app_name)).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId).setSmallIcon(icon).setContentTitle(getString(R.string.notif_text)).setContentText(getString(R.string.notif_message)).setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_HIGH);
        Intent resultIntent = new Intent(this, LogcatUdpService.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(LogcatUdpCfg.class);
        stackBuilder.addNextIntent(resultIntent);
        int intentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        intentFlags |= FLAG_IMMUTABLE;
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, intentFlags);
        builder.setContentIntent(resultPendingIntent);
        Notification notification = builder.build();
        try {
            mNotificationManager.notify(SERVICE_NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            Log.d(TAG, "notification rejected " + e);
        }
        startForeground(SERVICE_NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, TAG + " stopping.");
        if (mLogcatThread != null) {
            mLogcatThread.interrupt();
            try {
                mLogcatThread.join(1000);
                if (mLogcatThread.isAlive()) {
                    // TODO: Display "force close/wait" dialog
                    mLogcatThread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.w(TAG, "Joining logcat thread exception.");
            }
        }
        mNotificationManager.cancelAll();
        isRunning = false;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

}
