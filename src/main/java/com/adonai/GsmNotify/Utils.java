package com.adonai.GsmNotify;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.util.Collection;
import java.util.Iterator;

public class Utils {

    static final int PERMISSIONS_REQUEST_CODE = 0;

    final static String SENT = "SMS_SENT_NOTIFY_MAIN";
    final static String DELIVERED = "SMS_DELIVERED_NOTIFY_MAIN";

    final static int SMS_DEFAULT_TIMEOUT = 20000;
    final static int SMS_ROUNDTRIP_TIMEOUT = 90000;

    public enum DeviceStatus {
        ARMED,
        DISARMED,
        ALARM,
        KEY,
        UNKNOWN
    }

    static String join(Collection<String> s, String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iter = s.iterator();
        while (iter.hasNext()) {
            String current = iter.next();
            if (current.isEmpty()) {
                continue;
            }

            builder.append(current);

            if (!iter.hasNext()) {
                break;
            }
            builder.append(delimiter);
        }
        return builder.toString();
    }

    public static boolean isTablet(Activity context) {
        return false;
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent px equivalent to dp depending on device density
     */
    public static float convertDpToPixel(float dp, Context context){
        final Resources resources = context.getResources();
        final DisplayMetrics metrics = resources.getDisplayMetrics();
        return dp * (metrics.densityDpi / 160f);
    }

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent dp equivalent to px value
     */
    public static float convertPixelsToDp(float px, Context context){
        final Resources resources = context.getResources();
        final DisplayMetrics metrics = resources.getDisplayMetrics();
        return px / (metrics.densityDpi / 160f);
    }

    public static DeviceStatus getStatusBySms(Context context, Device.CommonSettings details, String lowercaseSms) {
        // тревоги по питанию
        if (lowercaseSms.contains(context.getString(R.string.disarmed_power_matcher))) { // disable power alarm
            return DeviceStatus.DISARMED;
        } else if (lowercaseSms.contains(context.getString(R.string.alarm_power_matcher))) { // power alarm
            return DeviceStatus.ALARM;
        }

        // сначала проверка "на охране/снято с охраны", если "снято",
        // то цвет ячейки зеленый (несмотря на тревоги по шлейфам),
        // если "на охране", то смотрим шлейфы, если по любому шлейфу "тревога",
        // то цвет ячейки красный, если "на охране" и нет тревог, то цвет ячейки желтый.
        if(details.isGsmQaud) { // GSM Qaud
            if (lowercaseSms.contains(context.getString(R.string.disarmed_matcher))) { // disarmed
                return DeviceStatus.DISARMED;
            } else if (lowercaseSms.contains(context.getString(R.string.armed_matcher))) { // armed
                boolean anyBusHasAlarm = lowercaseSms.contains(context.getString(R.string.armed_bus_alarm_matcher));
                return anyBusHasAlarm ? DeviceStatus.ALARM : DeviceStatus.ARMED; // either alarm on bus or armed
            } else if (lowercaseSms.startsWith(context.getString(R.string.alarm_matcher_qaud)) // alarm
                    || lowercaseSms.contains(context.getString(R.string.warning_matcher_qaud))) {
                return DeviceStatus.ALARM;
            } else {
                return DeviceStatus.UNKNOWN;
            }
        } else { // Signal XM/XL
            if (lowercaseSms.contains(context.getString(R.string.disarmed_matcher))) { // disarmed
                return DeviceStatus.DISARMED;
            } else if (lowercaseSms.contains(context.getString(R.string.armed_matcher))) { // armed
                boolean anyBusHasAlarm = lowercaseSms.contains(context.getString(R.string.armed_bus_alarm_matcher));
                return anyBusHasAlarm ? DeviceStatus.ALARM : DeviceStatus.ARMED; // either alarm on bus or armed
            } else if (lowercaseSms.contains(context.getString(R.string.alarm_matcher))) {
                return DeviceStatus.ALARM;
            } else {
                return DeviceStatus.UNKNOWN;
            }
        }
    }

    /**
     * Checks for permission and requests it if needed.
     * You should catch answer back in {@link Activity#onRequestPermissionsResult(int, String[], int[])}
     * <br/>
     * (Or don't. This way request will appear forever as {@link Activity#onResume()} will never end)
     * @param perm permission to request
     * @return true if this app had this permission prior to check, false otherwise.
     */
    public static boolean checkAndRequestPermissions(Activity ctx, String perm) {
        if (!havePermissions(ctx, perm)  && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ctx.requestPermissions(new String[]{perm}, PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    /**
     * Checks if all required permissions have been granted
     *
     * @param context The context to use
     * @return boolean true if all permissions have been granded
     */
    public static boolean havePermissions(Context context, String perm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        } // else: granted during installation
        return true;
    }

    public static String getDisplayName(Device.CommonSettings details) {
        if (details == null) {
            return "";
        }
        if (!TextUtils.isEmpty(details.name)) {
            return details.name;
        }
        return details.number != null ? details.number : "";
    }
}
