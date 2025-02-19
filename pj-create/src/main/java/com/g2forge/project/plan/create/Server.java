package com.g2forge.project.plan.create;

import java.util.Map;

import com.g2forge.gearbox.jira.JIRAServer;
import com.g2forge.project.plan.create.field.Field;
import com.g2forge.project.plan.create.field.KnownField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class Server {
	@Singular
	protected final Map<KnownField, Field> fields;

	protected final Integer sprintOffset;

	@Singular
	protected final Map<String, String> users;

	protected final JIRAServer api;
}
