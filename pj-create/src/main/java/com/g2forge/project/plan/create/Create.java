package com.g2forge.project.plan.create;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.event.Level;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.IssuelinksType;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.java.core.error.HError;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.gearbox.jira.ExtendedJiraRestClient;
import com.g2forge.gearbox.jira.JIRAServer;

import io.atlassian.util.concurrent.Promise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * A small CLI tool for creating Jira issues in bulk from a YAML file. This is particularly helpful when said issues need complex links between them (e.g.
 * dependencies) which are known at issue creation time, as the normal Jira bulk upload options do not allow automatic creation of such links.
 * 
 * Run <code>Create &lt;INPUTFILE&gt;</code>
 * 
 * The <code>INPUTFILE</code> must be a YAML file, which consists of a configuration one field of which is <code>issues</code>. The configuration must specify
 * at least a <code>summary</code> for each issue, and optionally more. The fields of the issues in the YAML file are documented below. The configuration can
 * also include, at the top level, an entry for any field marked "configurable" whose value will be used for any issue that does not specify a value explicitly.
 * Please see {@link JIRAServer} for information on specifying the Jira server and user account.
 * 
 * <table>
 * <caption>Create issues issue properties and their descriptions</caption> <thead>
 * <tr>
 * <th>Field</th>
 * <th>Required</th>
 * <th>Configurable</th>
 * <th>Type</th>
 * <th>Description</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <td>project</td>
 * <td>yes</td>
 * <td>yes</td>
 * <td>String</td>
 * <td>The key of the Jira project in which to create the issue(s). Usually 3-4 characters such as PRJ.</td>
 * </tr>
 * <tr>
 * <td>type</td>
 * <td>yes</td>
 * <td>yes</td>
 * <td>String</td>
 * <td>The type of the issue(s) to create.</td>
 * </tr>
 * <tr>
 * <td>epic</td>
 * <td>no</td>
 * <td>yes</td>
 * <td>String</td>
 * <td>The issue key (PRJ-123) of the epic to add the issue(s) to.</td>
 * </tr>
 * <tr>
 * <td>securityLevel</td>
 * <td>no</td>
 * <td>yes</td>
 * <td>String</td>
 * <td>The security level to set on the issue(s).</td>
 * </tr>
 * <tr>
 * <td>summary</td>
 * <td>yes</td>
 * <td>no</td>
 * <td>String</td>
 * <td>A short, single-line summary of the issue.</td>
 * </tr>
 * <tr>
 * <td>description</td>
 * <td>no</td>
 * <td>no</td>
 * <td>Text</td>
 * <td>A complete description of the issue. May contain Jira markup formatted text and be many lines of text. The YAML indenting, of course, will be
 * removed.</td>
 * </tr>
 * <tr>
 * <td>assignee</td>
 * <td>no</td>
 * <td>yes</td>
 * <td>String</td>
 * <td>The username of the person to assign the issue(s) to.</td>
 * </tr>
 * <tr>
 * <td>components</td>
 * <td>no</td>
 * <td>yes</td>
 * <td>Set&lt;String&gt;</td>
 * <td>A set of names of the project components to add to this issue. If both the issue &amp; configuration have components, the union of the sets will be
 * applied to the issue. Remember components are per-project, if you are creating issues across multiple projects.</td>
 * </tr>
 * <tr>
 * <td>labels</td>
 * <td>no</td>
 * <td>yes</td>
 * <td>Set&lt;String&gt;</td>
 * <td>A set of labels to apply to the issue(s).&nbsp;&nbsp;If both the issue &amp; configuration have labels, the union of the sets will be applied to the
 * issue.</td>
 * </tr>
 * <tr>
 * <td>relationships</td>
 * <td>no</td>
 * <td>yes</td>
 * <td>Map&lt;String, Set&lt;String&gt;&gt;</td>
 * <td>A map from jira links types to the issues to link to this one.&nbsp;&nbsp;Linked issues may be specified by key if they're already in Jira, or by summary
 * if they are to be added by this file.</td>
 * </tr>
 * </tbody>
 * </table>
 */
public class Create implements IStandardCommand {
	@Data
	@Builder
	@AllArgsConstructor
	protected static class LinkType {
		protected final String name;

		protected final boolean reverse;
	}

	protected static final Pattern PATTERN_KEY = Pattern.compile("([A-Z0-9]{2,5}-[0-9]+)(\\s.*)?");

	protected static Changes computeChanges(CreateConfig config) {
		final Changes.ChangesBuilder retVal = Changes.builder();
		for (CreateIssue raw : config.getIssues()) {
			// Integrate the configuration into the issue & record it 
			final CreateIssue issue = raw.fallback(config);
			retVal.issue(issue);

			// Record all the links
			for (String relationship : issue.getRelationships().keySet()) {
				for (String target : issue.getRelationships().get(relationship)) {
					retVal.link(new LinkIssuesInput(issue.getSummary(), target, relationship, null));
				}
			}
		}
		return retVal.build();
	}

