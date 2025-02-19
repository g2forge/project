package com.g2forge.project.plan.create;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.core.helpers.HCollector;
import com.g2forge.alexandria.java.core.helpers.HMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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

	protected final Integer sprint;

	protected final Integer sprintOffset;

	@Singular
	protected final Set<String> components;

	@Singular
	protected final Set<String> labels;

	@Singular
	protected final List<CreateIssue> issues;

	@Singular
	protected final Map<String, Set<String>> relationships;

	@Singular
	protected final Map<String, Boolean> flags;

	@Getter(lazy = true)
	@JsonIgnore
	private final Map<String, Boolean> specifiedFlags = getFlags() == null ? HMap.empty() : getFlags().entrySet().stream().filter(entry -> entry.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

	@Getter(lazy = true)
	@JsonIgnore
	private final Set<String> disabledFlags = getSpecifiedFlags().entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toSet());

	@JsonIgnore
	public List<CreateIssue> getEnabledIssues() {
		return getIssues().stream().filter(issue -> issue.isEnabled(this)).collect(Collectors.toList());
	}

	@JsonIgnore
	public List<CreateIssue> getDisabledIssues() {
		return getIssues().stream().filter(issue -> !issue.isEnabled(this)).collect(Collectors.toList());
	}

	public void validateFlags() {
		final Set<String> referencedFlags = getIssues().stream().flatMap(issue -> issue.getFlags() == null ? Stream.empty() : issue.getFlags().stream()).collect(Collectors.toSet());
		final Set<String> unknownFlags = HCollection.difference(referencedFlags, getSpecifiedFlags().keySet());
		if (!unknownFlags.isEmpty()) throw new IllegalArgumentException("The following flags are refenced by issues, but are neither enabled nor disabled: " + unknownFlags.stream().collect(HCollector.joining(", ", ", & ")));
	}
}
