package com.g2forge.project.plan;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.event.Level;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JiraAPI;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Download implements IStandardCommand {
	@Data
	@Builder(toBuilder = true)
	@RequiredArgsConstructor
	@Jacksonized
	public static class Input {
		protected final String query;

		protected final Path output;
	}

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Download());
	}

	public static Path withBase(Path base, Path path) {
		if (path.isAbsolute() || (base == null)) return path;
		return base.resolve(path);
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		if (invocation.getArguments().size() != 1) throw new IllegalArgumentException();
		final Path inputPath = Paths.get(invocation.getArguments().get(0));
		final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		final Input input = mapper.readValue(inputPath.toFile(), Input.class);

		try (final Workbook workbook = new XSSFWorkbook()) {
			final Sheet sheet = workbook.createSheet("Issues");
			{
				final Row header = sheet.createRow(0);
				header.createCell(0).setCellValue("Key");
				header.createCell(1).setCellValue("Summary");
			}

			try (final ExtendedJiraRestClient client = JiraAPI.load().connect(true)) {
				final int max = 500;
				int base = 0, rowNum = 1;
				while (true) {
					final SearchResult searchResult = client.getSearchClient().searchJql(input.getQuery(), max, base, null).get();
					log.info("Got issues {} to {} of {}", base, base + Math.min(searchResult.getMaxResults(), searchResult.getTotal() - base), searchResult.getTotal());

					for (Issue issue : searchResult.getIssues()) {
						final Row row = sheet.createRow(rowNum++);
						row.createCell(0).setCellValue(issue.getKey());
						row.createCell(1).setCellValue(issue.getSummary());
					}
					if ((base + max) >= searchResult.getTotal()) break;
					else base += max;
				}
			}

			try (final OutputStream outputStream = Files.newOutputStream(withBase(inputPath.getParent(), input.getOutput()))) {
				workbook.write(outputStream);
			}
		}

		return IStandardCommand.SUCCESS;
	}
}
