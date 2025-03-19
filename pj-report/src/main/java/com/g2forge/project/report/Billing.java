package com.g2forge.project.report;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
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
import com.g2forge.gearbox.argparse.ArgumentParser;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JiraAPI;
import com.g2forge.project.core.HConfig;

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

	protected List<Change> computeChanges(ExtendedJiraRestClient client, String userQueryParameter, String issueKey, ZonedDateTime start, ZonedDateTime end) throws InterruptedException, ExecutionException {
		final Issue issue = client.getIssueClient().getIssue(issueKey, HCollection.asList(IssueRestClient.Expandos.CHANGELOG)).get();
		final Iterable<ChangelogGroup> changelog = issue.getChangelog();
		final Cache<String, String> users = new Cache<>(id -> {
			if (id == null) return null;
			try {
				return client.getUserClient().getUserByQueryParam(userQueryParameter, id).get().getName();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException("Failed to look up user: " + id, e);
			}
		}, NeverCacheEvictionPolicy.create());
		return Change.toChanges(changelog, start, end, issue.getAssignee().getName(), issue.getStatus().getName(), users);
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

				retVal.addAll(HCollection.asList(searchResult.getIssues()));
				if ((base + actualMax) >= searchResult.getTotal()) break;
				else base += actualMax;
			}
		}
		return retVal;
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final Arguments arguments = ArgumentParser.parse(Arguments.class, invocation.getArguments());

		final Request request = HConfig.load(new PathDataSource(arguments.getRequest()), Request.class);
		final String userQueryParameter = request.getUserQueryParameter() == null ? "key" : request.getUserQueryParameter();
		final IPredicate1<Object> isComponentBillable = HMatch.createPredicate(true, request.getBillableComponents());

		final JiraAPI api = JiraAPI.createFromPropertyInput(request == null ? null : request.getApi(), null);
		try (final ExtendedJiraRestClient client = api.connect(true)) {
			final Bill.BillBuilder billBuilder = Bill.builder();

			final Map<String, Issue> issues;
			{
				final List<Issue> relevantIssues = findRelevantIssues(client, request.getJql(), request.getUsers().keySet(), request.getStart(), request.getEnd());
				issues = relevantIssues.stream().collect(Collectors.toMap(Issue::getKey, IFunction1.identity(), (i0, i1) -> i0));
			}
			log.info("Found: {}", issues.keySet().stream().collect(HCollector.joining(", ", ", & ")));
			for (Issue issue : issues.values()) {
				log.info("Examining {}", issue.getKey());
				final Set<String> billableComponents = HCollection.asList(issue.getComponents()).stream().map(BasicComponent::getName).distinct().filter(isComponentBillable).collect(Collectors.toSet());
				if (billableComponents.isEmpty()) continue;

				final List<Change> changes = computeChanges(client, userQueryParameter, issue.getKey(), request.getStart().atStartOfDay(ZoneId.systemDefault()), request.getEnd().atStartOfDay(ZoneId.systemDefault()));
				final Map<String, Double> billableHoursByUser = computeBillableHoursByUser(changes, status -> request.getBillableStatuses().contains(status), request.getUsers()::get);
				final Map<String, Double> billableHoursByUserDividedByComponents = billableHoursByUser.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / billableComponents.size()));
				for (String billableComponent : billableComponents) {
					for (Map.Entry<String, Double> entry : billableHoursByUserDividedByComponents.entrySet()) {
						billBuilder.add(billableComponent, entry.getKey(), issue.getKey(), entry.getValue());
					}
				}
			}

			final Bill bill = billBuilder.build();
			log.info("Bill by component");
			for (String component : bill.getComponents()) {
				final Bill byComponent = bill.filterBy(component, null, null);
				log.info("\t{}: {}h", component, Math.ceil(byComponent.getTotal()));
				for (String issue : byComponent.getIssues()) {
					final Bill byIssue = byComponent.filterBy(null, null, issue);
					log.info("\t\t{} {}: {}h", issue, issues.get(issue).getSummary(), Math.round(byIssue.getTotal() * 100.0) / 100.0);
				}
			}

			log.info("Bill by user");
			for (String user : bill.getUsers()) {
				final Bill byUser = bill.filterBy(null, user, null);
				log.info("\t{}: {}h", user, Math.ceil(byUser.getTotal()));
				for (String issue : byUser.getIssues()) {
					final Bill byIssue = byUser.filterBy(null, null, issue);
					log.info("\t\t{} {}: {}h", issue, issues.get(issue).getSummary(), Math.round(byIssue.getTotal() * 100.0) / 100.0);
				}
			}
		}

		// TODO: Report on any times where a person was not billing to anything, but was working
		// TODO: Report on any times an issue changed status outside working hours

		return IStandardCommand.SUCCESS;
	}
}
