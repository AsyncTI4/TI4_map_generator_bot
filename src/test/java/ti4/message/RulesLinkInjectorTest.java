package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RulesLinkInjectorTest {

    private static final Map<String, String> PAGE_BY_TERM = Map.of(
            "production", "R_production",
            "active system", "R_active_system",
            "pds", "R_pds",
            "wormhole", "R_wormholes",
            "wormholes", "R_wormholes",
            "wormhole nexus", "R_wormhole_nexus",
            "exhaust", "R_exhausted",
            "exhausted", "R_exhausted",
            "bombardment", "R_bombardment");

    private static String inject(String message) {
        Pattern pattern = RulesLinkInjector.patternFor(PAGE_BY_TERM.keySet());
        return RulesLinkInjector.inject(message, pattern, PAGE_BY_TERM::get);
    }

    private static String link(String text, String page) {
        return "[" + text + "](<https://www.tirules2.com/" + page + ">)";
    }

    @Test
    void linksATermAndKeepsItsCasing() {
        assertThat(inject("Move ships out of the Active System."))
                .isEqualTo("Move ships out of the " + link("Active System", "R_active_system") + ".");
    }

    @Test
    void linksOnlyTheFirstMentionOfAPage() {
        // "exhaust" and "exhausted" share a page, so the second word stays plain even though it is a different term.
        assertThat(inject("Exhausted Mecatol Rex, then exhaust another planet."))
                .isEqualTo(link("Exhausted", "R_exhausted") + " Mecatol Rex, then exhaust another planet.");
    }

    @Test
    void matchesWholeWordsOnly() {
        assertThat(inject("Reproduction of PDSes")).isEqualTo("Reproduction of PDSes");
    }

    @Test
    void prefersTheLongestTermAtAPosition() {
        // Without longest-first ordering "wormhole" would win and leave " nexus" dangling outside the link.
        assertThat(inject("Move through the Wormhole Nexus"))
                .isEqualTo("Move through the " + link("Wormhole Nexus", "R_wormhole_nexus"));
    }

    @Test
    void leavesEmojiTagsMentionsAndUrlsAlone() {
        String message = "<:pds:123456> <@987> https://example.com/production";

        assertThat(inject(message)).isEqualTo(message);
    }

    @Test
    void leavesCodeAndExistingLinksAlone() {
        String message = "`production` ```active system``` [wormhole](https://example.com)";

        assertThat(inject(message)).isEqualTo(message);
    }

    @Test
    void stillLinksATermThatFollowsAProtectedSpan() {
        assertThat(inject("<:pds:123456> PDS")).isEqualTo("<:pds:123456> " + link("PDS", "R_pds"));
    }

    @Test
    void leavesCardNamesThatContainATermAlone() {
        assertThat(inject("Played Tactical Bombardment.")).isEqualTo("Played Tactical Bombardment.");
    }

    @Test
    void handlesMessagesWithNothingToLink() {
        assertThat(inject("Nothing to see here.")).isEqualTo("Nothing to see here.");
        assertThat(inject(null)).isNull();
    }

    @Test
    void handlesAnEmptyTermList() {
        Pattern pattern = RulesLinkInjector.patternFor(List.of());

        assertThat(RulesLinkInjector.inject("production", pattern, term -> null))
                .isEqualTo("production");
    }

    @Test
    void everyTermInTheRulesFileBelongsToOnePage() throws IOException {
        // AliasHandler keys its map by term, so a term listed under two pages would silently keep only one of them.
        Properties rules = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/alias/rules_injection.properties"))) {
            rules.load(reader);
        }
        Map<String, String> pageByTerm = new HashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (String page : rules.stringPropertyNames()) {
            for (String term : rules.getProperty(page).split(",")) {
                String previous = pageByTerm.put(term.trim().toLowerCase(), page);
                if (previous != null) {
                    duplicates.add(term + " (" + previous + ", " + page + ")");
                }
            }
        }

        assertThat(duplicates).isEmpty();
    }
}
