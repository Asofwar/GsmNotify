package com.adonai.GsmNotify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.adonai.GsmNotify.database.DbProvider;
import com.adonai.GsmNotify.entities.HistoryEntry;
import com.adonai.GsmNotify.misc.DeliveryConfirmReceiver;
import com.adonai.GsmNotify.misc.SentConfirmReceiver;
import com.google.gson.Gson;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.SEND_SMS;

@SuppressLint("CommitPrefEdits")
public class MainActivity extends Activity implements View.OnClickListener {

    private static final int PERMISSIONS_REQUEST_CODE = 0;

    MessageQueue incMessages;

    SharedPreferences mPrefs;
    BroadcastReceiver sentReceiver, deliveryReceiver;
    Device mDevice;
    private TelephonyManager mTelephonyManager;
    private PhoneStateListener mCallListener;

    //ScrollView mScroll;
    Button mNotifyEnable, mNotifyDisable, mRelay1Enable, mRelay1Disable, mRelay2Enable, mRelay2Disable;
    Button mGetData, mGetTemperature;
    EditText mResultText, mDeviceInfo;

    static boolean isRunning;
    static String deviceNumber;

    static boolean isCalling;

    // увеличиваем длительность нажатия до 500 мс
    private View.OnTouchListener pressHolder = new View.OnTouchListener() {
        @Override
        public boolean onTouch(final View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    v.setPressed(true);
                    onClick(v);
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.setPressed(false);
                        }
                    }, 500);
                    break;

            }
            return true;
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DbProvider.setHelper(this);
        if (Utils.isTablet(this)) {
            setContentView(R.layout.main_tablet);
        } else {
            setContentView(R.layout.main_phone);
        }

        mTelephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        mCallListener = new QaudEndCallListener();
        registerCallStateListenerIfPermitted();

        incMessages = new MessageQueue();
        sentReceiver = new SentConfirmReceiver(this);
        deliveryReceiver = new DeliveryConfirmReceiver(this);
        mPrefs = getSharedPreferences(SMSReceiveService.PREFERENCES, MODE_PRIVATE);

        mNotifyEnable = findViewById(R.id.signal_on_button);
        mNotifyDisable = findViewById(R.id.signal_off_button);
        mRelay1Enable = findViewById(R.id.relay1_on_button);
        mRelay1Disable = findViewById(R.id.relay1_off_button);
        mRelay2Enable = findViewById(R.id.relay2_on_button);
        mRelay2Disable = findViewById(R.id.relay2_off_button);
        mGetData = findViewById(R.id.get_data_button);
        mGetTemperature = findViewById(R.id.get_temperature_button);

        mNotifyEnable.setOnTouchListener(pressHolder);
        mNotifyDisable.setOnTouchListener(pressHolder);
        mRelay1Enable.setOnTouchListener(pressHolder);
        mRelay1Disable.setOnTouchListener(pressHolder);
        mRelay2Enable.setOnTouchListener(pressHolder);
        mRelay2Disable.setOnTouchListener(pressHolder);
        mGetData.setOnTouchListener(pressHolder);
        mGetTemperature.setOnTouchListener(pressHolder);

        mResultText = findViewById(R.id.result_text);
        mDeviceInfo = findViewById(R.id.device_additional_info_text);


        if (getIntent().hasExtra("ID")) { // запускаем из настроек
            deviceNumber = getIntent().getStringExtra("ID");
            extractParams();
            prefillHistory();
        } else if (getIntent().hasExtra("number")) { // запускаем из ресивера
            deviceNumber = getIntent().getStringExtra("number");
            extractParams();

            mResultText.setText(getIntent().getStringExtra("text"));
        } else { // запускаем сами
            String[] IDs = mPrefs.getString("IDs", "").split(";");
            if (IDs.length > 1) {
                Intent selector = new Intent(this, SelectorActivity.class);
                startActivity(selector);
                finish();
            } else if (IDs[0].length() != 0) {
                deviceNumber = IDs[0];
                extractParams();
            } else { // first launch
                Toast.makeText(getApplicationContext(), R.string.first_launch, Toast.LENGTH_LONG).show();
                Intent firstLaunch = new Intent(this, SettingsActivity.class);
                startActivity(firstLaunch);
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DbProvider.releaseHelper();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // request permissions required for SMS handling and call state monitoring
        List<String> denied = checkPermissions(READ_SMS, SEND_SMS, RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE);
        if (!denied.isEmpty()) {
            ActivityCompat.requestPermissions(this, denied.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        } else {
            registerCallStateListenerIfPermitted();
        }

        //--- When the SMS has been sent ---
        registerReceiver(sentReceiver, new IntentFilter(Utils.SENT));
        //--- When the SMS has been delivered. ---
        registerReceiver(deliveryReceiver, new IntentFilter(Utils.DELIVERED));

        isRunning = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (Integer res: grantResults) {
                if (res != PermissionChecker.PERMISSION_GRANTED)
                    finish();
            }
            registerCallStateListenerIfPermitted();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // stop ringing
            Intent broadcastIntent = new Intent(this, SMSReceiveService.class);
            broadcastIntent.putExtra("stop_alarm", true);
            startService(broadcastIntent);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(sentReceiver);
        unregisterReceiver(deliveryReceiver);

        unregisterCallStateListener();

        isRunning = false;
    }

    private void registerCallStateListenerIfPermitted() {
        if (mTelephonyManager != null && mCallListener != null &&
                PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PermissionChecker.PERMISSION_GRANTED) {
            mTelephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private void unregisterCallStateListener() {
        if (mTelephonyManager != null && mCallListener != null) {
            mTelephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private List<String> checkPermissions(String... required) {
        List<String> denied = new ArrayList<>();
        for (String perm : required) {
            if (PermissionChecker.checkSelfPermission(this, perm) != PermissionChecker.PERMISSION_GRANTED)
                denied.add(perm);
        }
        return denied;
    }

    private void extractParams() {
        String gson = mPrefs.getString(deviceNumber, "");
        if (!gson.equals("")) {
            mDevice = new Device();
            mDevice.details = new Gson().fromJson(gson, Device.CommonSettings.class);
            setTitle(mDevice.details.name);

            // works for tablets, show additional info
            if (mDeviceInfo != null && mDevice.details.info != null) {
                mDeviceInfo.setText(mDevice.details.info);
            }

            if (mDevice.details.isGsmQaud) { // no relay buttons
                mRelay1Enable.setEnabled(false);
                mRelay1Disable.setEnabled(false);
                mRelay2Enable.setEnabled(false);
                mRelay2Disable.setEnabled(false);
            }

            invalidateOptionsMenu();
        } else {
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //setIntent(intent);

        if (intent.hasExtra("number")) { // launched from SMS receive service or by notification click
            String newMessage = intent.getStringExtra("text");
            if (intent.getStringExtra("number").equals(deviceNumber)) {
                incMessages.add(newMessage);
                mResultText.setTextKeepState(incMessages.toString());
            } else { // it's another number, switch!
                incMessages.clear();
                incMessages.add(newMessage);
                deviceNumber = intent.getStringExtra("number");
                extractParams();
                mResultText.setTextKeepState(incMessages.toString());
            }
        } else if (intent.hasExtra("ID")) { // launched from selector window
            deviceNumber = intent.getStringExtra("ID");
            extractParams();
            prefillHistory();
        }
    }

    private void prefillHistory() {
        // prefill text from DB
        try {
            RuntimeExceptionDao<HistoryEntry, Long> dao = DbProvider.getHelper().getHistoryDao();
            List<HistoryEntry> recentEntries = dao.queryBuilder().orderBy("eventDate", false).limit(5L)
                    .where().eq("deviceName", mDevice.details.name).query();
            Collections.reverse(recentEntries);
            for (HistoryEntry entry : recentEntries) {
                incMessages.add(entry);
            }
            mResultText.setTextKeepState(incMessages.toString());
        } catch (SQLException e) {
            Toast.makeText(this, R.string.db_cant_query_history, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem smsOption = menu.findItem(R.id.work_ongoing);
        if (mDevice != null) {
            smsOption.setChecked(mDevice.details.workOngoing);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.work_ongoing: {
                mDevice.details.workOngoing = !mDevice.details.workOngoing;

                // write to prefs
                SharedPreferences.Editor edit = mPrefs.edit();
                edit.putString(mDevice.details.number, new Gson().toJson(mDevice.details));
                edit.apply();

                // update menu checked state
                invalidateOptionsMenu();
                break;
            }
            case R.id.settings_menu: {
                AlertDialog.Builder settingsSelector = new AlertDialog.Builder(this);
                final String[] IDs = mPrefs.getString("IDs", "").split(";");

                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        switch (i) {
                            case 0: //  add_device
                                startActivity(intent);
                                break;
                            case 1: { // remove_device
                                List<String> IDStrings = new ArrayList<>();
                                Collections.addAll(IDStrings, mPrefs.getString("IDs", "").split(";"));
                                IDStrings.remove(deviceNumber);

                                // deleting from database
                                try {
                                    RuntimeExceptionDao<HistoryEntry, Long> dao = DbProvider.getHelper().getHistoryDao();
                                    DeleteBuilder<HistoryEntry, Long> stmt = dao.deleteBuilder();
                                    stmt.where().eq("deviceName", mDevice.details.name);
                                    dao.delete(stmt.prepare());
                                } catch (SQLException e) {
                                    Toast.makeText(MainActivity.this, R.string.db_cant_delete_history, Toast.LENGTH_LONG).show();
                                }

                                SharedPreferences.Editor edit = mPrefs.edit();
                                edit.putString("IDs", Utils.join(IDStrings, ";"));
                                edit.remove(deviceNumber);
                                edit.apply();

                                if (IDs.length != 1) { // если есть еще устройства, даем выбор
                                    Intent selector = new Intent(MainActivity.this, SelectorActivity.class);
                                    startActivity(selector);
                                } else { // если нет, надо добавить
                                    startActivity(intent);
                                }
                                //finish();
                                break;
                            }
                            case 2: // edit_device
                                startActivity(intent.putExtra("ID", deviceNumber));
                                break;
                        }
                    }
                };

                if (IDs.length > 1 || IDs.length == 1 && IDs[0].length() != 0) {
                    settingsSelector.setItems(new CharSequence[]{getString(R.string.add_device), getString(R.string.remove_device), getString(R.string.edit_device)}, listener);
                } else {
                    settingsSelector.setItems(new CharSequence[]{getString(R.string.add_device), getString(R.string.remove_device)}, listener);
                }
                settingsSelector.create().show();
                return true;
            }
            case R.id.device_history:
                HistoryListFragment hlf = HistoryListFragment.newInstance(mDevice.details.name);
                hlf.show(getFragmentManager(), "HistoryDialog_" + mDevice.details.name);
                return true;
        }

        return false;
    }

    private class QaudEndCallListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (isCalling && state == TelephonyManager.CALL_STATE_IDLE) {
                isCalling = false;
                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onClick(View view) {
        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(Utils.SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(Utils.DELIVERED), 0);
        SmsManager sms = SmsManager.getDefault();
        if (mDevice.details.isGsmQaud) {
            switch (view.getId()) {
                case R.id.get_data_button:
                    sms.sendTextMessage(mDevice.details.number, null, "Info", sentPI, deliveredPI);
                    break;
                case R.id.get_temperature_button:
                    sms.sendTextMessage(mDevice.details.number, null, "Temp", sentPI, deliveredPI);
                    break;
                case R.id.signal_on_button:
                case R.id.signal_off_button:
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setData(Uri.parse("tel:" + mDevice.details.number));
                    isCalling = true;
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    startActivity(intent);
                    break;
            }
        } else {
            switch (view.getId()) {
                case R.id.relay1_on_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_set_1=1#", sentPI, deliveredPI);
                    break;
                case R.id.relay1_off_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_set_1=0#", sentPI, deliveredPI);
                    break;
                case R.id.relay2_on_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_set_2=1#", sentPI, deliveredPI);
                    break;
                case R.id.relay2_off_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_set_2=0#", sentPI, deliveredPI);
                    break;
                case R.id.get_data_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_info#", sentPI, deliveredPI);
                    break;
                case R.id.get_temperature_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_temp#", sentPI, deliveredPI);
                    break;
                case R.id.signal_on_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_on#", sentPI, deliveredPI);
                    break;
                case R.id.signal_off_button:
                    sms.sendTextMessage(mDevice.details.number, null, "*" + mDevice.details.password + "#_off#", sentPI, deliveredPI);
                    break;
            }
        }
    }
}
