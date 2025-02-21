package com.g2forge.project.plan.create;

import java.util.Map;
import java.util.Set;

public interface ICreateConfig {
	public String getProject();

	public String getType();

	public String getEpic();

	public String getSecurityLevel();

	public String getAssignee();

	public Integer getSprint();

	public Set<String> getComponents();

	public Set<String> getLabels();

	public Map<String, Set<String>> getRelationships();

	public String getTransition();
}
