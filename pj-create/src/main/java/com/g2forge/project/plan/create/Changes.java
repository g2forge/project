package com.g2forge.project.plan.create;

import java.util.List;

import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
class Changes {
	@Singular
	protected final List<CreateIssue> issues;

	@Singular
	protected final List<LinkIssuesInput> links;
}
