package com.g2forge.project.report;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;

import org.junit.Test;

import com.g2forge.alexandria.test.HAssert;

public class TestWorkingHours {
	@Test
	public void inner() {
		final WorkingHours workingHours = new WorkingHours(EnumSet.complementOf(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), ZoneId.of("America/Los_Angeles"), LocalTime.parse("07:00:00"), LocalTime.parse("15:00:00"));
		final double actual = workingHours.computeBillableHours(ZonedDateTime.parse("2025-03-18T08:00:00-07:00[America/Los_Angeles]"), ZonedDateTime.parse("2025-03-18T14:00:00-07:00[America/Los_Angeles]"));
		HAssert.assertEquals(6, actual, 0.0);
	}

	@Test
	public void outer() {
		final WorkingHours workingHours = new WorkingHours(EnumSet.complementOf(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), ZoneId.of("America/Los_Angeles"), LocalTime.parse("07:00:00"), LocalTime.parse("15:00:00"));
		final double actual = workingHours.computeBillableHours(ZonedDateTime.parse("2025-03-18T06:00:00-07:00[America/Los_Angeles]"), ZonedDateTime.parse("2025-03-18T16:00:00-07:00[America/Los_Angeles]"));
		HAssert.assertEquals(8, actual, 0.0);
	}

	@Test
	public void early() {
		final WorkingHours workingHours = new WorkingHours(EnumSet.complementOf(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), ZoneId.of("America/Los_Angeles"), LocalTime.parse("07:00:00"), LocalTime.parse("15:00:00"));
		final double actual = workingHours.computeBillableHours(ZonedDateTime.parse("2025-03-18T06:00:00-07:00[America/Los_Angeles]"), ZonedDateTime.parse("2025-03-18T14:00:00-07:00[America/Los_Angeles]"));
		HAssert.assertEquals(7, actual, 0.0);
	}

	@Test
	public void late() {
		final WorkingHours workingHours = new WorkingHours(EnumSet.complementOf(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), ZoneId.of("America/Los_Angeles"), LocalTime.parse("07:00:00"), LocalTime.parse("15:00:00"));
		final double actual = workingHours.computeBillableHours(ZonedDateTime.parse("2025-03-18T08:00:00-07:00[America/Los_Angeles]"), ZonedDateTime.parse("2025-03-18T16:00:00-07:00[America/Los_Angeles]"));
		HAssert.assertEquals(7, actual, 0.0);
	}

	@Test
	public void split() {
		final WorkingHours workingHours = new WorkingHours(EnumSet.complementOf(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), ZoneId.of("America/Los_Angeles"), LocalTime.parse("07:00:00"), LocalTime.parse("15:00:00"));
		final double actual = workingHours.computeBillableHours(ZonedDateTime.parse("2025-03-14T12:00:00-07:00[America/Los_Angeles]"), ZonedDateTime.parse("2025-03-18T12:00:00-07:00[America/Los_Angeles]"));
		HAssert.assertEquals(16, actual, 0.0);
	}
}
