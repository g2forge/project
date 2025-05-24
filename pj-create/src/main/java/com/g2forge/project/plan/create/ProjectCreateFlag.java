package com.g2forge.project.plan.create;

import com.g2forge.alexandria.java.core.properties.IKnownPropertyBoolean;

public enum ProjectCreateFlag implements IKnownPropertyBoolean {
	DRYRUN {
		@Override
		public Boolean getDefault() {
			return false;
		}
	};
}