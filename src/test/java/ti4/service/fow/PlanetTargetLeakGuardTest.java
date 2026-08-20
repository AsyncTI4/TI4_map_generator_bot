package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Structural guard against re-introducing the Fog of War planet-holdings leak.
 *
 * <p>The leak has one shape: build Discord buttons by looping over <b>another player's</b> planets, then show
 * them to someone else. In fog that hands the viewer the target's holdings. It was reintroduced independently
 * in more than twenty components because the idiom is easy to copy, so a review-time convention is not enough.
 *
 * <p>This is a heuristic source scan, not real static analysis - it looks for a planet-enumerating loop with a
 * {@code Buttons.} construction close behind. Four enumeration methods are checked, because during the
 * investigation each one that was left out of a grep hid real leaks: {@code getPlanets} missed
 * {@code getPlanetsAllianceMode} (Reactor Meltdown, Ragh's Call, Galactic Movement), and both missed
 * {@code getReadiedPlanets}/{@code getExhaustedPlanets} (Reparations, Khrask hero).
 *
 * <p>If this test fails on new code, route the list through
 * {@link PlanetTargetService#targetButtons} instead. If the loop is genuinely non-fog-only - guarded by an
 * {@code isFowMode()} branch that takes the fog path elsewhere - add it to {@link #ALLOWED} with a reason.
 */
class PlanetTargetLeakGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** How many lines after the loop header still count as "this loop builds the buttons". */
    private static final int WINDOW = 14;

    private static final Pattern LOOP =
            Pattern.compile("for\\s*\\(\\s*(?:final\\s+)?String\\s+\\w+\\s*:\\s*(\\w+)\\s*\\.\\s*"
                    + "(getPlanets|getPlanetsAllianceMode|getReadiedPlanets|getExhaustedPlanets)\\s*\\(\\s*\\)");

    /**
     * Receivers that are the viewer themselves. Listing your own planets back to you leaks nothing.
     */
    private static final Set<String> SELF_RECEIVERS = Set.of("player", "viewer", "actor", "p1");

    /**
     * Loops that survive deliberately, each because the fog path is handled elsewhere in the same flow.
     * Keyed by "<simple file name>#<receiver>".
     */
    private static final Set<String> ALLOWED = Set.of(
            // Non-fog branches: the fog path skips the player-picking step and calls PlanetTargetService.
            "ButtonHelperActionCards.java#p2", // cripple/plague/unstable/infiltrate/reactor step 2
            "ComponentActionHelper.java#p2", // atomicsStep2
            "ButtonHelperHeroes.java#p2", // khrask step 3 ready/exhaust
            "YinHeroButtonHandler.java#target", // yinHeroTarget
            "IxthianGiftAcd2ButtonHandler.java#target", // ixthianGiftPlayer
            "PlotCardsService.java#puppet", // seethe
            "ButtonHelperFactionSpecific.java#saar", // raghsCall
            "SettlementsAcd2ButtonHandler.java#voter", // settlements
            // Whole-map loops (game.getPlanets()); these leak map inventory rather than ownership, and both
            // now take the PlanetTargetService path in fog.
            "VyserixLeaderHandler.java#game", // vyserix hero attachment
            "ButtonHelperAbilities.java#game", // ancient empire tomb tokens
            // Consensual / already-visible flows, audited as benign.
            "TransactionHelper.java#p1",
            "ButtonHelper.java#p1",
            "SleeperTokenHelper.java#p2",
            // Hacan mech trade: a transaction the owner initiates, listing only their own mech planets.
            "ButtonHelperFactionSpecific.java#hacan",
            // Non-fog branches of the coexistence flows; all now take the PlanetTargetService path in fog.
            "ButtonHelperAgents.java#p2",
            "DSHelperBreakthroughs.java#p1",
            "DSHelperBreakthroughs.java#target",
            "TeHelperActionCards.java#p2",
            "ButtonHelperCommanders.java#target",
            "ButtonHelperFactionSpecific.java#p2");

    @Test
    void noNewHandRolledPlanetButtonLoops() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                offenders.addAll(scan(file));
            }
        }

        assertThat(offenders).as("""
                        Found planet-target button list(s) built by hand from another player's planets.
                        In a Fog of War game this discloses that player's holdings to the viewer.
                        Use PlanetTargetService.targetButtons(...) instead, or add an entry to ALLOWED with a
                        reason if the fog path is handled elsewhere in the same flow.""").isEmpty();
    }

    private static List<String> scan(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = LOOP.matcher(lines.get(i));
            if (!matcher.find()) continue;

            String receiver = matcher.group(1);
            if (SELF_RECEIVERS.contains(receiver)) {
                continue;
            }
            String key = fileName(file) + "#" + receiver;
            if (ALLOWED.contains(key)) continue;

            for (int j = i + 1; j < Math.min(lines.size(), i + 1 + WINDOW); j++) {
                if (lines.get(j).contains("Buttons.")) {
                    offenders.add(key + " at " + fileName(file) + ":" + (i + 1));
                    break;
                }
            }
        }
        return offenders;
    }

    private static String fileName(Path file) {
        return file.getFileName().toString();
    }
}
