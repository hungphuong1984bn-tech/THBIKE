package com.du.dtc.bike.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;

@Entity(tableName = "bike_logs")
public class BikeLogEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long timestamp;
    public int speed;
    public double odo;
    
    // Battery and Power
    public double voltage;
    public double current;
    public double soc;
    
    // Temperatures
    public double tempBalanceReg;
    public double tempFet;
    public double tempPin1;
    public double tempPin2;
    public double tempPin3;
    public double tempPin4;
    public double tempMotor;
    public double tempController;

    // Cells
    public List<Double> cellVoltages;
}
