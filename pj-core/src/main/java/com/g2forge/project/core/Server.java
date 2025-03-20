package com.g2forge.project.core;

import java.util.Map;

import com.g2forge.gearbox.jira.JiraAPI;
import com.g2forge.gearbox.jira.fields.Field;
import com.g2forge.gearbox.jira.fields.IFieldConfig;
import com.g2forge.gearbox.jira.fields.KnownField;
import com.g2forge.gearbox.jira.user.UserPrimaryKey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class Server implements IFieldConfig {
	@Singular
	protected final Map<KnownField, Field> fields;

	protected final Integer sprintOffset;

	@Singular
	protected final Map<String, String> users;

	protected final UserPrimaryKey userPrimaryKey;

	protected final JiraAPI api;

	public UserPrimaryKey getUserPrimaryKey() {
		return userPrimaryKey == null ? UserPrimaryKey.NAME : userPrimaryKey;
	}
}
