package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>Why this exists: the rules a card needs live in its spec, and a refactor of the spec type migrates every
 * call site at once. Dropping an {@code excludeSelfOwned} or a controller requirement during that migration
 * compiles cleanly and fails no other test — every other test in this package exercises the <i>service</i>
 * generically, and nothing else asserts what any particular card asked for. This is the only guard against a
 * migration that silently changes a card's behaviour.
 *
 * <p>Scope is deliberate: it covers the specs that carry an ownership or controller modifier, because those
 * are the ones whose loss is both silent and behaviour-changing. Specs that are plain defaults plus a
 * predicate are left to the compiler — a dropped predicate is a visible edit, and a dropped default is a
 * no-op.
 *
 * <p>If a change here is intentional, update the expected value and say why in the commit. A change that is
 * not intentional is the bug this file is for.
 */
class PlanetTargetSpecTest extends BaseTi4Test {

    private static Game game() {
        Game game = new Game();
        game.setName("spec-pin-test");
        return game;
    }

    @Test
    void crippleMayTargetYourOwnPlanets() {
        PlanetTargetSpec spec = ButtonHelperActionCards.crippleSpec();
        assertThat(spec.buttonPrefix()).isEqualTo("crippleStep3_" + BlindSelectionService.TBD_FACTION);
        assertThat(spec.ownership()).isEqualTo(Ownership.ANY);
        assertThat(spec.requireController()).isFalse();
    }

    @Test
    void uprisingExcludesSelfAndNeedsAController() {
        PlanetTargetSpec spec = ButtonHelperActionCards.uprisingSpec();
        assertThat(spec.buttonPrefix()).isEqualTo("uprisingStep3_" + BlindSelectionService.TBD_FACTION);
        assertThat(spec.ownership()).isEqualTo(Ownership.EXCLUDE_SELF);
        assertThat(spec.requireController()).isTrue();
    }

    @Test
    void plagueExcludesSelfAndNeedsAController() {
        PlanetTargetSpec spec = ButtonHelperActionCards.plagueSpec();
        assertThat(spec.buttonPrefix()).isEqualTo("plagueStep3_" + BlindSelectionService.TBD_FACTION);
        assertThat(spec.ownership()).isEqualTo(Ownership.EXCLUDE_SELF);
        assertThat(spec.requireController()).isTrue();
    }

    @Test
    void reparationsExcludesSelfAndNeedsAController() {
        PlanetTargetSpec spec = ButtonHelperActionCards.reparationsSpec();
        assertThat(spec.buttonPrefix()).isEqualTo("reparationsStep3_" + BlindSelectionService.TBD_FACTION);
        assertThat(spec.ownership()).isEqualTo(Ownership.EXCLUDE_SELF);
        assertThat(spec.requireController()).isTrue();
    }

    @Test
    void unstableExcludesSelfAndFiltersOnAPrintedTrait() {
        PlanetTargetSpec spec = ButtonHelperActionCards.unstableSpec(game());
        assertThat(spec.buttonPrefix()).isEqualTo("unstableStep3_" + BlindSelectionService.TBD_FACTION);
        assertThat(spec.ownership()).isEqualTo(Ownership.EXCLUDE_SELF);
        // Hazardous is a printed trait, so it belongs in publicLegality rather than at resolution.
        assertThat(spec.publicLegality()).isNotNull();
        assertThat(spec.requireController()).isFalse();
    }

    @Test
    void stellarAtomicsExcludesSelf() {
        PlanetTargetSpec spec = ComponentActionHelper.atomicsSpec(game());
        assertThat(spec.buttonPrefix()).isEqualTo("atomicsStep3_" + BlindSelectionService.TBD_FACTION);
        assertThat(spec.ownership()).isEqualTo(Ownership.EXCLUDE_SELF);
        assertThat(spec.publicLegality()).isNotNull();
    }

    @Test
    void everyPlanetCardDrawsOnEverythingTheActorKnows() {
        // VISIBLE_NOW is for targets that depend on live unit positions; a planet card must not silently
        // narrow to "systems I can see right now", which would hide remembered systems from the list.
        assertThat(ButtonHelperActionCards.crippleSpec().visibility()).isEqualTo(Visibility.KNOWN);
        assertThat(ButtonHelperActionCards.uprisingSpec().visibility()).isEqualTo(Visibility.KNOWN);
        assertThat(ButtonHelperActionCards.plagueSpec().visibility()).isEqualTo(Visibility.KNOWN);
        assertThat(ButtonHelperActionCards.reparationsSpec().visibility()).isEqualTo(Visibility.KNOWN);
        assertThat(ButtonHelperActionCards.unstableSpec(game()).visibility()).isEqualTo(Visibility.KNOWN);
        assertThat(ComponentActionHelper.atomicsSpec(game()).visibility()).isEqualTo(Visibility.KNOWN);
    }

    @Test
    void everySpecCarriesTheOwnerPlaceholderSoResolutionChecksTheLiveController() {
        // A fog prefix that named a real faction would trust the button id instead of the board. Every
        // owner-resolved card must defer to whoever actually holds the planet when the button is pressed.
        assertThat(ButtonHelperActionCards.crippleSpec().buttonPrefix()).contains(BlindSelectionService.TBD_FACTION);
        assertThat(ButtonHelperActionCards.uprisingSpec().buttonPrefix()).contains(BlindSelectionService.TBD_FACTION);
        assertThat(ButtonHelperActionCards.plagueSpec().buttonPrefix()).contains(BlindSelectionService.TBD_FACTION);
        assertThat(ButtonHelperActionCards.reparationsSpec().buttonPrefix())
                .contains(BlindSelectionService.TBD_FACTION);
        assertThat(ButtonHelperActionCards.unstableSpec(game()).buttonPrefix())
                .contains(BlindSelectionService.TBD_FACTION);
        assertThat(ComponentActionHelper.atomicsSpec(game()).buttonPrefix())
                .contains(BlindSelectionService.TBD_FACTION);
    }
}
