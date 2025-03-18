package com.g2forge.project.report;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.test.HAssert;
import com.g2forge.gearbox.jira.fields.KnownField;
import com.g2forge.project.report.Billing.StatusChange;

public class TestBilling {
	protected static final ZonedDateTime START = ZonedDateTime.parse("2025-01-01T13:00:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime END = ZonedDateTime.parse("2025-01-01T14:00:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_MINUS20 = ZonedDateTime.parse("2025-01-01T12:40:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_PLUS20 = ZonedDateTime.parse("2025-01-01T13:20:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_PLUS40 = ZonedDateTime.parse("2025-01-01T13:40:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime END_PLUS20 = ZonedDateTime.parse("2025-01-01T14:20:00-07:00[America/Los_Angeles]");

	protected ChangelogGroup change(ZonedDateTime when, String fromStatus, String toStatus) {
		return new ChangelogGroup(null, Billing.convert(when), HCollection.asList(new ChangelogItem(null, KnownField.Status.getName(), fromStatus, fromStatus, toStatus, toStatus)));
	}

	@Test
	public void testConvertToStatusChangesAllAfter() {
		final List<StatusChange> actual = Billing.convertToStatusChanges(HCollection.asList(change(END_PLUS20, "State", "Ignored")), START, END, "Ignored");
		HAssert.assertEquals(HCollection.asList(new StatusChange(START, "State"), new StatusChange(END, "State")), actual);
	}

	@Test
	public void testConvertToStatusChangesAllBefore() {
		final List<StatusChange> actual = Billing.convertToStatusChanges(HCollection.asList(change(START_MINUS20, "Ignored", "State")), START, END, "State");
		HAssert.assertEquals(HCollection.asList(new StatusChange(START, "State"), new StatusChange(END, "State")), actual);
	}

	@Test
	public void testConvertToStatusChangesEmpty() {
		final List<StatusChange> actual = Billing.convertToStatusChanges(HCollection.emptyList(), START, END, "State");
		HAssert.assertEquals(HCollection.asList(new StatusChange(START, "State"), new StatusChange(END, "State")), actual);
	}

	@Test
	public void testConvertToStatusChangesOne() {
		final List<StatusChange> actual = Billing.convertToStatusChanges(HCollection.asList(change(START_PLUS20, "Initial", "Final")), START, END, "Final");
		HAssert.assertEquals(HCollection.asList(new StatusChange(START, "Initial"), new StatusChange(START_PLUS20, "Final"), new StatusChange(END, "Final")), actual);
	}

	@Test
	public void testConvertToStatusChangesTwo() {
		final List<StatusChange> actual = Billing.convertToStatusChanges(HCollection.asList(change(START_PLUS20, "Initial", "Middle"), change(START_PLUS40, "Middle", "Final")), START, END, "Final");
		HAssert.assertEquals(HCollection.asList(new StatusChange(START, "Initial"), new StatusChange(START_PLUS20, "Middle"), new StatusChange(START_PLUS40, "Final"), new StatusChange(END, "Final")), actual);
	}
}
