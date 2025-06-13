package com.g2forge.project.report;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.test.HAssert;
import com.g2forge.gearbox.jira.fields.KnownField;

public class TestChange {
	protected static final ZonedDateTime START = ZonedDateTime.parse("2025-01-01T13:00:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime END = ZonedDateTime.parse("2025-01-01T14:00:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_MINUS20 = ZonedDateTime.parse("2025-01-01T12:40:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_PLUS15 = ZonedDateTime.parse("2025-01-01T13:15:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_PLUS30 = ZonedDateTime.parse("2025-01-01T13:30:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime START_PLUS45 = ZonedDateTime.parse("2025-01-01T13:45:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime END_PLUS20 = ZonedDateTime.parse("2025-01-01T14:20:00-07:00[America/Los_Angeles]");
	protected static final ZonedDateTime END_PLUS40 = ZonedDateTime.parse("2025-01-01T14:40:00-07:00[America/Los_Angeles]");

	protected ChangelogGroup changeAssignee(ZonedDateTime when, String fromAssignee, String toAssignee) {
		return new ChangelogGroup(null, Billing.convert(when), HCollection.asList(new ChangelogItem(null, KnownField.Assignee.getName(), fromAssignee, fromAssignee, toAssignee, toAssignee)));
	}

	protected ChangelogGroup changeStatus(ZonedDateTime when, String fromStatus, String toStatus) {
		return new ChangelogGroup(null, Billing.convert(when), HCollection.asList(new ChangelogItem(null, KnownField.Status.getName(), fromStatus, fromStatus, toStatus, toStatus)));
	}

	@Test
	public void toChangesAllAfter() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(END_PLUS20, "State", "Ignored")), START, END, "user", "Ignored", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "State"), new Change(END, "user", "State")), actual);
	}

	@Test
	public void toChangesDoubleAfter() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(END_PLUS20, "State", "Ignored1"), changeStatus(END_PLUS40, "Ignored1", "Ignored2")), START, END, "user", "Ignored", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "State"), new Change(END, "user", "State")), actual);
	}

	@Test
	public void toChangesAllBefore() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(START_MINUS20, "Ignored", "State")), START, END, "user", "State", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "State"), new Change(END, "user", "State")), actual);
	}

	@Test
	public void toChangesEmpty() {
		final List<Change> actual = Change.toChanges(HCollection.emptyList(), START, END, "user", "State", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "State"), new Change(END, "user", "State")), actual);
	}

	@Test
	public void toChangesOne() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(START_PLUS15, "Initial", "Final")), START, END, "user", "Final", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "Initial"), new Change(START_PLUS15, "user", "Final"), new Change(END, "user", "Final")), actual);
	}

	@Test
	public void toChangesAdjusted() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(START_PLUS15, "Initial", "Final"), changeStatus(START_PLUS30, "Initial", "Final")), START, END, "user", "Final", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "Initial"), new Change(START_PLUS15, "user", "Final"), new Change(END, "user", "Final")), actual);
	}

	@Test
	public void toChangesThree() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(START_PLUS15, "Initial", "Middle"), changeAssignee(START_PLUS30, "user1", "user2"), changeStatus(START_PLUS45, "Middle", "Final")), START, END, "user2", "Final", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user1", "Initial"), new Change(START_PLUS15, "user1", "Middle"), new Change(START_PLUS30, "user2", "Middle"), new Change(START_PLUS45, "user2", "Final"), new Change(END, "user2", "Final")), actual);
	}

	@Test
	public void toChangesTwo() {
		final List<Change> actual = Change.toChanges(HCollection.asList(changeStatus(START_PLUS15, "Initial", "Middle"), changeStatus(START_PLUS45, "Middle", "Final")), START, END, "user", "Final", IFunction1.identity());
		HAssert.assertEquals(HCollection.asList(new Change(START, "user", "Initial"), new Change(START_PLUS15, "user", "Middle"), new Change(START_PLUS45, "user", "Final"), new Change(END, "user", "Final")), actual);
	}
}
