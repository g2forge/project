package com.g2forge.project.core;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.java.io.dataaccess.IDataSource;
import com.g2forge.alexandria.java.type.ref.ITypeRef;

import lombok.Getter;

public class HConfig {
	@Getter(lazy = true)
	private static final ObjectMapper mapper = createObjectMapper();

	protected static ObjectMapper createObjectMapper() {
		final ObjectMapper retVal = new ObjectMapper(new YAMLFactory());
		retVal.findAndRegisterModules();
		return retVal;
	}

	public static <T> T load(IDataSource source, Class<T> type) {
		try (final InputStream stream = source.getStream(ITypeRef.of(InputStream.class))) {
			return getMapper().readValue(stream, type);
		} catch (IOException exception) {
			throw new RuntimeIOException("Failed to load " + source + " as " + type.getSimpleName(), exception);
		}
	}
}
