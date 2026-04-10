package com.du.dtc.bike.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {BikeLogEntity.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class BikeDatabase extends RoomDatabase {
    private static volatile BikeDatabase INSTANCE;

    public abstract BikeLogDao bikeLogDao();

    public static BikeDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (BikeDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            BikeDatabase.class, "bike_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
