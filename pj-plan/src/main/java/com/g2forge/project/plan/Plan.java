package com.g2forge.project.plan;

import com.g2forge.alexandria.command.command.DispatchCommand;
import com.g2forge.alexandria.command.command.IStructuredCommand;

public class Plan implements IStructuredCommand {
	public static void main(String[] args) throws Throwable {
		final DispatchCommand.ManualBuilder builder = new DispatchCommand.ManualBuilder();
		builder.main(args);
	}
}
