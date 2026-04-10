package com.du.dtc.bike.db;

import androidx.room.TypeConverter;

import java.util.ArrayList;
import java.util.List;

public class Converters {
    @TypeConverter
    public static List<Double> fromString(String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();
        String[] parts = value.split(",");
        List<Double> list = new ArrayList<>();
        for (String part : parts) {
            try {
                list.add(Double.parseDouble(part.trim()));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return list;
    }

    @TypeConverter
    public static String fromList(List<Double> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
}
