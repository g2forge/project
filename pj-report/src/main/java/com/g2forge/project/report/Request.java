package com.g2forge.project.report;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class Request {
	protected final String jql;

	@Singular
	protected final Map<String, WorkingHours> users;

	@Singular
	protected final Set<String> billableStatuses;

	@Singular
	protected final Set<String> billableComponents;

	protected final LocalDate start;

	protected final LocalDate end;

	public ZoneId getZone(String user) {
		return user == null ? ZoneId.systemDefault() : getUsers().get(user).getZone();
	}
}