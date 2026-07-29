package com.example.file_management.util;

import java.util.Calendar;
import java.util.Date;

public class InfinityDateTime {
    public static Date getInfinityDate(){
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR,2099);
        calendar.set(Calendar.MONTH,12);
        calendar.set(Calendar.DAY_OF_MONTH,31);
        calendar.set(Calendar.HOUR_OF_DAY,23);
        calendar.set(Calendar.MINUTE,59);
        calendar.set(Calendar.SECOND,59);

        return calendar.getTime();
    }
}
