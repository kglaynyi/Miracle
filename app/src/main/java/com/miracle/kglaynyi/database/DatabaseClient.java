package com.miracle.kglaynyi.database;

import android.content.Context;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DatabaseClient {

    private static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE IndexLink ADD COLUMN selectedFoldersJson TEXT");
        }
    };

    private static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Movie ADD COLUMN folder_path TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE Episode ADD COLUMN folder_path TEXT DEFAULT ''");
        }
    };

    private Context mCtx;
    private static DatabaseClient mInstance;

    //our app database object
    private AppDatabase appDatabase;

    private DatabaseClient(Context mCtx) {
        this.mCtx = mCtx;

        //creating the app database with Room database builder
        //MyToDos is the name of the database
        appDatabase = Room.databaseBuilder(mCtx, AppDatabase.class, "MyToDos")
                .addMigrations(MIGRATION_30_31, MIGRATION_31_32)
                .build();
    }

    public static synchronized DatabaseClient getInstance(Context mCtx) {
        if (mInstance == null) {
            mInstance = new DatabaseClient(mCtx);
        }
        return mInstance;
    }

    public AppDatabase getAppDatabase() {
        return appDatabase;
    }
}