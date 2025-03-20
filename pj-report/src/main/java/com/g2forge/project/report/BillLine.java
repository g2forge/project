package com.g2forge.project.report;

import com.g2forge.gearbox.csv.CSVMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class BillLine {
	@Getter(lazy = true)
	private static final CSVMapper<BillLine> mapper = new CSVMapper<>(BillLine.class, "component", "assignee", "key", "summary", "hours", "ranges", "link");

	protected String component;

	protected String assignee;

	protected String key;

	protected String summary;

	protected double hours;

	protected String ranges;

	protected String link;
}
