package ti4.message;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ti4.helpers.AliasHandler;
import ti4.logging.BotLogger;

public final class RulesLinkInjector {

    private static final String RULES_SITE = "https://www.tirules2.com/";
    private static final String TERM_GROUP = "term";
    private static final String NOT_PRECEDED_BY_WORD_CHARACTER = "(?<![\\p{L}\\p{N}_])";
    private static final String NOT_FOLLOWED_BY_WORD_CHARACTER = "(?![\\p{L}\\p{N}_])";
    private static final String MATCHES_NOTHING = "(?!)";
    private static final List<String> SPANS_LEFT_UNTOUCHED = List.of(
            "```[\\s\\S]*?```",
            "`[^`]*`",
            "\\[[^\\]]*\\]\\([^)]*\\)",
            "<[^>\\s]+>",
            "https?://\\S+",
            "Tactical Bombardment",
            "Monopolize Production");

    private RulesLinkInjector() {}

    private static final class InjectedRules {
        private static final Pattern PATTERN = patternFor(AliasHandler.getInjectedRules());
    }

    public static String inject(String message) {
        try {
            return inject(message, InjectedRules.PATTERN, AliasHandler::getInjectedRule);
        } catch (Exception e) {
            BotLogger.error("Issue injecting Rules into message: " + message, e);
            return message;
        }
    }

    static Pattern patternFor(Collection<String> terms) {
        String untouchedSpans = String.join("|", SPANS_LEFT_UNTOUCHED);
        return Pattern.compile(
                untouchedSpans + "|" + NOT_PRECEDED_BY_WORD_CHARACTER + "(?<" + TERM_GROUP + ">" + alternationOf(terms)
                        + ")" + NOT_FOLLOWED_BY_WORD_CHARACTER,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String alternationOf(Collection<String> terms) {
        if (terms.isEmpty()) {
            return MATCHES_NOTHING;
        }
        return terms.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    static String inject(String message, Pattern pattern, Function<String, String> pageForTerm) {
        if (message == null) {
            return null;
        }
        Set<String> linkedPages = new HashSet<>();
        Matcher matcher = pattern.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String term = matcher.group(TERM_GROUP);
            String page = term == null ? null : pageForTerm.apply(term.toLowerCase(Locale.ROOT));
            String replacement = page != null && linkedPages.add(page) ? linkTo(term, page) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String linkTo(String term, String page) {
        return "[" + term + "](<" + RULES_SITE + page + ">)";
    }
}
