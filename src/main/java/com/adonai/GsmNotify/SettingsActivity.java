package com.adonai.GsmNotify;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.telephony.SmsManager;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.adonai.GsmNotify.settings.SettingsFragment;
import com.adonai.GsmNotify.settings.SettingsPage1;
import com.adonai.GsmNotify.settings.SettingsPage2;
import com.adonai.GsmNotify.settings.SettingsPage3;
import com.adonai.GsmNotify.settings.SettingsPage4;
import com.adonai.GsmNotify.settings.SettingsPage5;
import com.adonai.GsmNotify.settings.SettingsPage6;
import com.adonai.GsmNotify.settings.SettingsPage7;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("CommitPrefEdits")
public class SettingsActivity extends FragmentActivity implements View.OnClickListener, Handler.Callback {
    final public static int HANDLE_STEP = 1;
    final public static int HANDLE_FINISH = 2;
    final public static int HANDLE_RESET = 3;
    final public static int HANDLE_FORCE_RESET = 4;


    Boolean hasSomethingChanged = false;

    BroadcastReceiver sentReceiver, deliveryReceiver;
    SharedPreferences mPrefs;
    ProgressDialog pd;

    Button mApply, mEditDevice, mManageDevice, mApplyDevice;
    TextView mPasswordLabel;
    EditText mDeviceName, mDeviceNumber, mDevicePassword, mDeviceInfo;
    CheckBox mIsGsmQaud;
    Handler mHandler;

    FragmentManager mFragmentManager;
    SettingsFragment[] mSettingsPages = new SettingsFragment[7];
    ViewPager mPager;
    ViewFlipper mFlipper;
    FragmentPagerAdapter mPagerAdapter;

    Device mDevice;

    static boolean isRunning;

    public class SentConfirmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    Toast.makeText(SettingsActivity.this, getString(R.string.sms_sent_success), Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Toast.makeText(SettingsActivity.this, getString(R.string.generic_failure), Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Toast.makeText(SettingsActivity.this, getString(R.string.no_service), Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Toast.makeText(SettingsActivity.this, getString(R.string.null_message), Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Toast.makeText(getBaseContext(), getString(R.string.radio_off), Toast.LENGTH_SHORT).show();
                    break;
            }

            if (getResultCode() != Activity.RESULT_OK) {
                mHandler.removeCallbacksAndMessages(null);
                pd.dismiss();
            }
        }
    }

    public class DeliveryConfirmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    Toast.makeText(SettingsActivity.this, getString(R.string.sms_deliver_success), Toast.LENGTH_SHORT).show();
                    break;
                case Activity.RESULT_CANCELED:
                    //Toast.makeText(SettingsActivity.this, getString(R.string.result_canceled), Toast.LENGTH_SHORT).show();
                    //mHandler.removeCallbacksAndMessages(null);
                    //pd.dismiss();
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_settings);

        mPrefs = getSharedPreferences(SMSReceiveService.PREFERENCES, MODE_PRIVATE);
        sentReceiver = new SentConfirmReceiver();
        deliveryReceiver = new DeliveryConfirmReceiver();
        mFragmentManager = getSupportFragmentManager();

