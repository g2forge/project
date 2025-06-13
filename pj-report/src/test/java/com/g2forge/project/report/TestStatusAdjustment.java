package com.g2forge.project.report;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;

import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.test.HAssert;

public class TestStatusAdjustment {
	@Test
	public void empty() {
		HAssert.assertEquals(HCollection.emptyList(), StatusAdjustment.parse(null));
		HAssert.assertEquals(HCollection.emptyList(), StatusAdjustment.parse(""));
		HAssert.assertEquals(HCollection.emptyList(), StatusAdjustment.parse("    "));
	}

	@Test
	public void multiple() {
		final List<StatusAdjustment> expected = HCollection.asList(new StatusAdjustment(LocalDateTime.of(2025, 6, 12, 13, 59), "statusA", "statusB"), new StatusAdjustment(LocalDateTime.of(2025, 6, 12, 14, 22), "statusB", "statusC"));
		HAssert.assertEquals(expected, StatusAdjustment.parse("* Status adjustment: statusA -> statusB at 2025-06-12 13:59\n* Status adjustment: statusB → statusC 2025-06-12 14:22"));
	}

	@Test
	public void simple() {
		final List<StatusAdjustment> expected = HCollection.asList(new StatusAdjustment(LocalDateTime.of(2025, 6, 12, 13, 59), "statusA", "statusB"));
		HAssert.assertEquals(expected, StatusAdjustment.parse("Status adjustment: statusA -> statusB at 2025-06-12 13:59"));
		// Many spaces are optional, matches are case insensitive, seconds are optional, to and -> are both valid
		HAssert.assertEquals(expected, StatusAdjustment.parse("StatusAdjustMENT statusA    to statusB    at2025-06-12 13:59:00"));
		// You can also use →, and both the arrow/"to" and "at" are optional
		HAssert.assertEquals(expected, StatusAdjustment.parse("Status adjustment: statusA → statusB 2025-06-12 13:59"));
		HAssert.assertEquals(expected, StatusAdjustment.parse("Status adjustment: statusA statusB 2025-06-12 13:59"));
	}
}
