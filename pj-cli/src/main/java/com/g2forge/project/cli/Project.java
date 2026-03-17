package com.g2forge.project.cli;

import com.g2forge.alexandria.command.command.DispatchCommand;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.command.IStructuredCommand;
import com.g2forge.project.core.IProjectCommand;

public class Project implements IStructuredCommand {
	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, DispatchCommand.createAnnotation(IProjectCommand.class));
	}
}
