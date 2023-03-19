package sk.madzik.android.logcatudp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class LogcatUdpCfg extends AppCompatActivity {
    public static final String TAG = LogcatUdpCfg.class.getSimpleName();

    private static final int MENU_SAVE = Menu.FIRST + 1;
    private static final int MENU_CANCEL = Menu.FIRST + 2;
    private static final int MENU_CLR_LOG = Menu.FIRST + 3;
    private static final int MENU_SHARE = Menu.FIRST + 4;

    static final String DEF_SERVER = "192.168.1.10";
    static final int DEF_PORT = 10009;

    static final String DEF_FORMAT = "brief";

    private boolean cancelSave = false;

    private SharedPreferences mSettings;

    private CheckBox chkSendIds;
    private EditText txtDevId;
    private EditText txtServer;
    private EditText txtPort;
    private CheckBox chkUseFilter;
    private EditText txtFilter;
    private CheckBox chkAutoStart;

    private Button btnGrantLogs;

    private Spinner spinLogFormat;

    ProgressDialog prgDialog;
    private String androidID;

    public static class Preferences {
        public static final String PREFS_NAME = "LogcatUdp";
        public static final String SEND_IDS = "SendIds";
        public static final String DEV_ID = "DeviceID";
        public static final String DEST_SERVER = "DestServer";
        public static final String DEST_PORT = "DestPort";
        public static final String AUTO_START = "AutoStart";
        public static final String USE_FILTER = "UseFilter";
        public static final String FILTER_TEXT = "FilterText";
        public static final String LOG_FORMAT = "LogFormat";
    }

    @SuppressLint("HardwareIds")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "started");
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setContentView(R.layout.main);
        androidID = Secure.getString(LogcatUdpCfg.this.getContentResolver(), Secure.ANDROID_ID);
        if (TextUtils.isEmpty(androidID)) {
            androidID = "emulator";
        }

        chkSendIds = findViewById(R.id.chkSendIds);
        txtDevId = findViewById(R.id.txtID);
        txtServer = findViewById(R.id.txtServer);
        txtPort = findViewById(R.id.txtPort);
        spinLogFormat = findViewById(R.id.spinLogFormat);
        chkUseFilter = findViewById(R.id.chkUseFilter);
        txtFilter = findViewById(R.id.txtFilter);
        chkAutoStart = findViewById(R.id.chkAutoStart);

        mSettings = getSharedPreferences(Preferences.PREFS_NAME, MODE_PRIVATE);

        // set send ID (un)checked
        chkSendIds.setChecked(mSettings.getBoolean(Preferences.SEND_IDS, false));
        // enable/disable ID editbox
        if (!chkSendIds.isChecked()) {
            findViewById(R.id.lblID).setVisibility(View.GONE);
            txtDevId.setVisibility(View.GONE);
        }
        chkSendIds.setOnCheckedChangeListener((buttonView, isChecked) -> {
            findViewById(R.id.lblID).setVisibility((isChecked ? View.VISIBLE : View.GONE));
            txtDevId.setVisibility((isChecked ? View.VISIBLE : View.GONE));
        });

        // set text in ID editbox
        txtDevId.setText(mSettings.getString(Preferences.DEV_ID, androidID));

        txtServer.setText(mSettings.getString(Preferences.DEST_SERVER, DEF_SERVER));
        txtPort.setText(String.valueOf(mSettings.getInt(Preferences.DEST_PORT, DEF_PORT)));
        chkAutoStart.setChecked(mSettings.getBoolean(Preferences.AUTO_START, true));

        // set log format
        spinLogFormat.setSelection(getLogFormatIndex(mSettings.getString(Preferences.LOG_FORMAT, DEF_FORMAT)));

        // set Filter log (un)checked
        chkUseFilter.setChecked(mSettings.getBoolean(Preferences.USE_FILTER, false));
        // enable/disable Filter editbox
        if (!chkUseFilter.isChecked()) {
            findViewById(R.id.lblFilter).setVisibility(View.GONE);
            txtFilter.setVisibility(View.GONE);
        }
        chkUseFilter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            findViewById(R.id.lblFilter).setVisibility((isChecked ? View.VISIBLE : View.GONE));
            txtFilter.setVisibility((isChecked ? View.VISIBLE : View.GONE));
        });

        // set text in filter editbox
        txtFilter.setText(mSettings.getString(Preferences.FILTER_TEXT, ""));

        Button btnActivateService = findViewById(R.id.activateServiceBtn);
        btnActivateService.setOnClickListener(v -> {
            stopService();
            saveSettings();
            startService();
            Toast.makeText(LogcatUdpCfg.this, "LogcatUdp service (re)started", Toast.LENGTH_SHORT).show();
        });

        Button btnDeactivateService = findViewById(R.id.deactivateServiceBtn);
        btnDeactivateService.setOnClickListener(v -> {
            if (stopService()) {
                Toast.makeText(LogcatUdpCfg.this, "LogcatUdp service stopped", Toast.LENGTH_SHORT).show();
            }
        });

        btnGrantLogs = findViewById(R.id.grantPermissionBtn);
        btnGrantLogs.setOnClickListener(view -> {
            Context context = getApplicationContext();
            RootUtils.setReadLogsPermission(context);
            if (RootUtils.haveReadLogsPermission(context)) {
                btnGrantLogs.setEnabled(false);
            }
        });
        if (!RootUtils.haveReadLogsPermission(getApplicationContext()) && RootUtils.isDeviceRooted()) {
            btnGrantLogs.setEnabled(true);
        }

        Log.d(TAG, "created");
    }

    @Override
    public void onPause() {
        Log.d(TAG, "Pause cfg dialog");
        super.onPause();
        if (!cancelSave) {
            saveSettings();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(0, MENU_SAVE, 0, getString(R.string.mnuSave));
        item.setIcon(android.R.drawable.ic_menu_save);

        MenuItem mnuClose = menu.add(0, MENU_CANCEL, 0, getString(R.string.mnuClose));
        mnuClose.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        MenuItem mnuClear = menu.add(0, MENU_CLR_LOG, 0, getString(R.string.mnuClear));
        mnuClear.setIcon(android.R.drawable.ic_menu_delete);

        MenuItem mnuShare = menu.add(0, MENU_SHARE, 0, getString(R.string.mnuShare));
        mnuShare.setIcon(android.R.drawable.ic_menu_share);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SAVE:
                saveSettings();
                break;
            case MENU_CANCEL:
                /*boolean stopped = */
                stopService();
                cancelSave = true;
                finish();
                break;
            case MENU_CLR_LOG:
                try {
                    Runtime.getRuntime().exec("logcat -c");
                    Log.i(TAG, "Log cleared!");
                } catch (IOException e) {
                    Log.e(TAG, "Clearing log failed!");
                    e.printStackTrace();
                }
                break;
            case MENU_SHARE:
                prgDialog = ProgressDialog.show(this, "", "Loading log. Please wait...", true);
                Thread checkUpdate = new Thread() {
                    public void run() {
                        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Logcat from phone: " + androidID);
                        try {
                            Process process = Runtime.getRuntime().exec("logcat -d -t 1000");
                            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            StringBuilder extraText = new StringBuilder();
                            String ln_str;
                            while ((ln_str = reader.readLine()) != null) {
                                extraText.append(ln_str).append(System.getProperty("line.separator"));
                            }
                            Log.d(TAG, "Text size: " + extraText.toString().length());
                            intent.putExtra(Intent.EXTRA_TEXT, extraText.toString());
                            startActivity(Intent.createChooser(intent, "How do you want to share?"));
                        } catch (IOException e) {
                            Log.e(TAG, "Sharing log failed!");
                            e.printStackTrace();
                        }
                        handler.sendEmptyMessage(0);
                    }
                };
                checkUpdate.start();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    final Handler handler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            prgDialog.dismiss();
        }
    };

    private void startService() {
        Intent serviceIntent = new Intent(LogcatUdpCfg.this, LogcatUdpService.class);
        Log.d(TAG, "Start service");
        ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);
    }

    private boolean stopService() {
        Intent serviceIntent = new Intent(LogcatUdpCfg.this, LogcatUdpService.class);
        Log.d(TAG, "Stop service");
        boolean stopres = stopService(serviceIntent);
        if (stopres) Log.d(TAG, "Service Stopped!");
        return stopres;
    }

    private void saveSettings() {
        Log.d(TAG, "saving settings");
        SharedPreferences.Editor editor = mSettings.edit();

        boolean sendIds = chkSendIds.isChecked();

        String devId = "";
        if (sendIds) devId = txtDevId.getText().toString();
        String destServer = txtServer.getText().toString();
        int destPort = 0;
        boolean error = false;

        try {
            destPort = Integer.parseInt(txtPort.getText().toString());
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Port is not a valid integer!", Toast.LENGTH_SHORT).show();
            error = true;
        }

        String logFormat = spinLogFormat.getSelectedItem().toString();

        boolean useFilter = chkUseFilter.isChecked();
        String filterText = "";
        if (useFilter) filterText = txtFilter.getText().toString();

        boolean autoStart = chkAutoStart.isChecked();

        if (!error) {
//          boolean startserv = false;
//			if (LogcatUdpService.isRunning) {
//				stopService();
//				startserv = true;
//			}
            editor.putBoolean(Preferences.SEND_IDS, sendIds);
            if (sendIds) editor.putString(Preferences.DEV_ID, devId);
            editor.putString(Preferences.DEST_SERVER, destServer);
            editor.putInt(Preferences.DEST_PORT, destPort);
            editor.putBoolean(Preferences.USE_FILTER, useFilter);
            if (useFilter) editor.putString(Preferences.FILTER_TEXT, filterText);
            editor.putBoolean(Preferences.AUTO_START, autoStart);
            editor.putString(Preferences.LOG_FORMAT, logFormat);
            editor.apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
//			if (startserv) {
//				startService();
//			}
        } else {
            Toast.makeText(this, "Settings not saved!!!", Toast.LENGTH_LONG).show();
        }
    }

    private int getLogFormatIndex(String format) {
        String[] format_array = getResources().getStringArray(R.array.log_format_array);
        int ret = Arrays.asList(format_array).indexOf(format);
        return Math.max(ret, 0);
    }
}
