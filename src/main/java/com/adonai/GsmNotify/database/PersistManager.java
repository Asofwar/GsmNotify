package com.adonai.GsmNotify.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.adonai.GsmNotify.Device;
import com.adonai.GsmNotify.R;
import com.adonai.GsmNotify.SMSReceiveService;
import com.adonai.GsmNotify.Utils;
import com.adonai.GsmNotify.entities.HistoryEntry;
import com.google.gson.Gson;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static com.adonai.GsmNotify.SMSReceiveService.PREFERENCES;

/**
 * Created by adonai on 29.06.14.
 */
public class PersistManager extends OrmLiteSqliteOpenHelper {

    private static final String TAG = PersistManager.class.getSimpleName();

    //имя файла базы данных который будет храниться в /data/data/APPNAME/DATABASE_NAME
    private static final String DATABASE_NAME ="devices.db";

    //с каждым увеличением версии, при нахождении в устройстве БД с предыдущей версией будет выполнен метод onUpgrade();
    private static final int DATABASE_VERSION = 2;

    //ссылки на DAO соответсвующие сущностям, хранимым в БД
    private Dao<HistoryEntry, Long> historyDao = null;
    private final Context mContext;

    public PersistManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    //Выполняется, когда файл с БД не найден на устройстве
    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, HistoryEntry.class);
        } catch (SQLException e) {
            Log.e(TAG, "error creating DB " + DATABASE_NAME);
            throw new RuntimeException(e);
        }
    }

    //Выполняется, когда БД имеет версию отличную от текущей
    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVer, int newVer) {
        SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        
        switch (oldVer) {
            case 1:
                db.execSQL("ALTER TABLE history ADD COLUMN archived BOOLEAN");
                db.execSQL("ALTER TABLE history ADD COLUMN status INTEGER");
                
                String[] IDs = preferences.getString("IDs", "").split(";");
                for (String deviceNumber : IDs) {
                    String gson = preferences.getString(deviceNumber, null);
                    Device.CommonSettings settings = new Gson().fromJson(gson, Device.CommonSettings.class);
                    if(settings != null) {
                        String displayName = Utils.getDisplayName(settings);
                        List<HistoryEntry> saved = getHistoryDao().queryForEq("deviceName", displayName);
                        for (HistoryEntry he : saved) {
                            he.setStatus(Utils.getStatusBySms(mContext, settings, he.getSmsText().toLowerCase()));
                            he.setArchived(he.getSmsText().contains(mContext.getString(R.string.archive_suffix)));
                            getHistoryDao().update(he);
                        }
                    }
                }
                break;
        }
    }

    public RuntimeExceptionDao<HistoryEntry, Long> getHistoryDao() {
        if(historyDao == null)
            try {
                historyDao = getDao(HistoryEntry.class);
            } catch (SQLException e) {
                throw new RuntimeException(e); // should not happen
            }
        return new RuntimeExceptionDao<>(historyDao);
    }

    //выполняется при закрытии приложения
    @Override
    public void close() {
        super.close();
    }
}
