package com.adonai.GsmNotify.settings;

import androidx.fragment.app.Fragment;

import com.adonai.GsmNotify.Device;

import java.lang.reflect.Method;

public abstract class SettingsFragment extends Fragment {
    protected Device mSource;

    @SuppressWarnings("unchecked")
    public static <T> T getValue(String value, T defaultValue) {
        T result;
        try {

            final Method valueOf = defaultValue.getClass().getMethod("valueOf", String.class);
            result = (T) valueOf.invoke(null, value);
            return result;
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }
}
