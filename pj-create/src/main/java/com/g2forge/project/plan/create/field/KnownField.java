package com.g2forge.project.plan.create.field;

import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.g2forge.project.plan.create.Server;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum KnownField implements IField {
	Assignee(IssueFieldId.ASSIGNEE_FIELD.id, "name"),
	EpicSummary("customfield_10002", null),
	Parent("customfield_10000", null),
	Sprint("customfield_10004", null),
	Security("security", "name");

	protected final String name;

	protected final String withKey;

	public IField get(Server server) {
		if ((server == null) || (server.getFields() == null)) return this;
		final Field retVal = server.getFields().get(this);
		if (retVal == null) return this;
		return retVal;
	}
}
