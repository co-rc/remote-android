package org.jbanaszczyk.corc.db;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.jbanaszczyk.corc.ble.BleTypeConverters;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

@Database(
        entities = {
                BleDevicePersistent.class,
                VersionEntity.class
        },
        version = 2,
        exportSchema = false
)
@TypeConverters({BleTypeConverters.class})
public abstract class CorcDatabase extends RoomDatabase {

    public static final int CURRENT_DB_VERSION = 2;
    private static final String LOG_TAG = "CORC:DB";

    // Enum-based singleton holder for thread-safe, serialization-safe singleton
    private enum DbSingleton {
        INSTANCE;

        private volatile CorcDatabase db;

        @NonNull
        private CorcDatabase build(@NonNull Context context) {
            Log.i(LOG_TAG, "Building Room database 'corc.db'.");
            return Room.databaseBuilder(
                            context.getApplicationContext(),
                            CorcDatabase.class,
                            "corc.db"
                    )
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            Log.i(LOG_TAG, "Room onCreate: initializing version table to " + CURRENT_DB_VERSION + ".");
                            db.execSQL("INSERT OR REPLACE INTO version (id, db_version) VALUES (1, " + CURRENT_DB_VERSION + ")");
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            Log.d(LOG_TAG, "Room onOpen invoked.");
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build();
        }

        @NonNull
        synchronized CorcDatabase get(@NonNull Context context) {
            if (db == null) {
                db = build(context);
                // Run version check off the main thread to avoid main-thread Room access
                new Thread(() -> {
                    try {
                        ensureVersion(context, this);
                    } catch (Throwable t) {
                        Log.e(LOG_TAG, "Background database version check failed", t);
                    }
                }, "corc-db-version-check").start();
            }
            return db;
        }

        synchronized void recreate(@NonNull Context context) {
            CorcDatabase oldDb = db;
            if (oldDb != null) {
                Log.i(LOG_TAG, "Closing current DB instance before recreation.");
                try {
                    oldDb.close();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error closing database during recreation", e);
                }
            }
            Log.w(LOG_TAG, "Deleting database file 'corc.db' and recreating from scratch.");
            context.deleteDatabase("corc.db");
            db = build(context);
            // Initialize version row in the new database
            db.runInTransaction(() -> db.versionDao().insertOrReplace(new VersionEntity(CURRENT_DB_VERSION)));
            Log.i(LOG_TAG, "Database recreated and version initialized to " + CURRENT_DB_VERSION + ".");
        }
    }

    public abstract BleDeviceDao bleDeviceDao();

    public abstract VersionDao versionDao();

    @NonNull
    public static CorcDatabase getInstance(@NonNull Context context) {
        return DbSingleton.INSTANCE.get(context);
    }

    private static void ensureVersion(@NonNull Context context, @NonNull DbSingleton holder) {
        CorcDatabase currentDb = holder.db;
        if (currentDb == null) {
            Log.w(LOG_TAG, "ensureVersion: currentDb is null, skipping check.");
            return;
        }

        try {
            VersionDao versionDao = currentDb.versionDao();
            VersionEntity version = versionDao.getSingleton();
            if (version == null) {
                // Fresh database: set version and ensure empty devices list
                Log.i(LOG_TAG, "Fresh database detected. Initializing version to " + CURRENT_DB_VERSION + " and clearing devices table.");
                currentDb.runInTransaction(() -> {
                    currentDb.bleDeviceDao().deleteAll();
                    currentDb.versionDao().insertOrReplace(new VersionEntity(CURRENT_DB_VERSION));
                });
                return;
            }

            if (version.getDbVersion() != CURRENT_DB_VERSION) {
                // Version mismatch: remove the entire database file and recreate from scratch
                Log.w(LOG_TAG, "Database version mismatch detected: stored=" + version.getDbVersion() + ", expected=" + CURRENT_DB_VERSION + ". Triggering recreation.");
                holder.recreate(context);
            } else {
                Log.d(LOG_TAG, "Database version check passed: " + version.getDbVersion());
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error during database version check", e);
            // Optionally we could trigger recreate(context) here if we suspect the database is corrupted
        }
    }
}
