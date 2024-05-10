package com.g2forge.project.plan;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.event.Level;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JIRAServer;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Download implements IStandardCommand {
	@Data
	@Builder(toBuilder = true)
	@RequiredArgsConstructor
	@Jacksonized
	public static class Input {
		protected final String query;
	}

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Download());
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		if (invocation.getArguments().size() != 1) throw new IllegalArgumentException();
		final Path inputPath = Paths.get(invocation.getArguments().get(0));
		final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		final Input input = mapper.readValue(inputPath.toFile(), Input.class);

		try (final ExtendedJiraRestClient client = JIRAServer.load().connect(true)) {
			final int max = 500;
			int base = 0;
			while (true) {
				final SearchResult searchResult = client.getSearchClient().searchJql(input.getQuery(), max, base, null).get();
				for (Issue issue : searchResult.getIssues()) {
					log.info("Issue: {} - {}", issue.getKey(), issue.getSummary());
				}
				if ((base + max) >= searchResult.getTotal()) break;
				else base += max;
			}
		}

		return IStandardCommand.SUCCESS;
	}
}
