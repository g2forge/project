package com.g2forge.project.plan.create;

import com.g2forge.alexandria.java.function.IFunction1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@Jacksonized
public class SprintConfig {
	@Getter(lazy = true)
	private static final SprintConfig DEFAULT = new SprintConfig(null, 0, 1, Integer.MAX_VALUE);

	protected final Integer sprint;

	protected final Integer offset;

	protected final Integer min;

	protected final Integer max;

	public SprintConfig fallback(SprintConfig that) {
		final SprintConfigBuilder retVal = builder();
		retVal.sprint(IFunction1.create(SprintConfig::getSprint).applyWithFallback(this, that));
		retVal.offset(IFunction1.create(SprintConfig::getOffset).applyWithFallback(this, that));
		retVal.min(IFunction1.create(SprintConfig::getMin).applyWithFallback(this, that));
		retVal.max(IFunction1.create(SprintConfig::getMax).applyWithFallback(this, that));
		return retVal.build();
	}

	public Integer modify(Integer sprint) {
		if (sprint == null) return null;
		return Integer.min(Integer.max(sprint + getOffset(), getMin()), getMax());
	}
}
