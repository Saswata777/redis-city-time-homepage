package org.example.util;

import java.time.LocalTime;

public class TimeUtil {
    public static boolean isStoreOpen(String open, String close, String current){
        LocalTime openTime = LocalTime.parse(open);
        LocalTime closeTime = LocalTime.parse(close);
        LocalTime currentTime = LocalTime.parse(current);

        if(openTime.equals(closeTime)) return true;
        if(openTime.isBefore(closeTime)){
            return !currentTime.isBefore(openTime) && !currentTime.isAfter(closeTime);
        }
        return !currentTime.isBefore(openTime) || !currentTime.isAfter(closeTime);
    }
}
