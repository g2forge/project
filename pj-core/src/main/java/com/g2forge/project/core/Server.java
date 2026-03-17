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

	protected final Map<Integer, Integer> sprintMap;

	@Singular
	protected final Map<String, String> users;


	protected final UserPrimaryKey userPrimaryKey;
	protected final JiraAPI api;

	public UserPrimaryKey getUserPrimaryKey() {
		return userPrimaryKey == null ? UserPrimaryKey.NAME : userPrimaryKey;
	}

	public Integer modifySprint(Integer sprint) {
		// No matter how things are configured, this is the correct result
		if (sprint == null) return null;

		if ((getSprintOffset() != null) && (getSprintMap() != null)) throw new IllegalArgumentException("Both offset and map are non-null, please specify either a map from sprint numbers to IDs, or an offset!");

		if (getSprintMap() != null) {
			final Integer retVal = getSprintMap().get(sprint);
			if (retVal == null) throw new IllegalArgumentException(String.format("Sprint number %1$d was not mapped, please update your sprint number to ID map!", sprint));
			return retVal;
		} else return sprint + getSprintOffset();
	}
}
