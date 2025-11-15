package com.adonai.GsmNotify;

import android.app.PendingIntent;
import android.content.*;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.adonai.GsmNotify.database.DbProvider;
import com.adonai.GsmNotify.database.PersistManager;
import com.adonai.GsmNotify.entities.HistoryEntry;
import com.adonai.GsmNotify.misc.AbstractAsyncLoader;
import com.adonai.GsmNotify.misc.DeliveryConfirmReceiver;
import com.adonai.GsmNotify.misc.SentConfirmReceiver;
import com.adonai.contrib.ButtonWithRedTriangle;
import com.adonai.views.ColumnLinearLayout;
import com.google.gson.Gson;
import com.j256.ormlite.stmt.*;
import io.blackbox_vision.datetimepickeredittext.view.DatePickerEditText;

import java.io.*;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static android.widget.LinearLayout.LayoutParams;
import static com.adonai.GsmNotify.Utils.*;

public class SelectorActivity extends AppCompatActivity implements View.OnClickListener {

    private SharedPreferences mPrefs;

    public final static int STATUS_LOADER = 0;
    private StatusRetrieverCallback mLocalArchiveParseCallback = new StatusRetrieverCallback();

    private String[] mDeviceIds;
    Map<String, Device.CommonSettings> mDeviceSettingsMap;
    private ViewGroup mMainLayout;

    static boolean isRunning;
    static boolean isStatusChecking;

    private BroadcastReceiver sentReceiver, deliveryReceiver;
    private final IntentFilter sentIntentFilter = new IntentFilter(SENT);
    private final IntentFilter deliveredIntentFilter = new IntentFilter(DELIVERED);
    private boolean receiversRegistered;
    private Handler mUiHandler;
    private Handler.Callback mStatusWalkCallback = new StatusWalkCallback();

    private static final int HANDLE_START         = 0;
    private static final int HANDLE_SEND          = 1;
    private static final int HANDLE_ACK           = 2;
    private static final int HANDLE_TIMEOUT       = 3;
    private static final int HANDLE_TERMINATE     = 4;
    private static final int HANDLE_FINISH        = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = getSharedPreferences(SMSReceiveService.PREFERENCES, MODE_PRIVATE);

        sentReceiver = new SentConfirmReceiver(this);
        deliveryReceiver = new DeliveryConfirmReceiver(this);

        LoaderManager.getInstance(this).initLoader(STATUS_LOADER, null, mLocalArchiveParseCallback);

