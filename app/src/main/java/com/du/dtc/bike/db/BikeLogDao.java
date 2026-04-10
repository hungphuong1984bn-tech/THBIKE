package com.du.dtc.bike.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface BikeLogDao {
    @Insert
    void insertLog(BikeLogEntity log);

    @Query("SELECT * FROM bike_logs WHERE timestamp >= :startTs AND timestamp <= :endTs ORDER BY timestamp ASC")
    List<BikeLogEntity> getLogsByTimeRange(long startTs, long endTs);

    // Lấy theo khoảng thời gian (ms)
    @Query("SELECT * FROM bike_logs WHERE timestamp >= :fromMs ORDER BY timestamp ASC")
    List<BikeLogEntity> getLogsSince(long fromMs);

    @Query("DELETE FROM bike_logs WHERE timestamp < :beforeMs")
    void deleteOlderThan(long beforeMs);
}
