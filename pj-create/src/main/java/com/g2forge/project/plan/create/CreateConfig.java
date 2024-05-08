package com.g2forge.project.plan.create;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class CreateConfig implements ICreateConfig {
	protected final String project;

	protected final String type;

	protected final String epic;

	protected final String securityLevel;

	protected final String assignee;

	@Singular
	protected final Set<String> components;

	@Singular
	protected final Set<String> labels;

	@Singular
	protected final List<CreateIssue> issues;

	@Singular
	protected final Map<String, Set<String>> relationships;
}
