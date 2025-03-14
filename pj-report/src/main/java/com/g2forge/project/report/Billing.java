package com.g2forge.project.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.event.Level;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.java.adt.name.IStringNamed;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.gearbox.argparse.ArgumentParser;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JiraAPI;
import com.g2forge.gearbox.jira.fields.KnownField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Billing implements IStandardCommand {
	@Data
	@Builder(toBuilder = true)
	@AllArgsConstructor
	protected static class Arguments {
		protected final String issueKey;
	}

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Billing());
	}

	protected void demoLogChanges(final String issueKey) throws InterruptedException, ExecutionException, IOException, URISyntaxException {
		final Set<String> fields = HCollection.asList(KnownField.Status).stream().map(IStringNamed::getName).collect(Collectors.toSet());
		try (final ExtendedJiraRestClient client = JiraAPI.load().connect(true)) {
			final Issue issue = client.getIssueClient().getIssue(issueKey, HCollection.asList(IssueRestClient.Expandos.CHANGELOG)).get();
			log.info("Created at {}", issue.getCreationDate());
			for (ChangelogGroup changelogGroup : issue.getChangelog()) {
				boolean printedGroupLabel = false;
				for (ChangelogItem changelogItem : changelogGroup.getItems()) {
					if ((fields == null) || fields.contains(changelogItem.getField())) {
						if (!printedGroupLabel) {
							log.info("{} {}", changelogGroup.getCreated(), changelogGroup.getAuthor().getDisplayName());
							printedGroupLabel = true;
						}
						log.info("\t{}: {} -> {}", changelogItem.getField(), changelogItem.getFromString(), changelogItem.getToString());
					}
				}
			}
		}
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final Arguments arguments = ArgumentParser.parse(Arguments.class, invocation.getArguments());

		demoLogChanges(arguments.getIssueKey());

		// Progressing: Input - API info, list of users
		// TODO: Search for all relevant issues (anything updatedBy a relevant user in the given time range https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html, might have to search across all users)

		// TODO: I/O - Start time and end time for the report, and the exact time we ran in
		// TODO: Build a status history for an issue (Limit to the queried time range, Infer initial status from first status change, and create a timestamp of "now" for the end if needed)
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
