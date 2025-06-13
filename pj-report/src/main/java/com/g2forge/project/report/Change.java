package com.g2forge.project.report;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.g2forge.alexandria.java.adt.compare.ComparableComparator;
import com.g2forge.alexandria.java.adt.compare.MappedComparator;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.gearbox.jira.fields.KnownField;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class Change {
	/**
	 * Back propagate the from assignee and status for a newly found change log to a previous change. This allows each change to have from "from" and "to"
	 * information. This is made slightly more complex because not every change affects assignee, and not ever change affects status.
	 * 
	 * @param retVal The list of changes being built up.
	 * @param fromAssignee The "from" assignee of the newly found change.
	 * @param fromStatus The "from" status of the newly found change.
	 */
	protected static void backPropagate(final List<Change> retVal, String fromAssignee, String fromStatus) {
		for (int i = retVal.size() - 1; i >= 0; i--) {
			final Change prev = retVal.get(i);
			if ((prev.getAssignee() != null) && (prev.getStatus() != null)) break;
			if ((fromAssignee == null) && (fromStatus == null)) break;

			final Change.ChangeBuilder builder = prev.toBuilder();
			if (fromAssignee != null) {
				if (prev.getAssignee() == null) builder.assignee(fromAssignee);
				else if (!prev.getAssignee().equals(fromAssignee)) {
					throw new IllegalArgumentException("Cannot back propogate change to assignee to change at " + prev.getStart() + ", because previous assignee (" + fromAssignee + ") does not match expected previous assignee (" + prev.getAssignee() + ")");
				} else fromAssignee = null;
			}
			if (fromStatus != null) {
				if (prev.getStatus() == null) builder.status(fromStatus);
				else if (!prev.getStatus().equals(fromStatus)) {
					throw new IllegalArgumentException("Cannot back propogate change to status to change at " + prev.getStart() + ", because previous status (" + fromStatus + ") does not match expected previous status (" + prev.getStatus() + ")");
				} else fromStatus = null;
			}
			retVal.set(i, builder.build());
		}
	}

	public static List<Change> toChanges(final Iterable<ChangelogGroup> changelog, ZonedDateTime start, ZonedDateTime end, String assignee, String status, IFunction1<String, String> assigneeResolver) {
		final List<Change> retVal = new ArrayList<>();
		String finalAssignee = assignee, finalStatus = status, prevFromStatus = null, prevToStatus = null;
		boolean foundFinalAssignee = false, foundFinalStatus = false;
		final List<ChangelogGroup> sorted = HCollection.asListIterable(changelog).stream().sorted(new MappedComparator<>(ChangelogGroup::getCreated, ComparableComparator.create())).collect(Collectors.toList());
		for (ChangelogGroup changelogGroup : sorted) {
			final ZonedDateTime created = Billing.convert(changelogGroup.getCreated());
			// Ignore changes before the start, and stop processing after the end
			if (created.isBefore(start)) continue;

			// Extract the from and to status from any changes to the status field (take the last change if there are multiple which should never happen)
			String fromAssignee = null, toAssignee = null;
			String fromStatus = null, toStatus = null;
			for (ChangelogItem changelogItem : changelogGroup.getItems()) {
				if (KnownField.Assignee.getName().equals(changelogItem.getField())) {
					fromAssignee = assigneeResolver.apply(changelogItem.getFrom());
					toAssignee = assigneeResolver.apply(changelogItem.getTo());
				} else if (KnownField.Status.getName().equals(changelogItem.getField())) {
					fromStatus = changelogItem.getFromString();
					toStatus = changelogItem.getToString();
				}
			}

			// Skip duplicate status changes, which are generally caused by adjustments
			if ((toAssignee == null) && (toStatus != null) && (prevFromStatus != null) && prevFromStatus.equals(fromStatus) && prevToStatus.equals(toStatus)) continue;

			// IF the status changed (not all change log groups include a chance to the status), then...
			if ((toAssignee != null) || (toStatus != null)) {
				if (created.isAfter(end)) {
					if (!foundFinalAssignee && (fromAssignee != null)) {
						finalAssignee = fromAssignee;
						foundFinalAssignee = true;
					}
					if (!foundFinalStatus && (fromStatus != null)) {
						finalStatus = fromStatus;
						foundFinalStatus = true;
					}
					if (foundFinalAssignee && foundFinalStatus) break;
				} else {
					// If this is the first change, record the starting info, otherwise back propagate any new information we just learned
					if (retVal.isEmpty()) retVal.add(new Change(start, fromAssignee, fromStatus));
					else {
						try {
							backPropagate(retVal, fromAssignee, fromStatus);
						} catch (Throwable throwable) {
							throw new RuntimeException("Failed to backpropogate information about changelog group at " + changelogGroup.getCreated() + " by " + (changelogGroup.getAuthor() != null ? changelogGroup.getAuthor().getDisplayName() : "No Author"), throwable);
						}
					}
					retVal.add(new Change(created, toAssignee, toStatus));
				}
			}

			if (toStatus != null) {
				prevFromStatus = fromStatus;
				prevToStatus = toStatus;
			}
		}
		// Add a start marker if we didn't get a chance to already
		if (retVal.isEmpty()) retVal.add(new Change(start, finalAssignee, finalStatus));
		else {
			try {
				backPropagate(retVal, finalAssignee, finalStatus);
			} catch (Throwable throwable) {
				throw new RuntimeException("Failed to backpropogate information for final assignee and status", throwable);
			}
		}
		// Add an end marker if we didn't get a chance at exactly the right time
		if (!retVal.get(retVal.size() - 1).getStart().isEqual(end)) retVal.add(new Change(end, finalAssignee, finalStatus));
		return retVal;
	}

	protected final ZonedDateTime start;

	protected final String assignee;

	protected final String status;
}