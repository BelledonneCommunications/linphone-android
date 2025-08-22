package org.linphone.utils

import android.annotation.SuppressLint
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle

class DateUtils {
    companion object {
        private val _todaysDate = BehaviorSubject.createDefault(getToday())
        val todaysDate: Observable<LocalDateTime> = _todaysDate.hide()

        private fun getToday(): LocalDateTime {
            return LocalDate.now().atStartOfDay()
        }

        @SuppressLint("CheckResult")
        private fun checkDate() {
            // If necessary, update today's date.
            val date = getToday()
            if (date != _todaysDate.value) _todaysDate.onNext(date)

            // Set a timer for when the hour next changes to re-check the current date.
            val delay = (60 - Calendar.getInstance().get(Calendar.MINUTE)) * 60000L
            Log.d("Check date in $delay ms")
            Observable.timer(delay, TimeUnit.MILLISECONDS).subscribe { checkDate() }
        }

        /** Formats the date string as a user-friendly string. */
        fun formatFriendlyDate(
            dateTime: ZonedDateTime?,
            localDateTime: LocalDateTime,
            useLastWeek: Boolean = false
        ): String {
            try {
                if (dateTime == null) return ""

                checkDate()

                val midnightDate = dateTime.toLocalDateTime().toLocalDate().atStartOfDay()
                val midnightToday = localDateTime.toLocalDate().atStartOfDay()

                if (midnightDate == midnightToday) return ""

                val yesterday = midnightToday?.minusDays(1)
                val aWeekAgo = midnightToday?.minusDays(7)
                val twoWeeksAgo = midnightToday?.minusDays(14)

                // Create a localized formatter
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(
                    Locale.getDefault()
                )

                return when {
                    midnightDate == midnightToday -> ""
                    midnightDate == yesterday -> "Yesterday"
                    midnightDate > aWeekAgo -> getDayName(dateTime.dayOfWeek)
                    useLastWeek && midnightDate > twoWeeksAgo -> "Last week"
                    else -> dateTime.format(formatter)
                }
            } catch (e: Exception) {
                Log.e("formatFriendlyDate", e)
                return ""
            }
        }

        fun toLocaleHMString(dateTime: ZonedDateTime?): String {
            try {
                val hmsString = dateTime?.toLocalDateTime().let {
                    it?.format(DateTimeFormatter.ofPattern("HH:mm"))
                }

                if (hmsString != null) {
                    return hmsString
                }

                return ""
            } catch (e: Exception) {
                Log.e("toLocaleHMString", e)
                return ""
            }
        }

        private fun getDayName(dayIndex: DayOfWeek): String {
            return when (dayIndex) {
                DayOfWeek.SUNDAY -> "Sunday"
                DayOfWeek.MONDAY -> "Monday"
                DayOfWeek.TUESDAY -> "Tuesday"
                DayOfWeek.WEDNESDAY -> "Wednesday"
                DayOfWeek.THURSDAY -> "Thursday"
                DayOfWeek.FRIDAY -> "Friday"
                DayOfWeek.SATURDAY -> "Saturday"
                else -> throw IllegalArgumentException("Invalid day index")
            }
        }

        fun secondsToHms(totalSeconds: Int, alwaysIncludeHours: Boolean = true): String {
            var seconds = totalSeconds
            val days = seconds / 86400
            seconds %= 86400
            val hours = seconds / 3600
            seconds %= 3600
            val minutes = seconds / 60
            seconds %= 60

            val dayString = if (days > 0) "${days}d " else ""
            val hoursString = if (alwaysIncludeHours || hours > 0) "${padLeft(hours)}:" else ""

            return "$dayString$hoursString${padLeft(minutes)}:${padLeft(seconds)}"
        }

        private fun padLeft(value: Int): String {
            return value.toString().padStart(2, '0')
        }
    }
}