        mPager = findViewById(R.id.settings_page_holder);
        mPager.setOffscreenPageLimit(mSettingsPages.length);
        mPagerAdapter = new FragmentPagerAdapter(mFragmentManager) {
            @Override
            public Fragment getItem(int i) {
                assert i < mSettingsPages.length;
                return mSettingsPages[i];
            }

            @Override
            public int getCount() {
                return mSettingsPages.length;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case 0:
                        return getString(R.string.common);
                    case 1:
                        return getString(R.string.phones);
                    case 2:
                        return getString(R.string.inputs);
                    case 3:
                        return getString(R.string.outputs);
                    case 4:
                        return getString(R.string.temperature);
                    case 5:
                        return getString(R.string.report);
                    case 6:
                        return getString(R.string.restart);
                    default:
                        return null;
                }
            }
        };

        mPager.setAdapter(mPagerAdapter);

        mFlipper = findViewById(R.id.settings_flipper);
        mApply = findViewById(R.id.device_apply);
        mApply.setOnClickListener(this);
        mEditDevice = findViewById(R.id.edit_device_button);
        mEditDevice.setOnClickListener(this);
        mManageDevice = findViewById(R.id.manage_device_button);
        mManageDevice.setOnClickListener(this);
        mApplyDevice = findViewById(R.id.device_apply_button);
        mApplyDevice.setOnClickListener(this);
        mDeviceName = findViewById(R.id.device_name_text);
        mDeviceNumber = findViewById(R.id.device_number_text);
        mPasswordLabel = findViewById(R.id.device_password_label);
        mDevicePassword = findViewById(R.id.device_password_text);
        mDeviceInfo = findViewById(R.id.device_additional_info_text);
        mIsGsmQaud = findViewById(R.id.gsm_qaud_checkbox);
        mIsGsmQaud.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                int visibility = b ? View.GONE : View.VISIBLE;
                mDevicePassword.setVisibility(visibility); // don't show password field if we have GSM Qaud
                mPasswordLabel.setVisibility(visibility);
            }
        });

        prepareUI(getIntent().getStringExtra("ID"));

        mHandler = new Handler(this);
    }

    private void prepareUI(String id) {
        mDevice = new Device();

        if (id != null) {
            mDevice.details = new Gson().fromJson(mPrefs.getString(id, ""), Device.CommonSettings.class);

            if (mDevice.details.name != null) {
                mDeviceName.setText(mDevice.details.name);
            }
            if (mDevice.details.number != null) {
                mDeviceNumber.setText(mDevice.details.number);
            }
            if (mDevice.details.password != null) {
                mDevicePassword.setText(mDevice.details.password);
            }
            if (mDevice.details.info != null) {
                mDeviceInfo.setText(mDevice.details.info);
            }
            if(mDevice.details.isGsmQaud) { // can't configure GSM Qaud
                mEditDevice.setEnabled(false);
                mIsGsmQaud.setChecked(true);
            }

            mManageDevice.setVisibility(View.VISIBLE);
            mEditDevice.setVisibility(View.VISIBLE);
        }

        mSettingsPages[0] = SettingsPage1.newInstance(mDevice);
        mSettingsPages[1] = SettingsPage2.newInstance(mDevice);
        mSettingsPages[2] = SettingsPage3.newInstance(mDevice);
        mSettingsPages[3] = SettingsPage4.newInstance(mDevice);
        mSettingsPages[4] = SettingsPage5.newInstance(mDevice);
        mSettingsPages[5] = SettingsPage6.newInstance(mDevice);
        mSettingsPages[6] = SettingsPage7.newInstance(mDevice);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //--- When the SMS has been sent ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sentReceiver, new IntentFilter(Utils.SENT), Context.RECEIVER_NOT_EXPORTED);
            //--- When the SMS has been delivered. ---
            registerReceiver(deliveryReceiver, new IntentFilter(Utils.DELIVERED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sentReceiver, new IntentFilter(Utils.SENT));
            //--- When the SMS has been delivered. ---
            registerReceiver(deliveryReceiver, new IntentFilter(Utils.DELIVERED));
        }

        isRunning = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(sentReceiver);
        unregisterReceiver(deliveryReceiver);

        isRunning = false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.manage_device_button: {
                Intent editNow = new Intent(this, MainActivity.class).putExtra("ID", mDevice.details.number);
                startActivity(editNow);
                finish();
                break;
            }
            case R.id.device_apply_button: {
                String newNumber = mDeviceNumber.getText().toString();
                String newPassword = mDevicePassword.getText().toString();
                String newName = mDeviceName.getText().toString();
                String newInfo = mDeviceInfo.getText().toString();

                boolean validPassword = !newPassword.isEmpty() || mIsGsmQaud.isChecked();
                if (validPassword && !newNumber.isEmpty() && !newName.isEmpty()) { // all fields are filled in
                    List<String> IDStrings = new ArrayList<>();
                    Collections.addAll(IDStrings, mPrefs.getString("IDs", "").split(";"));

                    if (IDStrings.contains(newNumber) && !newNumber.equals(mDevice.details.number)) {
                        // we have already that number and that's not us
                        Toast.makeText(this, R.string.existing_device, Toast.LENGTH_SHORT).show();
                        break;
                    }

                    // we assume we have all the views created
                    EditText password = mSettingsPages[0].getView().findViewById(R.id.new_password_edit);
                    password.setText(newPassword); // implicitly changes mDevice.details.password

                    SharedPreferences.Editor edit = mPrefs.edit();
                    if (mDevice.details.number != null) {
                        IDStrings.remove(mDevice.details.number);
                        edit.remove(mDevice.details.number);
                    }

                    mDevice.details.name = newName;
                    mDevice.details.number = newNumber;
                    mDevice.details.info = newInfo;
                    mDevice.details.isGsmQaud = mIsGsmQaud.isChecked();

                    mEditDevice.setEnabled(!mIsGsmQaud.isChecked());

                    IDStrings.add(mDevice.details.number);
                    edit.putString("IDs", Utils.join(IDStrings, ";"));
                    edit.putString(mDevice.details.number, new Gson().toJson(mDevice.details));
                    edit.commit();

                    Toast.makeText(this, R.string.settings_applied, Toast.LENGTH_SHORT).show();

                    mManageDevice.setVisibility(View.VISIBLE);
                    mEditDevice.setVisibility(View.VISIBLE);
                } else {
                    new AlertDialog.Builder(this).setMessage(R.string.data_not_full).create().show();
                }
                break;
            }
            case R.id.edit_device_button: {
                mFlipper.setDisplayedChild(1);
                break;
            }
            case R.id.device_apply:
                hasSomethingChanged = false;

                /*
                Pair<Boolean, String> toSend = compileDiff(1);
                if(toSend.first)
                    new AlertDialog.Builder(this).setTitle("Страница 1").setMessage(toSend.second).show();
                toSend = compileDiff(2);
                if(toSend.first)
                    new AlertDialog.Builder(this).setTitle("Страница 2").setMessage(toSend.second).show();
                toSend = compileDiff(3);
                if(toSend.first)
                    new AlertDialog.Builder(this).setTitle("Страница 3").setMessage(toSend.second).show();
                toSend = compileDiff(4);
                if(toSend.first)
                    new AlertDialog.Builder(this).setTitle("Страница 4").setMessage(toSend.second).show();
                toSend = compileDiff(5);
                if(toSend.first)
                    new AlertDialog.Builder(this).setTitle("Страница 5").setMessage(toSend.second).show();
                */

                pd = ProgressDialog.show(this, getString(R.string.wait_please), getString(R.string.querying_device), true, false);
                mHandler.sendMessage(mHandler.obtainMessage(HANDLE_STEP, 1));
                break;
        }
    }

    // boolean - page changed, string - message to send
    private Pair<Boolean, String> compileDiff(Integer pageNumber) {
        assert pageNumber != null;
        String res = "*" + mDevicePassword.getText().toString() + "#_sp_*" + pageNumber;
        Integer original_length = res.length();
        if (pageNumber == 1) {
            if (mDevice.timeToArm != null) {
                res += "_1=" + String.format("%03d", mDevice.timeToArm);
            }
            if (mDevice.inputManager != null) {
                res += "_2=" + String.format("%1d", mDevice.inputManager);
            }
            if (mDevice.sendSmsOnPowerLoss != null) {
                res += "_3=" + (mDevice.sendSmsOnPowerLoss ? "1" : "+");
            }
            if (mDevice.timeToWaitOnPowerLoss != null) {
                res += "_4=" + String.format("%03d", mDevice.timeToWaitOnPowerLoss);
            }
            if (mDevice.smsAtArm != null) {
                res += "_5=" + (mDevice.smsAtArm ? "1" : "+");
            }
            if (mDevice.smsAtWrongKey != null) {
                res += "_6=" + (mDevice.smsAtWrongKey ? "1" : "+");
            }
            if (mDevice.details.password != null && !mDevice.details.password.equals(mDevicePassword.getText().toString())) {
                res += "_7=" + "\"" + mDevice.details.password + "\"";
            }
            if (mDevice.smsAtDisarm != null) {
                res += "_8=" + (mDevice.smsAtDisarm ? "1" : "+");
            }
        }
        if (pageNumber == 2) {
            for (int i = 0; i < mDevice.phones.length; i++) {
                Device.PhoneSettings curr = mDevice.phones[i];

                if (curr.phoneNum != null) {
                    res += "_1." + String.valueOf(i + 1) + "=" + curr.phoneNum;
                }
                if (curr.info != null) {
                    res += "_2." + String.valueOf(i + 1) + "=" + (curr.info ? "1" : "+");
                }
                if (curr.manage != null) {
                    res += "_3." + String.valueOf(i + 1) + "=" + (curr.manage ? "1" : "+");
                }
                if (curr.confirm != null) {
                    res += "_4." + String.valueOf(i + 1) + "=" + (curr.confirm ? "1" : "+");
                }
            }
            if (mDevice.recallCycles != null) {
                res += "_5=" + String.format("%03d", mDevice.recallCycles);
            }
            if (mDevice.recallWait != null) {
                res += "_6=" + String.format("%03d", mDevice.recallWait);
            }
            if (mDevice.checkBalanceNum != null) {
                res += "_7=" + "\"" + mDevice.checkBalanceNum + "\"";
            }
        }
        if (pageNumber == 3) {
            for (int i = 0; i < mDevice.inputs.length; i++) {
                Device.InputSettings curr = mDevice.inputs[i];

                if (curr.timeToRearm != null) {
                    res += "_1." + String.valueOf(i + 1) + "=" + String.format("%03d", curr.timeToRearm);
                }
                // TODO: here will some additional code sit
                if (curr.timeToWaitBeforeCall != null) {
                    res += "_3." + String.valueOf(i + 1) + "=" + String.format("%03d", curr.timeToWaitBeforeCall);
                }
                if (curr.smsText != null) {
                    res += "_5." + String.valueOf(i + 1) + "=" + "\"" + curr.smsText + "\"";
                }
                if (curr.constantControl != null) {
                    res += "_6." + String.valueOf(i + 1) + "=" + (curr.constantControl ? "1" : "+");
                }
                if (curr.innerSound != null) {
                    res += "_7." + String.valueOf(i + 1) + "=" + (curr.innerSound ? "1" : "+");
                }
            }
        }
        if (pageNumber == 4) {
            for (int i = 0; i < mDevice.outputs.length; i++) {
                Device.OutputSettings curr = mDevice.outputs[i];

                if (curr.outputMode != null) // always send timing when change mode
                {
                    res += "_1." + String.valueOf(i + 1) + "=" + String.format("%1d", curr.outputMode);
                    if (curr.outputMode == 3 && curr.timeToEnableOnDisarm != null) {
                        res += "_2." + String.valueOf(i + 1) + "=" + String.format("%03d", curr.timeToEnableOnDisarm);
                    } else if (curr.outputMode == 4 && curr.timeToEnableOnAlert != null) {
                        res += "_2." + String.valueOf(i + 1) + "=" + String.format("%03d", curr.timeToEnableOnAlert);
                    }
                }
            }
        }
        if (pageNumber == 5) {
            if (mDevice.enableTC != null) {
                res += "_1=" + (mDevice.enableTC ? "1" : "+");
            }
            if (mDevice.tempLimit != null) {
                res += "_2=" + String.format("%05.0f", mDevice.tempLimit * 1000).substring(0, 5);
            }
            if (mDevice.tcSendSms != null) {
                res += "_3=" + (mDevice.tcSendSms ? "1" : "+");
            }
            if (mDevice.tcActivateAlert != null) {
                res += "_4=" + (mDevice.tcActivateAlert ? "1" : "+");
            }
            if (mDevice.tcActivateInnerSound != null) {
                res += "_5=" + (mDevice.tcActivateInnerSound ? "1" : "+");
            }
            if (mDevice.tMin != null) {
                res += "_6=" + String.format("%05.0f", mDevice.tMin * 1000).substring(0, 5);
            }
            if (mDevice.tMax != null) {
                res += "_7=" + String.format("%05.0f", mDevice.tMax * 1000).substring(0, 5);
            }
        }

        return new Pair<>(res.length() > original_length, res + "#");
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLE_STEP: {
                Integer step = (Integer) msg.obj;
                switch (step) {
                    case 1: // pages
                    case 2:
                    case 3:
                    case 4:
                    case 5: {
                        pd.setMessage(getString(R.string.setting_mode) + " " + step);
                        Pair<Boolean, String> toSend = compileDiff(step);
                        if (toSend.first) {
                            hasSomethingChanged = true;
                            sendSms(toSend.second);
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(HANDLE_STEP, ++step), Utils.SMS_DEFAULT_TIMEOUT);
                        } else {
                            mHandler.sendMessage(mHandler.obtainMessage(HANDLE_STEP, ++step));
                        }
                        break;
                    }
                    case 6: { // temp control
                        pd.setMessage(getString(R.string.setting_temp_mode));
                        if (mDevice.tempMode != null) {
                            String res = "*" + mDevice.details.password + "#" + (mDevice.tempMode == 1 ? "_th" : "_tb") + "#";
                            sendSms(res);
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(HANDLE_STEP, ++step), Utils.SMS_DEFAULT_TIMEOUT);
                        } else {
                            mHandler.sendMessage(mHandler.obtainMessage(HANDLE_STEP, ++step));
                        }
                        break;
                    }
                    case 7: { // report control
                        pd.setMessage(getString(R.string.setting_reports_mode));
                        if (mDevice.enableInfoReport != null || mDevice.enableTempReport != null) {
                            String res = "*" + mDevice.details.password + "#";
                            if (mDevice.enableInfoReport != null) {
                                res += mDevice.enableInfoReport ? "_infon" : "_infoff";
                            }
                            if (mDevice.enableTempReport != null) {
                                res += mDevice.enableTempReport ? "_tempon" : "_tempoff";
                            }
                            res += "#";
                            sendSms(res);
                            mHandler.sendEmptyMessageDelayed(HANDLE_RESET, Utils.SMS_DEFAULT_TIMEOUT);
                        } else {
                            mHandler.sendEmptyMessage(HANDLE_RESET);
                        }
                        break;
                    }
                }
                break;
            }
            case HANDLE_RESET: {
                if (hasSomethingChanged) {
                    pd.setMessage(getString(R.string.resetting_device));
                    sendSms("*" + mDevice.details.password + "#_fullrst#");
                    mHandler.sendEmptyMessageDelayed(HANDLE_FINISH, Utils.SMS_DEFAULT_TIMEOUT);
                } else {
                    mHandler.sendEmptyMessage(HANDLE_FINISH);
                }
                break;
            }
            case HANDLE_FINISH: {
                pd.dismiss();

                String IDStrings = mPrefs.getString("IDs", "");
                if (!IDStrings.contains(mDevice.details.number)) {
                    IDStrings = IDStrings + mDevice.details.number + ";";
                }

                SharedPreferences.Editor edit = mPrefs.edit();
                edit.putString("IDs", IDStrings);
                edit.putString(mDevice.details.number, new Gson().toJson(mDevice.details));
                edit.commit();

                // replace with finish
                // mSavedDevice = new Gson().fromJson(mPrefs.getString(mDevice.number, ""), Device.class);

                Intent editNow = new Intent(this, MainActivity.class).putExtra("ID", mDevice.details.number);
                startActivity(editNow);
                finish();
                break;
            }
            case HANDLE_FORCE_RESET: {
                pd = ProgressDialog.show(this, getString(R.string.wait_please), getString(R.string.resetting_device), true, false);
                sendSms("*" + mDevice.details.password + "#_fullrst#");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                    }
                }, Utils.SMS_DEFAULT_TIMEOUT);
                break;
            }
        }
        return true;
    }

    private void sendSms(String text) {
        PendingIntent sentPI = PendingIntent.getBroadcast(SettingsActivity.this, 0, new Intent(Utils.SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(SettingsActivity.this, 0, new Intent(Utils.DELIVERED), 0);
        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> splitted = sms.divideMessage(text);
        ArrayList<PendingIntent> sendIntents = new ArrayList<>(splitted.size());
        ArrayList<PendingIntent> deliverIntents = new ArrayList<>(splitted.size());
        for(int partIndex = 0; partIndex < splitted.size(); ++partIndex) {
            sendIntents.add(sentPI);
            deliverIntents.add(deliveredPI);
        }
        sms.sendMultipartTextMessage(mDevice.details.number, null, splitted, sendIntents, deliverIntents);
    }
}