        mUiHandler = new Handler(Looper.getMainLooper(), mStatusWalkCallback);
    }

    @Override
    protected void onStart() {
        super.onStart();
        obtainAndSortDevices();

        if(isTablet(this)) {
            prepareTabletUI();
        } else {
            preparePhoneUI();
        }

        //--- When the SMS has been sent ---
        ContextCompat.registerReceiver(this, sentReceiver, sentIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        //--- When the SMS has been delivered. ---
        ContextCompat.registerReceiver(this, deliveryReceiver, deliveredIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiversRegistered = true;

        isRunning = true;
    }

    private void obtainAndSortDevices() {
        long a = System.currentTimeMillis();
        String storedIds = mPrefs.getString("IDs", "");
        if (TextUtils.isEmpty(storedIds)) {
            mDeviceIds = new String[0];
            mDeviceSettingsMap = new HashMap<>(0);
            return;
        }

        String[] rawIds = storedIds.split(";");
        List<String> validIds = new ArrayList<>(rawIds.length);
        mDeviceSettingsMap = new HashMap<>(rawIds.length);
        for(String devId : rawIds) {
            if (TextUtils.isEmpty(devId)) {
                continue;
            }
            String gson = mPrefs.getString(devId, "");
            Device.CommonSettings details = new Gson().fromJson(gson, Device.CommonSettings.class);
            if (details == null) {
                continue;
            }
            validIds.add(devId);
            mDeviceSettingsMap.put(devId, details);
        }

        mDeviceIds = validIds.toArray(new String[0]);
        if (mDeviceIds.length == 0) {
            return;
        }

        Arrays.sort(mDeviceIds, Comparator.comparing(id -> getDisplayName(mDeviceSettingsMap.get(id))));
        Log.e("timetrace", String.valueOf(System.currentTimeMillis() - a));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiversRegistered) {
            unregisterReceiver(sentReceiver);
            unregisterReceiver(deliveryReceiver);
            receiversRegistered = false;
        }

        isRunning = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // stop querying if we're exiting
        mUiHandler.removeMessages(HANDLE_TIMEOUT);
        mUiHandler.sendEmptyMessage(HANDLE_FINISH);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("number")) { // запущено из сервиса SMS
            Loader<List<DeviceStatus>> loader = LoaderManager.getInstance(this).getLoader(STATUS_LOADER);
            if (loader != null) {
                loader.onContentChanged();
            }
            if(isStatusChecking) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(HANDLE_ACK, intent.getStringExtra("number")));
            }
        }
    }

    private void prepareTabletUI() {
        ScrollView scrollView = new ScrollView(this);
        mMainLayout = new ColumnLinearLayout(this);
        scrollView.addView(mMainLayout);
        setContentView(scrollView);

        for (String devId : mDeviceIds) {
            Device.CommonSettings details = mDeviceSettingsMap.get(devId);
            if (details == null) {
                continue;
            }
            ButtonWithRedTriangle openDevice = new ButtonWithRedTriangle(this);
            openDevice.setWidth(LayoutParams.MATCH_PARENT);
            openDevice.setText(getDisplayName(details));
            openDevice.setUpperLeft(details.workOngoing);
            openDevice.setLowerRight(details.isGsmQaud);
            openDevice.setTag(devId);
            openDevice.setTag(R.integer.device_details, details);
            openDevice.setMaxLines(1);
            openDevice.setEllipsize(TextUtils.TruncateAt.END);
            openDevice.setOnClickListener(this);
            mMainLayout.addView(openDevice);
        }
    }

    private void preparePhoneUI() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        mMainLayout = deviceList;


        for (String ID : mDeviceIds) {
            Device.CommonSettings details = mDeviceSettingsMap.get(ID);
            if (details == null) {
                continue;
            }
            ButtonWithRedTriangle viewer = new ButtonWithRedTriangle(this);
            viewer.setWidth(LayoutParams.MATCH_PARENT);
            viewer.setText(getDisplayName(details));
            viewer.setUpperLeft(details.workOngoing);
            viewer.setLowerRight(details.isGsmQaud);
            viewer.setTag(ID);
            viewer.setTag(R.integer.device_details, details);
            viewer.setOnClickListener(this);
            deviceList.addView(viewer);
        }
        scrollView.addView(deviceList);

        setContentView(scrollView);
    }

    @Override
    public void onClick(View v) {
        Intent starter = new Intent(this, MainActivity.class).putExtra("ID", v.getTag().toString());
        startActivity(starter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.selector_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem smsOption = menu.findItem(R.id.notify_on_sms);
        boolean shouldOpen = mPrefs.getBoolean(SMSReceiveService.OPEN_ON_SMS_KEY, true);
        smsOption.setChecked(shouldOpen);

        MenuItem soundOption = menu.findItem(R.id.notify_with_sound);
        boolean shouldRing = mPrefs.getBoolean(SMSReceiveService.RING_ON_SMS_KEY, false);
        soundOption.setChecked(shouldRing);

        MenuItem soundAlarmOption = menu.findItem(R.id.notify_alarm_with_sound);
        boolean shouldAlarmRing = mPrefs.getBoolean(SMSReceiveService.RING_ON_ALARM_SMS_KEY, false);
        soundAlarmOption.setChecked(shouldAlarmRing);

        MenuItem queryOption = menu.findItem(R.id.query_all_devices);
        queryOption.setVisible(!isStatusChecking);

        MenuItem stopQueryOption = menu.findItem(R.id.stop_query_devices);
        stopQueryOption.setVisible(isStatusChecking);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Loader<List<DeviceStatus>> loader = LoaderManager.getInstance(this).getLoader(STATUS_LOADER);
        if (loader != null) {
            loader.onContentChanged();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.notify_on_sms: {
                boolean shouldOpen = mPrefs.getBoolean(SMSReceiveService.OPEN_ON_SMS_KEY, true);
                shouldOpen = !shouldOpen;

                // write to prefs
                mPrefs.edit().putBoolean(SMSReceiveService.OPEN_ON_SMS_KEY, shouldOpen).apply();

                // update menu checked state
                invalidateOptionsMenu();
                return true;
            }
            case R.id.notify_with_sound: {
                boolean shouldRing = mPrefs.getBoolean(SMSReceiveService.RING_ON_SMS_KEY, false);
                shouldRing = !shouldRing;

                // write to prefs
                SharedPreferences.Editor edit = mPrefs.edit();
                edit.putBoolean(SMSReceiveService.RING_ON_SMS_KEY, shouldRing);
                if(shouldRing) {
                    edit.putBoolean(SMSReceiveService.RING_ON_ALARM_SMS_KEY, !shouldRing); // exclusive
                }
                edit.apply();

                // update menu checked state
                invalidateOptionsMenu();
                return true;
            }
            case R.id.notify_alarm_with_sound: {
                boolean shouldRing = mPrefs.getBoolean(SMSReceiveService.RING_ON_ALARM_SMS_KEY, false);
                shouldRing = !shouldRing;

                // write to prefs
                SharedPreferences.Editor edit = mPrefs.edit();
                edit.putBoolean(SMSReceiveService.RING_ON_ALARM_SMS_KEY, shouldRing);
                if(shouldRing) {
                    edit.putBoolean(SMSReceiveService.RING_ON_SMS_KEY, !shouldRing); // exclusive
                }
                edit.apply();

                // update menu checked state
                invalidateOptionsMenu();
                return true;
            }
            case R.id.query_all_devices:
                mUiHandler.removeCallbacksAndMessages(null);
                mUiHandler.sendEmptyMessage(HANDLE_START);
                return true;
            case R.id.stop_query_devices:
                mUiHandler.removeCallbacksAndMessages(null);
                mUiHandler.sendEmptyMessage(HANDLE_TERMINATE);
                return true;
            case R.id.show_alarm_history:
                AlarmHistoryListFragment hlf = AlarmHistoryListFragment.newInstance();
                hlf.show(getFragmentManager(), "AlarmHistoryListDialog");
                return true;
            case R.id.show_ibutton_history:
                KeyHistoryListFragment khlf = KeyHistoryListFragment.newInstance();
                khlf.show(getFragmentManager(), "KeyHistoryListDialog");
                return true;
            case R.id.show_power_history:
                PowerHistoryListFragment phlf = PowerHistoryListFragment.newInstance();
                phlf.show(getFragmentManager(), "PowerHistoryListDialog");
                return true;
            case R.id.clear_all_history:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.clear_all_history).setMessage(R.string.clear_all_history_confirm);
                builder.setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PersistManager manager = DbProvider.getTempHelper(SelectorActivity.this);
                                try {
                                    Calendar prevMonth = Calendar.getInstance();
                                    prevMonth.set(Calendar.MONTH, prevMonth.get(Calendar.MONTH) - 2);
                                    DeleteBuilder<HistoryEntry, Long> db = manager.getHistoryDao().deleteBuilder();
                                    db.where().lt("eventDate", prevMonth.getTime());
                                    db.delete();
                                    Loader<List<DeviceStatus>> loader = LoaderManager.getInstance(SelectorActivity.this).getLoader(STATUS_LOADER);
                                    if (loader != null) {
                                        loader.onContentChanged();
                                    }
                                } catch (SQLException e) {
                                    Toast.makeText(SelectorActivity.this, R.string.db_cant_delete_history, Toast.LENGTH_LONG).show();
                                }
                                DbProvider.releaseTempHelper(); // it's ref-counted thus will not close if activity uses it...
                            }
                        });
                builder.create().show();
                return true;
            case R.id.add_device:
                Intent intent = new Intent(SelectorActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.export_csv_data:
                View export = View.inflate(this, R.layout.export_alarms_dialog, null);

                DateFormat ddMMyyyy = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Calendar minDate = Calendar.getInstance();
                minDate.add(Calendar.YEAR, -2);

                final DatePickerEditText dateFromPicker = export.findViewById(R.id.date_from);
                dateFromPicker.setMinDate(ddMMyyyy.format(minDate.getTime())); // previous year
                dateFromPicker.setManager(getSupportFragmentManager());
                dateFromPicker.setDate(minDate);

                final DatePickerEditText dateToPicker = (DatePickerEditText) export.findViewById(R.id.date_to);
                dateToPicker.setMaxDate(ddMMyyyy.format(Calendar.getInstance().getTime())); // current date
                dateToPicker.setManager(getSupportFragmentManager());
                dateToPicker.setDate(Calendar.getInstance());

                final EditText fileName = (EditText) export.findViewById(R.id.file_name);
                fileName.setText("export.csv");

                new AlertDialog.Builder(this)
                        .setTitle(R.string.export_alarms)
                        .setView(export)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PersistManager manager = DbProvider.getTempHelper(SelectorActivity.this);
                                try {
                                    Calendar from = dateFromPicker.getDate();
                                    from.set(Calendar.HOUR_OF_DAY, 0);
                                    from.set(Calendar.MINUTE, 0);
                                    from.set(Calendar.SECOND, 0);

                                    Calendar to = dateToPicker.getDate();
                                    to.set(Calendar.HOUR_OF_DAY, 23);
                                    to.set(Calendar.MINUTE, 59);
                                    to.set(Calendar.SECOND, 59);

                                    QueryBuilder<HistoryEntry, Long> qb = manager.getHistoryDao().queryBuilder();
                                    Where<HistoryEntry, Long> where = qb.where();
                                    where.between("eventDate", from.getTime(), to.getTime());
                                    List<HistoryEntry> exports = qb.orderBy("eventDate", true).query();
                                    exportToCsv(fileName.getText().toString(), exports);
                                } catch (SQLException e) {
                                    Toast.makeText(SelectorActivity.this, R.string.db_cant_export_to_csv, Toast.LENGTH_LONG).show();
                                }
                            }
                        })
                        .show();
        }

        return false;
    }

    private void exportToCsv(String name, List<HistoryEntry> exports) {
        StringBuilder csv = new StringBuilder(exports.size() * 200); // 140 sms text + 40-50 date/status/address
        csv.append(getString(R.string.csv_first_row)).append("\n");
        for (HistoryEntry he : exports) {
            DateFormat date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            DateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            DateFormat zone = new SimpleDateFormat("zzzz", Locale.getDefault());
            csv.append(date.format(he.getEventDate())).append(',')
                    .append(time.format(he.getEventDate())).append(',')
                    .append(zone.format(he.getEventDate())).append(',')
                    .append(he.getDeviceName()).append(',')
                    .append(he.getStatus()).append(',')
                    .append('"').append(he.getSmsText()).append('"')
                    .append('\n');
        }

        saveStringToAppStorage(name, csv.toString());
    }

    private void saveStringToAppStorage(String name, String csv) {
        File baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (baseDir == null) {
            baseDir = getFilesDir();
        }

        File exportDir = new File(baseDir, "Signal");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Toast.makeText(this, R.string.error_saving_csv, Toast.LENGTH_SHORT).show();
            return;
        }

        File toFile = new File(exportDir, name);

        try (Writer writer = new FileWriter(toFile)) {
            writer.write(csv);
            Toast.makeText(this, getString(R.string.saved_to) + toFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_saving_csv, Toast.LENGTH_SHORT).show();
        }
    }


    private class StatusRetrieverCallback implements LoaderManager.LoaderCallbacks<List<DeviceStatus>> {

        @Override
        public Loader<List<DeviceStatus>> onCreateLoader(int i, Bundle bundle) {
            return new AbstractAsyncLoader<List<DeviceStatus>>(SelectorActivity.this) {

                @Override
                protected void onForceLoad() {
                    if(mDeviceIds != null) {
                        super.onForceLoad();
                    }
                }

                @NonNull
                @Override
                public List<DeviceStatus> loadInBackground() {
                    if (mDeviceIds == null || mDeviceIds.length == 0) {
                        return Collections.emptyList();
                    }

                    List<DeviceStatus> devStatuses = new ArrayList<>(mDeviceIds.length);

                    PersistManager manager = DbProvider.getTempHelper(SelectorActivity.this);
                    for (String deviceId : mDeviceIds) {
                        DeviceStatus currentStatus = DeviceStatus.UNKNOWN;
                        Device.CommonSettings details = mDeviceSettingsMap.get(deviceId);
                        if (details == null) {
                            devStatuses.add(currentStatus);
                            continue;
                        }
                        try {
                            List<HistoryEntry> entriesForDevice = manager.getHistoryDao().queryBuilder()
                                    .orderBy("eventDate", false)
                                    .where().eq("deviceName", getDisplayName(details))
                                    .and().eq("archived", false).query();
                            for(HistoryEntry he : entriesForDevice) {
                                currentStatus = he.getStatus();
                                if(currentStatus != DeviceStatus.UNKNOWN) { // stop if we've found status
                                    break;
                                }
                            }
                        } catch (SQLException sqle) {
                            currentStatus = DeviceStatus.UNKNOWN;
                        }
                        devStatuses.add(currentStatus);
                    }
                    DbProvider.releaseTempHelper(); // it's ref-counted thus will not close if activity uses it...
                    return devStatuses;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<DeviceStatus>> loader, List<DeviceStatus> statusesRetrieved) {
            int currentButtonIndex = 0;
            for(DeviceStatus status : statusesRetrieved) {
                Button child = getDeviceButton(currentButtonIndex++);
                if (child == null) {
                    continue;
                }

                Drawable background = child.getBackground();
                if (background == null) {
                    continue;
                }
                Drawable newBackground = background.mutate();
                switch (status) {
                    case ARMED:
                        newBackground.setColorFilter(ContextCompat.getColor(SelectorActivity.this, R.color.dark_yellow), PorterDuff.Mode.MULTIPLY);
                        break;
                    case DISARMED:
                        newBackground.setColorFilter(ContextCompat.getColor(SelectorActivity.this, R.color.dark_green), PorterDuff.Mode.MULTIPLY);
                        break;
                    case ALARM:
                        newBackground.setColorFilter(ContextCompat.getColor(SelectorActivity.this, R.color.dark_red), PorterDuff.Mode.MULTIPLY);
                        break;
                    case UNKNOWN:
                        // leave the same
                        newBackground.clearColorFilter();
                        break;
                }
                ViewCompat.setBackground(child, newBackground);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<DeviceStatus>> loader) {

        }
    }

    private class StatusWalkCallback implements Handler.Callback {
        private int currentQueried;

        // TODO: Replace the manual Handler-driven SMS polling with WorkManager when telephony interactions are modernized.
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_START:
                    if (mDeviceIds == null || mDeviceIds.length == 0) {
                        return true;
                    }
                    currentQueried = 0;
                    mUiHandler.sendEmptyMessage(HANDLE_SEND);
                    isStatusChecking = true;
                    invalidateOptionsMenu();
                    return true;
                case HANDLE_SEND:
                    Button deviceOpenButton = getDeviceButton(currentQueried);
                    if (deviceOpenButton == null) {
                        continueQueryIfNeeded();
                        return true;
                    }
                    Device.CommonSettings details = (Device.CommonSettings) deviceOpenButton.getTag(R.integer.device_details);
                    if (details == null) {
                        continueQueryIfNeeded();
                        return true;
                    }
                    deviceOpenButton.setText("→ " + deviceOpenButton.getText() + " ←");
                    sendStatusQuerySms(details);
                    mUiHandler.sendEmptyMessageDelayed(HANDLE_TIMEOUT, SMS_ROUNDTRIP_TIMEOUT);
                    return true;
                case HANDLE_ACK:
                    String number = (String) msg.obj;
                    if(currentQueried < mDeviceIds.length && number.equals(mDeviceIds[currentQueried])) { // it's current queried device's status message!
                        mUiHandler.removeMessages(HANDLE_TIMEOUT);
                        continueQueryIfNeeded();
                    }
                    return true;
                case HANDLE_TIMEOUT: // device didn't answer
                    Toast.makeText(SelectorActivity.this, R.string.sms_wait_timeout, Toast.LENGTH_LONG).show();
                    continueQueryIfNeeded();
                    return true;
                case HANDLE_TERMINATE:
                    restoreOldName();
                    /* fall through */
                case HANDLE_FINISH:
                    isStatusChecking = false;
                    invalidateOptionsMenu();
                    return true;
            }
            return false;
        }

        private void continueQueryIfNeeded() {
            restoreOldName();
            if(mDeviceIds == null || mDeviceIds.length == 0) {
                mUiHandler.sendEmptyMessage(HANDLE_FINISH);
                return;
            }
            if(mDeviceIds.length > ++currentQueried) { // query next
                mUiHandler.sendEmptyMessage(HANDLE_SEND);
            } else { // finish
                mUiHandler.sendEmptyMessage(HANDLE_FINISH);
            }
        }

        private void restoreOldName() {
            Button deviceOpenButton = getDeviceButton(currentQueried);
            if (deviceOpenButton == null) {
                return;
            }
            Device.CommonSettings details = (Device.CommonSettings) deviceOpenButton.getTag(R.integer.device_details);
            if (details != null) {
                deviceOpenButton.setText(getDisplayName(details));
            }
        }
    }

    public void sendStatusQuerySms(Device.CommonSettings details) {
        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SENT), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(DELIVERED), PendingIntent.FLAG_IMMUTABLE);
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(details.number, null, "*" + details.password + "#_info#", sentPI, deliveredPI);
    }

    private Button getDeviceButton(int index) {
        if (mMainLayout == null) {
            return null;
        }
        View child = mMainLayout.getChildAt(index);
        if (child instanceof Button) {
            return (Button) child;
        }
        return null;
    }
}
