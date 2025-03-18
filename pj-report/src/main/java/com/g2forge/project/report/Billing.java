package com.g2forge.project.report;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.event.Level;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.core.helpers.HCollector;
import com.g2forge.alexandria.java.io.dataaccess.PathDataSource;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.gearbox.argparse.ArgumentParser;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JiraAPI;
import com.g2forge.gearbox.jira.fields.KnownField;
import com.g2forge.project.core.HConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Billing implements IStandardCommand {
	@Data
	@Builder(toBuilder = true)
	@AllArgsConstructor
	protected static class Arguments {
		protected final String issueKey;

		protected final Path request;
	}

	@Data
	@Builder(toBuilder = true)
	@RequiredArgsConstructor
	protected static class StatusChange {
		protected final ZonedDateTime start;

		protected final String status;
	}

	public static ZonedDateTime convert(DateTime dateTime) {
		final Instant instant = Instant.ofEpochMilli(dateTime.getMillis());
		final ZoneId zoneId = ZoneId.of(dateTime.getZone().getID(), ZoneId.SHORT_IDS);
		return ZonedDateTime.ofInstant(instant, zoneId);
	}

	public static DateTime convert(ZonedDateTime zonedDateTime) {
		final long millis = zonedDateTime.toInstant().toEpochMilli();
		final DateTimeZone dateTimeZone = DateTimeZone.forID(zonedDateTime.getZone().getId());
		return new DateTime(millis, dateTimeZone);
	}

	protected static List<StatusChange> convertToStatusChanges(final Iterable<ChangelogGroup> changelog, ZonedDateTime start, ZonedDateTime end, String status) {
		final List<StatusChange> retVal = new ArrayList<>();
		String finalStatus = status;
		for (ChangelogGroup changelogGroup : changelog) {
			final ZonedDateTime created = convert(changelogGroup.getCreated());
			// Ignore changes before the start, and stop processing after the end
			if (created.isBefore(start)) continue;

			// Extract the from and to status from any changes to the status field (take the last change if there are multiple which should never happen)
			String fromStatus = null, toStatus = null;
			for (ChangelogItem changelogItem : changelogGroup.getItems()) {
				if (!KnownField.Status.getName().equals(changelogItem.getField())) continue;
				fromStatus = changelogItem.getFromString();
				toStatus = changelogItem.getToString();
			}

			// IF the status changed (not all change log groups include a chance to the status), then...
			if (toStatus != null) {
				if (created.isAfter(end)) {
					finalStatus = fromStatus;
					break;
				}

				// If this is the first change, record the starting statu
				if (retVal.isEmpty()) retVal.add(new StatusChange(start, fromStatus));
				retVal.add(new StatusChange(created, toStatus));
			}
		}
		// Add a start marker if we didn't get a chance to already
		if (retVal.isEmpty()) retVal.add(new StatusChange(start, finalStatus));
		// Add an end marker if we didn't get a chance at exactly the right time
		if (!retVal.get(retVal.size() - 1).getStart().isEqual(end)) retVal.add(new StatusChange(end, finalStatus));
		return retVal;
	}

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Billing());
	}

	protected final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	protected List<StatusChange> computeStatusChanges(ExtendedJiraRestClient client, String issueKey, ZonedDateTime start, ZonedDateTime end) throws InterruptedException, ExecutionException {
		final Issue issue = client.getIssueClient().getIssue(issueKey, HCollection.asList(IssueRestClient.Expandos.CHANGELOG)).get();
		final Iterable<ChangelogGroup> changelog = issue.getChangelog();
		return convertToStatusChanges(changelog, start, end, issue.getStatus().toString());
	}

	protected List<Issue> findRelevantIssues(ExtendedJiraRestClient client, List<String> users, LocalDate start, LocalDate end) throws InterruptedException, ExecutionException {
		final List<Issue> retVal = new ArrayList<>();
		for (String user : users) {
			final SearchResult result = client.getSearchClient().searchJql(String.format("issuekey IN updatedBy(%1$s, \"%2$s\", \"%3$s\")", user, start.format(DATE_FORMAT), end.format(DATE_FORMAT))).get();
			retVal.addAll(HCollection.asList(result.getIssues()));
		}
		return retVal;
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final Arguments arguments = ArgumentParser.parse(Arguments.class, invocation.getArguments());

		final Request request = HConfig.load(new PathDataSource(arguments.getRequest()), Request.class);
		final JiraAPI api = JiraAPI.createFromPropertyInput(request == null ? null : request.getApi(), null);
		try (final ExtendedJiraRestClient client = api.connect(true)) {
			final List<Issue> relevantIssues = findRelevantIssues(client, request.getUsers(), request.getStart(), request.getEnd());
			log.info("Found: {}", relevantIssues.stream().map(Issue::getKey).collect(HCollector.joining(", ", ", & ")));
			for (Issue issue : relevantIssues) {
				final List<StatusChange> changes = computeStatusChanges(client, issue.getKey(), request.getStart().atStartOfDay(ZoneId.systemDefault()), request.getEnd().atStartOfDay(ZoneId.systemDefault()).plus(5, ChronoUnit.DAYS));
				log.info("Changes to {}", issue.getKey());
				for (StatusChange change : changes) {
					log.info("\t{} -> {}", change.getStart(), change.getStatus());
				}
			}
		}

		// TODO: Input - working hours for a person (just start/stop times & days of week for now, add support for exceptions later)
		// TODO: Input - mapping of issues to accounts (e.g. by epic, by component, etc)
		// TODO: Construct a per-person timeline
		//		what accounts were they working on at all times (what issues, then group issues by account, two accounts can be double billed, or split)
		//		Reduce issue timeline to "active" statuses, and project those times against working hours
		//		Abstract the projection, so I can add filters/exceptions/days-off later
		// TODO: Report on any times where a person was not billing to anything, but was working
		// TODO: Report on any times an issue changed status outside working hours

		return IStandardCommand.SUCCESS;
	}
}
