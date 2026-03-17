package com.g2forge.project.plan;

import java.io.InputStream;
import java.io.PrintStream;

import com.g2forge.alexandria.annotations.service.Service;
import com.g2forge.alexandria.command.command.DispatchCommand;
import com.g2forge.alexandria.command.command.IStructuredCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.project.core.IProjectCommand;

@Service(IProjectCommand.class)
public class Plan implements IProjectCommand, IStructuredCommand {
	protected static DispatchCommand.ManualBuilder build() {
		final DispatchCommand.ManualBuilder builder = new DispatchCommand.ManualBuilder();
		builder.command(new Download(), "download");
		builder.command(new Sprints(), "sprints");
		return builder;
	}

	public static void main(String[] args) throws Throwable {
		build().main(args);
	}

	@Override
	public IExit invoke(CommandInvocation<?, InputStream, PrintStream> invocation) throws Throwable {
		return build().invoke(invocation);
	}
}
