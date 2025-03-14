package com.g2forge.project.report;

import java.util.List;

import com.g2forge.gearbox.jira.JiraAPI;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class Request {
	protected final JiraAPI api;

	@Singular
	protected final List<String> users;
}