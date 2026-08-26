package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ComponentActionHelper;
import ti4.service.fow.PlanetTargetService.Ownership;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.fow.PlanetTargetService.Visibility;
import ti4.testUtils.BaseTi4Test;

/**
 * Pins each card's fog targeting rules to the values they have today.
 *
 * <p>Why this exists: every other test in this package exercises the <i>service</i> generically, and nothing
 * else asserts what any particular card asked for. Dropping an {@code excludeSelfOwned} or a controller
 * requirement during a future spec refactor compiles cleanly and fails no other test - this is the only guard
 * against a migration that silently changes a card's behaviour.
 *
 * <p>Scope is deliberate: only specs carrying an ownership or controller modifier are pinned, because those
 * are the ones whose loss is silent. A dropped predicate on the others is a visible edit; a dropped default is
 * a no-op. If a change here is intentional, update the table and say why in the commit.
 */
class PlanetTargetSpecTest extends BaseTi4Test {

    private record Expected(String card, PlanetTargetSpec spec, Ownership ownership, boolean requireController) {}

    @Test
    void cardSpecsMatchTheirPrintedRules() {
        Game game = new Game();
        game.setName("spec-pin-test");

        List<Expected> cards = List.of(
                // Cripple Defenses: "Choose 1 planet" - any planet, no controller needed.
                new Expected("Cripple", ButtonHelperActionCards.crippleSpec(), Ownership.ANY, false),
                // Uprising / Plague / Reparations: another player's controlled planet.
                new Expected("Uprising", ButtonHelperActionCards.uprisingSpec(), Ownership.EXCLUDE_SELF, true),
                new Expected("Plague", ButtonHelperActionCards.plagueSpec(), Ownership.EXCLUDE_SELF, true),
                new Expected("Reparations", ButtonHelperActionCards.reparationsSpec(), Ownership.EXCLUDE_SELF, true),
                // Unstable / Stellar Atomics: another player's planet, filtered by a printed trait instead.
                new Expected("Unstable", ButtonHelperActionCards.unstableSpec(game), Ownership.EXCLUDE_SELF, false),
                new Expected(
                        "Stellar Atomics", ComponentActionHelper.atomicsSpec(game), Ownership.EXCLUDE_SELF, false));

        for (Expected c : cards) {
            assertThat(c.spec().ownership()).as(c.card() + " ownership").isEqualTo(c.ownership());
            assertThat(c.spec().requireController())
                    .as(c.card() + " requireController")
                    .isEqualTo(c.requireController());
            // Every planet card draws on everything the actor knows, never just what's visible right now.
            assertThat(c.spec().visibility()).as(c.card() + " visibility").isEqualTo(Visibility.KNOWN);
            // TBDF defers to the live controller at press time instead of trusting a stale button id.
            assertThat(c.spec().buttonPrefix()).as(c.card() + " prefix").contains(BlindSelectionService.TBD_FACTION);
        }

        // Hazardous/DMZ-shaped rules are printed traits, so they belong in publicLegality, not at resolution.
        assertThat(ButtonHelperActionCards.unstableSpec(game).publicLegality()).isNotNull();
        assertThat(ComponentActionHelper.atomicsSpec(game).publicLegality()).isNotNull();
    }
}
