package com.g2forge.project.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class WorkingHours {
	protected final Set<DayOfWeek> workdays;

	protected final ZoneId zone;

	protected final LocalTime start, end;

	public double computeBillableHours(ZonedDateTime start, ZonedDateTime end) {
		final LocalDate startDate = start.withZoneSameInstant(getZone()).toLocalDate();
		final LocalDate endDate = end.withZoneSameInstant(getZone()).toLocalDate();

		double retVal = 0.0;
		for (LocalDate current = startDate; !current.isAfter(endDate); current = current.plus(1, ChronoUnit.DAYS)) {
			final DayOfWeek dayOfWeek = current.getDayOfWeek();
			if (!getWorkdays().contains(dayOfWeek)) continue;

			final ZonedDateTime workdayStart = getStart().atDate(current).atZone(zone);
			final ZonedDateTime workdayEnd = getEnd().atDate(current).atZone(zone);

			final ZonedDateTime dayStart = workdayStart.isAfter(start) ? workdayStart : start;
			final ZonedDateTime dayEnd = workdayEnd.isBefore(end) ? workdayEnd : end;
			final long seconds = ChronoUnit.SECONDS.between(dayStart, dayEnd);
			if (seconds > 0) retVal += seconds / (60.0 * 60.0);
		}
		return retVal;
	}
}