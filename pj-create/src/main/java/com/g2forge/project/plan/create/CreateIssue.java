package com.g2forge.project.plan.create;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.g2forge.alexandria.java.function.IFunction1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class CreateIssue implements ICreateConfig {
	protected final String project;

	protected final String type;

	protected final String epic;

	protected final String securityLevel;

	protected final String summary;

	protected final String description;

	protected final String assignee;

	@Singular
	protected final Set<String> components;

	@Singular
	protected final Set<String> labels;

	@Singular
	protected final Map<String, Set<String>> relationships;

	@Singular
	protected final Set<String> flags;

	public CreateIssue fallback(CreateConfig config) {
		final CreateIssueBuilder retVal = builder();

		// Configurable fields
		retVal.project(IFunction1.create(ICreateConfig::getProject).applyWithFallback(this, config));
		retVal.type(IFunction1.create(ICreateConfig::getType).applyWithFallback(this, config));
		retVal.epic(IFunction1.create(ICreateConfig::getEpic).applyWithFallback(this, config));

		// Only add the components from the config if it's the same project (or we have no project)
		retVal.components(Stream.of(this, (getProject() == null) || getProject().equals(config.getProject()) ? config : null).filter(Objects::nonNull).map(ICreateConfig::getComponents).flatMap(l -> l == null ? Stream.empty() : l.stream()).collect(Collectors.toSet()));

		retVal.labels(Stream.of(this, config).map(ICreateConfig::getLabels).flatMap(l -> l == null ? Stream.empty() : l.stream()).collect(Collectors.toSet()));
		retVal.securityLevel(IFunction1.create(ICreateConfig::getSecurityLevel).applyWithFallback(this, config));
		retVal.assignee(IFunction1.create(ICreateConfig::getAssignee).applyWithFallback(this, config));
		{ // Merge the relationships
			final Map<String, Set<String>> relationships = new LinkedHashMap<>();
			for (ICreateConfig createConfig : new ICreateConfig[] { this, config }) {
				if (createConfig.getRelationships() != null) for (Map.Entry<String, Set<String>> entry : createConfig.getRelationships().entrySet()) {
					if ((entry.getValue() != null) && !entry.getValue().isEmpty()) relationships.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<String>()).addAll(entry.getValue());
				}
			}
			if (!relationships.isEmpty()) retVal.relationships(relationships);
		}

		// Per-issue fields
		retVal.summary(getSummary());
		retVal.description(getDescription());
		if (getRelationships() != null) retVal.relationships(getRelationships());

		return retVal.build();
	}

	@JsonIgnore
	public boolean isEnabled(CreateConfig config) {
		return (getFlags() == null) || !getFlags().stream().anyMatch(config.getDisabledFlags()::contains);
	}
}
