package com.ryosoftware.calls_blocker.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ScheduleRulesSection(
    scheduleRules: List<ScheduleRule>,
    onAddRule: () -> Unit,
    onEditRule: (ScheduleRule) -> Unit,
    onRemoveRule: (ScheduleRule) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.schedule_blocking_title),
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = stringResource(R.string.schedule_blocking_description),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            if (scheduleRules.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_blocking_no_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.status_inactive_text)
                )
            } else {
                scheduleRules.forEach { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getScheduleRuleString(context, rule),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onEditRule(rule) }
                        )

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { onRemoveRule(rule) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = onAddRule,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.find_my_phone_add),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun getScheduleRuleString(context: Context, rule: ScheduleRule): String {
    val weekDays = context.resources.getStringArray(R.array.week_day_names)
    val startTime = LocalTime.ofSecondOfDay(rule.startMinute * 60L)
    val formattedStartTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(startTime)

    val endTime = LocalTime.ofSecondOfDay(rule.endMinute * 60L)
    val formattedEndTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(endTime)

    val startWeekDayAndTime = context.resources.getQuantityString(
        R.plurals.weekday_and_time,
        rule.startMinute / 60,
        weekDays[rule.startDay - 1],
        formattedStartTime
    )
    val endWeekDayAndTime = context.resources.getQuantityString(
        R.plurals.weekday_and_time,
        rule.endMinute / 60,
        weekDays[rule.endDay - 1],
        formattedEndTime
    )

    return context.resources.getString(
        R.string.schedule_blocking_period,
        startWeekDayAndTime,
        endWeekDayAndTime
    )
}
