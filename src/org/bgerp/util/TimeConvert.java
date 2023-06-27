package org.bgerp.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

/**
 * Date ant time converting utils.
 *
 * @author Shamil Vakhitov
 */
public class TimeConvert {
    /**
     * Converts date to month of year.
     * @param value
     * @return {@code null} or month, containing {@code value}.
     */
    public static final YearMonth toYearMonth(Date value) {
        return
            value != null ?
            YearMonth.from(value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) :
            null;
    }

    /**
     * Converts the first day of a month to date.
     * @param value the month.
     * @return {@code null} or date of the first day of the month {@code value}.
     */
    public static final Date toDate(YearMonth value) {
        return
            value != null ?
            Date.from(value.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()) :
            null;
    }

    /**
     * Converts beginning of a day to date.
     * @param value the day.
     * @return {@code null} or date of the beginning of the day {@code value}.
     */
    public static final Date toDate(LocalDate value) {
        return
            value != null ?
            Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant()) :
            null;
    }

    /**
     * Converts date to local date.
     * @param value
     * @return {@code null} or converted value.
     */
    public static final LocalDate toLocalDate(Date value) {
        if (value == null)
            return null;

        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Converts date to SQL timestamp.
     * @param value
     * @return {@code null} or converted value.
     */
    public static final java.sql.Timestamp toTimestamp(Date value) {
        if (value == null)
            return null;
        return new java.sql.Timestamp(value.getTime());
    }
}
