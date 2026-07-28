package imb.tzalarmclock.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room representation of an alarm.
 *
 * The schedule is flattened into a discriminator plus one payload column per
 * variant, with only the column relevant to [scheduleType] populated. Storing
 * the payload in plain columns (rather than a serialized blob) keeps the
 * schema queryable and diffable across the migrations later stages will need.
 */
@Entity(tableName = AlarmEntity.TABLE_NAME)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** Wall-clock time of day as minutes since midnight, `0..1439`. */
    @ColumnInfo(name = "minute_of_day")
    val minuteOfDay: Int,

    /** IANA zone id, or `null` for a floating alarm. */
    @ColumnInfo(name = "zone_id")
    val zoneId: String?,

    /** Name of a `ScheduleType` constant. */
    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,

    /** Epoch day, set only for `ONE_TIME_DATE`. */
    @ColumnInfo(name = "schedule_date")
    val scheduleDate: Long? = null,

    /**
     * Weekday bitmask, set only for `WEEKLY`. Bit `n` is `DayOfWeek.of(n + 1)`,
     * so bit 0 is Monday and bit 6 is Sunday.
     */
    @ColumnInfo(name = "schedule_weekdays")
    val scheduleWeekdays: Int? = null,

    /** Comma-separated ascending days of month, set only for `MONTHLY`. */
    @ColumnInfo(name = "schedule_month_days")
    val scheduleMonthDays: String? = null,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    /** Alarm-specific ringtone, or `null` to use the app default. */
    @ColumnInfo(name = "ringtone_uri")
    val ringtoneUri: String? = null,

    /** Alarm-specific vibration setting, or `null` to use the app default. */
    @ColumnInfo(name = "vibrate")
    val vibrate: Boolean? = null,
) {
    companion object {
        const val TABLE_NAME = "alarms"
    }
}
