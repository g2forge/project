package com.g2forge.project.report;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.event.Level;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.FieldType;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.User;
import com.g2forge.alexandria.adt.associative.cache.Cache;
import com.g2forge.alexandria.adt.associative.cache.NeverCacheEvictionPolicy;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.java.adt.compare.IComparable;
import com.g2forge.alexandria.java.core.error.UnreachableCodeError;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.core.helpers.HCollector;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.IPredicate1;
import com.g2forge.alexandria.java.function.builder.IBuilder;
import com.g2forge.alexandria.java.io.dataaccess.PathDataSource;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.match.HMatch;
import com.g2forge.alexandria.path.path.filename.Filename;
import com.g2forge.gearbox.argparse.ArgumentParser;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JiraAPI;
import com.g2forge.gearbox.jira.fields.KnownField;
import com.g2forge.project.core.HConfig;
import com.g2forge.project.core.Server;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Billing implements IStandardCommand {
	@Data
	@Builder(toBuilder = true)
	@AllArgsConstructor
	protected static class Arguments {
		protected final Path server;

		protected final Path request;
	}

	@Data
	@Builder(toBuilder = true)
	@RequiredArgsConstructor
	public static class Bill {
		public static class BillBuilder implements IBuilder<Bill> {
			public BillBuilder add(String component, String user, String issue, double amount) {
				final Key key = new Key(component, user, issue);
				if (amounts$key != null) {
					final int index = amounts$key.indexOf(key);
					if (index >= 0) {
						amounts$value.set(index, amounts$value.get(index) + amount);
						return this;
					}
				}
				return amount(key, amount);
			}
		}

		@Data
		@Builder(toBuilder = true)
		@RequiredArgsConstructor
		public static class Key implements IComparable<Key> {
			protected final String component;

			protected final String user;

			protected final String issue;

			@Override
			public int compareTo(Key o) {
				final int component = getComponent().compareTo(o.getComponent());
				if (component != 0) return component;

				final int user = getUser().compareTo(o.getUser());
				if (user != 0) return user;

				final int issue = getIssue().compareTo(o.getIssue());
				return issue;
			}
		}

		@Singular
		protected final Map<Key, Double> amounts;

		public Bill filterBy(String component, String user, String issue) {
			return new Bill(getAmounts().entrySet().stream().filter(entry -> {
				final Key key = entry.getKey();
				if ((component != null) && !key.getComponent().equals(component)) return false;
				if ((user != null) && !key.getUser().equals(user)) return false;
				if ((issue != null) && !key.getIssue().equals(issue)) return false;
				return true;
			}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
		}

		public Set<String> getComponents() {
			return getAmounts().keySet().stream().map(Key::getComponent).collect(Collectors.toSet());
		}

		public Set<String> getIssues() {
			return getAmounts().keySet().stream().map(Key::getIssue).collect(Collectors.toSet());
		}

		public double getTotal() {
			return getAmounts().values().stream().mapToDouble(Double::doubleValue).sum();
		}

		public Set<String> getUsers() {
			return getAmounts().keySet().stream().map(Key::getUser).collect(Collectors.toSet());
		}
	}

	protected static Map<String, Double> computeBillableHoursByUser(List<Change> changes, IPredicate1<String> isStatusBillable, IFunction1<? super String, ? extends WorkingHours> workingHoursFunction) {
		final Map<String, Double> retVal = new TreeMap<>();
		for (int i = 0; i < changes.size() - 1; i++) {
			final Change change = changes.get(i);
			if ((change.getAssignee() == null) || !isStatusBillable.test(change.getStatus())) continue;

			final WorkingHours workingHours = workingHoursFunction.apply(change.getAssignee());
			if (workingHours == null) throw new IllegalArgumentException("No working hours found for user \"" + change.getAssignee() + "\", please configure the billing report to include the working hours for that user!");

			final Double billable = workingHours.computeBillableHours(change.getStart(), changes.get(i + 1).getStart());
			if (billable < 0) throw new UnreachableCodeError();
			if (billable > 0) {
				final Double previous = retVal.get(change.getAssignee());
				retVal.put(change.getAssignee(), (previous == null ? 0.0 : previous) + billable);
			}
		}
		return retVal;
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

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Billing());
	}

	protected final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	protected List<Change> computeChanges(ExtendedJiraRestClient client, Server server, Request request, IFunction1<User, String> userToFriendly, String issueKey, ZonedDateTime start, ZonedDateTime end) throws InterruptedException, ExecutionException {
		final Issue issue = client.getIssueClient().getIssue(issueKey, HCollection.asList(IssueRestClient.Expandos.CHANGELOG)).get();
		final List<ChangelogGroup> changelog = new ArrayList<>(HCollection.asListIterable(issue.getChangelog()));
		for (Comment comment : issue.getComments()) {
			final String body = comment.getBody();
			final List<StatusAdjustment> adjustments = StatusAdjustment.parse(body);
			if (!adjustments.isEmpty()) {
				final ZoneId zone = request.getZone(comment.getAuthor().getName());
				for (StatusAdjustment adjustment : adjustments) {
					final ZonedDateTime when = adjustment.getWhen().atZone(zone);
					changelog.add(new ChangelogGroup(comment.getAuthor(), convert(when), HCollection.asList(new ChangelogItem(FieldType.JIRA, KnownField.Status.getName(), adjustment.getFrom(), adjustment.getFrom(), adjustment.getTo(), adjustment.getTo()))));
				}
			}
		}

		final IFunction1<String, String> users = new Cache<>(primaryKey -> {
			if (primaryKey == null) return null;
			try {
				final User user = client.getUserClient().getUserByQueryParam(server.getUserPrimaryKey().getQueryParameter(), primaryKey).get();
				return userToFriendly.apply(user);
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException("Failed to look up user: " + primaryKey, e);
			}
		}, NeverCacheEvictionPolicy.create());
		return Change.toChanges(changelog, start, end, userToFriendly.apply(issue.getAssignee()), issue.getStatus().getName(), users);
	}

	protected List<Issue> findRelevantIssues(ExtendedJiraRestClient client, String jql, Collection<? extends String> users, LocalDate start, LocalDate end) throws InterruptedException, ExecutionException {
		final List<Issue> retVal = new ArrayList<>();
		for (String user : users) {
			log.info("Finding issues for {}", user);
			final String compositeJQL = String.format("issuekey IN updatedBy(%1$s, \"%2$s\", \"%3$s\")", user, start.format(DATE_FORMAT), end.format(DATE_FORMAT)) + ((jql == null) ? "" : (" AND " + jql));
			final int desiredMax = 500;
			int base = 0;
			while (true) {
				final SearchResult searchResult = client.getSearchClient().searchJql(compositeJQL, desiredMax, base, null).get();
				final int actualMax = searchResult.getMaxResults();
				log.info("\tGot issues {} to {} of {}", base, base + Math.min(actualMax, searchResult.getTotal() - base), searchResult.getTotal());

				retVal.addAll(HCollection.asListIterable(searchResult.getIssues()));
				if ((base + actualMax) >= searchResult.getTotal()) break;
				else base += actualMax;
			}
		}
		return retVal;
	}

	protected List<Change> examineIssue(final ExtendedJiraRestClient client, Server server, Request request, IPredicate1<String> isStatusBillable, IPredicate1<Object> isComponentBillable, IFunction1<User, String> userToFriendly, Issue issue, Bill.BillBuilder billBuilder) throws InterruptedException, ExecutionException {
		log.info("Examining {}", issue.getKey());
		final Set<String> billableComponents = HCollection.asListIterable(issue.getComponents()).stream().map(BasicComponent::getName).distinct().filter(isComponentBillable).collect(Collectors.toSet());
		if (billableComponents.isEmpty()) return null;

		final List<Change> changes = computeChanges(client, server, request, userToFriendly, issue.getKey(), request.getStart().atStartOfDay(ZoneId.systemDefault()), request.getEnd().atStartOfDay(ZoneId.systemDefault()));
		final Map<String, Double> billableHoursByUser = computeBillableHoursByUser(changes, isStatusBillable, request.getUsers()::get);
		final Map<String, Double> billableHoursByUserDividedByComponents = billableHoursByUser.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / billableComponents.size()));
		for (String billableComponent : billableComponents) {
			for (Map.Entry<String, Double> entry : billableHoursByUserDividedByComponents.entrySet()) {
				billBuilder.add(billableComponent, entry.getKey(), issue.getKey(), entry.getValue());
			}
		}
		return changes;
	}

	public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final Arguments arguments = ArgumentParser.parse(Arguments.class, invocation.getArguments());

		final Server server = HConfig.load(new PathDataSource(arguments.getServer()), Server.class);
		final Request request = HConfig.load(new PathDataSource(arguments.getRequest()), Request.class);
		final IPredicate1<String> isStatusBillable = status -> request.getBillableStatuses().contains(status);
		final IPredicate1<Object> isComponentBillable = HMatch.createPredicate(true, request.getBillableComponents());

		final Map<String, String> userReverseMap = server.getUsers().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
		final IFunction1<User, String> userToFriendly = user -> {
			final String primaryKey = server.getUserPrimaryKey().getValue(user);
			return userReverseMap.getOrDefault(primaryKey, primaryKey);
		};

		final JiraAPI api = JiraAPI.createFromPropertyInput(server == null ? null : server.getApi(), null);
		try (final ExtendedJiraRestClient client = api.connect(true)) {
			final Bill.BillBuilder billBuilder = Bill.builder();

			final Map<String, Issue> issues;
			final Map<String, List<Change>> changes = new TreeMap<>();
			{
				final List<Issue> relevantIssues = findRelevantIssues(client, request.getJql(), request.getUsers().keySet(), request.getStart(), request.getEnd());
				issues = relevantIssues.stream().collect(Collectors.toMap(Issue::getKey, IFunction1.identity(), (i0, i1) -> i0));
			}
			log.info("Found: {}", issues.keySet().stream().collect(HCollector.joining(", ", ", & ")));
			final Map<Issue, Throwable> errors = new LinkedHashMap<>();
			for (Issue issue : issues.values()) {
				try {
					changes.put(issue.getKey(), examineIssue(client, server, request, isStatusBillable, isComponentBillable, userToFriendly, issue, billBuilder));
				} catch (Throwable throwable) {
					log.error("Failed to incorporate {} into billing report: {}", issue.getKey(), throwable);
					errors.put(issue, throwable);
				}
			}

			final Bill bill = billBuilder.build();
			final List<BillLine> billLines = new ArrayList<>();
			log.info("Bill by component");
			for (String component : bill.getComponents()) {
				final Bill byComponent = bill.filterBy(component, null, null);
				log.info("\t{}: {}h", component, Math.ceil(byComponent.getTotal()));
				for (String issue : byComponent.getIssues()) {
					final Bill byIssue = byComponent.filterBy(null, null, issue);
					final String summary = issues.get(issue).getSummary();
					final double hours = Math.round(byIssue.getTotal() * 100.0) / 100.0;
					log.info("\t\t{} {}: {}h", issue, summary, hours);

					final String assignees = byIssue.getUsers().stream().collect(HCollector.joining(", ", ", & "));
					final String link = server.getApi().createIssueLink(issue);
					final StringBuilder ranges = new StringBuilder();
					final List<Change> issueChanges = changes.get(issue);

					boolean currentBillable = false;
					for (int i = 0; i < issueChanges.size(); i++) {
						final Change change = issueChanges.get(i);
						final boolean newBillable = isStatusBillable.test(change.getStatus());
						if (newBillable != currentBillable) {
							currentBillable = newBillable;
							final ZoneId zone = request.getZone(change.getAssignee());
							final LocalDateTime local = change.getStart().withZoneSameInstant(zone).toLocalDateTime();
							ranges.append(DATETIME_FORMAT.format(local)).append(" (@").append(change.getAssignee() == null ? zone : change.getAssignee()).append(')');
							ranges.append(' ').append(((issueChanges.size() - 1) == i) ? "End" : (newBillable ? "Start" : "Stop")).append('\n');
						}
					}
					billLines.add(new BillLine(component, assignees, issue, summary, hours, ranges.toString().strip(), link));
				}
			}
			final Path outputFile = Filename.replaceExtension(arguments.getRequest(), "csv");
			log.info("Writing bill to {}", outputFile);
			BillLine.getMapper().write(billLines, outputFile);

			log.info("Bill by user");
			for (String user : bill.getUsers()) {
				final Bill byUser = bill.filterBy(null, user, null);
				log.info("\t{}: {}h", user, Math.ceil(byUser.getTotal()));
				for (String issue : byUser.getIssues()) {
					final Bill byIssue = byUser.filterBy(null, null, issue);
					log.info("\t\t{} {}: {}h", issue, issues.get(issue).getSummary(), Math.round(byIssue.getTotal() * 100.0) / 100.0);
				}
			}

			if (!errors.isEmpty()) {
				log.error("One or more issues could not be incorporated into the report (please see above for complete errors):");
				for (Map.Entry<Issue, Throwable> entry : errors.entrySet()) {
					log.error("\t{}: {}", entry.getKey().getKey(), entry.getValue().getMessage());
				}
				return IStandardCommand.FAIL;
			}
		}

		// TODO: Report on any times where a person was not billing to anything, but was working
		// TODO: Report on any times an issue changed status outside working hours

		return IStandardCommand.SUCCESS;
	}
}
