package com.g2forge.project.report;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.fluent.optional.IOptional;
import com.g2forge.alexandria.parse.IMatch;
import com.g2forge.alexandria.parse.IMatcher;
import com.g2forge.alexandria.parse.IMatcherBuilder;
import com.g2forge.alexandria.parse.NamedCharacterClass;
import com.g2forge.alexandria.parse.regex.Regex;
import com.g2forge.alexandria.parse.regex.RegexMatcher;
import com.g2forge.alexandria.parse.regex.RegexMatcher.Flag;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class StatusAdjustment {
	protected static final IMatcher<?, Regex> GAP = pattern().charClass(false, cc -> cc.named(NamedCharacterClass.Space)).star().build();

	protected static final IMatcher<?, Regex> LOCALDATETIME = pattern().digit(10).repeat(4).text("-").digit(10).repeat(2).text("-").digit(10).repeat(2).with(GAP).digit(10).repeat(1, 2).text(":").digit(10).repeat(2).group(g -> g.text(":").digit(10).plus()).opt().build();

	protected static final IMatcher<?, Regex> STATUS = pattern().charClass(false, cc -> cc.range('a', 'z')).plus().build();

	@SuppressWarnings("unchecked")
	protected static final IMatcher<StatusAdjustment, Regex> ADJUSTMENT = RegexMatcher.<StatusAdjustment>builder(Flag.CASE_INSENSITIVE).group(g -> g.text("*").with(GAP)).opt().text("status").with(GAP).opt().text("adjustment").group(g -> g.with(GAP).opt().text(":").opt()).with(GAP).group(StatusAdjustment::getFrom, g -> g.with(STATUS).buildReq(IMatch::getAsString)).with(GAP).group(g -> g.alt(pattern().text("to").build(), pattern().text("->").build(), pattern().text("→").build())).opt().with(GAP)
			.group(StatusAdjustment::getTo, g -> g.with(STATUS).buildReq(IMatch::getAsString)).with(GAP).group(g -> g.text("at").with(GAP)).opt().group(StatusAdjustment::getWhen, g -> g.with(LOCALDATETIME).buildReq(match -> LocalDateTime.parse(match.getAsString(), Billing.DATETIME_FORMAT))).buildReq(match -> new StatusAdjustment(match.getAsObject(StatusAdjustment::getWhen), match.getAsObject(StatusAdjustment::getFrom), match.getAsObject(StatusAdjustment::getTo)));

	public static List<StatusAdjustment> parse(String text) {
		if ((text == null) || text.isBlank()) return HCollection.emptyList();

		final String[] lines = text.split("\n");
		final List<StatusAdjustment> retVal = new ArrayList<>();
		for (String line : lines) {
			final IOptional<StatusAdjustment> match = ADJUSTMENT.match(line);
			if (match.isNotEmpty()) retVal.add(match.get());
		}
		return retVal;
	}

	protected static IMatcherBuilder<Object, Regex> pattern() {
		return RegexMatcher.builder(Flag.CASE_INSENSITIVE);
	}

	protected final LocalDateTime when;

	protected final String from;

	protected final String to;
}