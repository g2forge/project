package com.g2forge.project.plan;

import java.io.InputStream;
import java.io.PrintStream;

import org.joda.time.DateTime;

import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JiraAPI;
import com.g2forge.gearbox.jira.sprint.Sprint;
import com.g2forge.gearbox.jira.sprint.SprintRestClient;

public class Sprints implements IStandardCommand {
	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Sprints());
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		if (invocation.getArguments().size() != 2) throw new IllegalArgumentException();
		final long initialSprintId = Long.parseLong(invocation.getArguments().get(0));
		final int sprintDuration = Integer.parseInt(invocation.getArguments().get(1));

		try (final ExtendedJiraRestClient client = JiraAPI.load().connect(true)) {
			final SprintRestClient sprintClient = client.getSprintClient();
			DateTime previousEnd = sprintClient.getSprintById(initialSprintId - 1).get().getEndDate();
			for (long sprintId = initialSprintId; true; sprintId++) {
				final DateTime currentEnd = previousEnd.plusDays(sprintDuration);
				final Sprint sprint = Sprint.builder().id(sprintId).startDate(previousEnd).endDate(currentEnd).build();
				invocation.getIo().getStandardError().append(sprintClient.getSprintById(sprintId).get().toString()).append(" -> ").println(sprint);
				sprintClient.updateSprint(sprint).get();
				previousEnd = currentEnd;
			}
		}
	}
}