	protected static boolean isKey(String keySummary) {
		return PATTERN_KEY.matcher(keySummary).matches();
	}

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, new Create());
	}

	protected static void verifyChanges(final Changes changes) {
		{ // Verify all the links we can
			final Set<String> summaries = changes.getIssues().stream().map(CreateIssue::getSummary).collect(Collectors.toSet());
			final List<String> badLinks = new ArrayList<>();
			for (LinkIssuesInput link : changes.getLinks()) {
				final String target = link.getToIssueKey();
				if (isKey(target)) continue;
				if (!summaries.contains(target)) badLinks.add(String.format("Link target \"%1$s\" <[%2$s]- \"%3$s\" is not valid!", target, link.getLinkType(), link.getFromIssueKey()));
			}
			if (!badLinks.isEmpty()) throw new IllegalArgumentException("One or more bad links:\n" + badLinks.stream().collect(Collectors.joining("\n")));
		}
	}

	protected final Map<String, Map<String, BasicComponent>> projectComponentsCache = new LinkedHashMap<>();

	public List<String> createIssues(InputStream stream) throws JsonParseException, JsonMappingException, IOException, URISyntaxException, InterruptedException, ExecutionException {
		// Load the config, but if it's empty, don't bother
		final CreateConfig config = load(stream);
		if ((config.getIssues() == null) || config.getIssues().isEmpty()) return Collections.emptyList();

		final Changes changes = computeChanges(config);
		verifyChanges(changes);
		return implementChanges(changes);
	}

	protected Map<String, BasicComponent> getProjectComponents(final ExtendedJiraRestClient client, final String projectKey) {
		return projectComponentsCache.computeIfAbsent(projectKey, projectKey2 -> {
			final Map<String, BasicComponent> retVal = new LinkedHashMap<>();
			Iterable<BasicComponent> components;
			try {
				components = client.getProjectComponentsClient().getComponents(projectKey2).get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			for (BasicComponent component : components) {
				retVal.put(component.getName(), component);
			}
			return retVal;
		});
	}

	protected List<String> implementChanges(Changes changes) throws IOException, URISyntaxException, InterruptedException, ExecutionException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		try (final ExtendedJiraRestClient client = JIRAServer.load().connect(true)) {
			final Map<String, LinkType> linkTypes = new HashMap<>();
			for (IssuelinksType linkType : client.getMetadataClient().getIssueLinkTypes().get()) {
				linkTypes.put(linkType.getName(), new LinkType(linkType.getName(), false));
				linkTypes.put(linkType.getInward(), new LinkType(linkType.getName(), true));
				linkTypes.put(linkType.getOutward(), new LinkType(linkType.getName(), false));
			}

			final IssueRestClient issueClient = client.getIssueClient();
			final Map<String, String> issues = new LinkedHashMap<>();
			for (CreateIssue issue : changes.getIssues()) {
				final IssueInputBuilder builder = new IssueInputBuilder(issue.getProject(), 0l);
				builder.setFieldInput(new FieldInput(IssueFieldId.ISSUE_TYPE_FIELD, ComplexIssueInputFieldValue.with("name", issue.getType())));
				if (issue.getEpic() != null) builder.setFieldInput(new FieldInput("customfield_10000", issue.getEpic()));
				if (issue.getSecurityLevel() != null) builder.setFieldInput(new FieldInput("security", ComplexIssueInputFieldValue.with("name", issue.getSecurityLevel())));
				builder.setSummary(issue.getSummary());
				builder.setDescription(issue.getDescription());
				if (issue.getAssignee() != null) builder.setAssigneeName(issue.getAssignee());
				if ((issue.getComponents() != null) && !issue.getComponents().isEmpty()) {
					final Map<String, BasicComponent> components = getProjectComponents(client, issue.getProject());
					builder.setFieldInput(new FieldInput(IssueFieldId.COMPONENTS_FIELD, issue.getComponents().stream().map(name -> {
						final BasicComponent component = components.get(name);
						if (component == null) throw new IllegalArgumentException(String.format("Component \"%1$s\" was not found in Jira", name));
						return ComplexIssueInputFieldValue.with("id", component.getId().toString());
					}).collect(Collectors.toSet())));
				}
				if ((issue.getLabels() != null) && !issue.getLabels().isEmpty()) builder.setFieldInput(new FieldInput(IssueFieldId.LABELS_FIELD, issue.getLabels()));

				final List<Throwable> throwables = new ArrayList<>();
				for (int i = 0; i < 5; i++) {
					final Promise<BasicIssue> promise = issueClient.createIssue(builder.build());
					final BasicIssue created;
					try {
						created = promise.get();
					} catch (ExecutionException e) {
						throwables.add(e);
						continue;
					}
					issues.put(issue.getSummary(), created.getKey());
					throwables.clear();
					break;
				}
				if (!throwables.isEmpty()) {
					HError.withSuppressed(new RuntimeException(String.format("Failed to create issue: %1$s", issue.getSummary())), throwables).printStackTrace(System.err);
				}
			}

			for (LinkIssuesInput link : changes.getLinks()) {
				final LinkType linkType = linkTypes.get(link.getLinkType());
				final String from = issues.get(link.getFromIssueKey());
				final String to = issues.getOrDefault(link.getToIssueKey(), link.getToIssueKey());
				// TODO: Handle it when an issue we're linking wasn't created
				issueClient.linkIssue(new LinkIssuesInput(linkType.isReverse() ? to : from, linkType.isReverse() ? from : to, linkType.getName(), link.getComment())).get();
			}

			return new ArrayList<>(issues.values());
		}
	}

	@Override
	public IExit invoke(CommandInvocation<InputStream, PrintStream> invocation) throws Throwable {
		if (invocation.getArguments().size() != 1) throw new IllegalArgumentException();
		final Path input = Paths.get(invocation.getArguments().get(0));
		try (final InputStream stream = Files.newInputStream(input)) {
			createIssues(stream).forEach(System.out::println);
		}
		return IStandardCommand.SUCCESS;
	}

	protected CreateConfig load(final InputStream stream) throws IOException, JsonParseException, JsonMappingException {
		final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		return mapper.readValue(stream, new TypeReference<CreateConfig>() {});
	}
}
