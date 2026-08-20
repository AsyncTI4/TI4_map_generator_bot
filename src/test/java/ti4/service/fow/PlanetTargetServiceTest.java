package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.FoWHelper;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.testUtils.BaseTi4Test;

/**
 * Guards the Fog of War rule that a planet may only be offered as a target when the acting player could
 * know it exists: they can see its system, have ever seen its system, or can see its owner's stats.
 *
 * <p>The headline case is {@code targetButtons_includesPlanetOnRememberedButNotVisibleTile}. Before the fix,
 * the candidate filter discarded tile visibility entirely for any owned planet and kept only planets whose
 * owner's stats were visible - which in a normal fog game is nobody - so every planet-target action card
 * collapsed to showing the player their own planets.
 */
class PlanetTargetServiceTest extends BaseTi4Test {

    private static final String PREFIX = "crippleStep3_" + BlindSelectionService.TBD_FACTION;

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
    }

    private static List<Button> build(Game game, Player actor, PlanetTargetSpec spec) {
        return PlanetTargetService.targetButtons(game, actor, spec, new ArrayList<>());
    }

    private static boolean offers(List<Button> buttons, String planetId) {
        return buttons.stream().anyMatch(b -> (PREFIX + "_" + planetId).equals(b.getCustomId()));
    }

    /**
     * The default fixture is a real saved game, so its players already carry persisted {@code fow_systems}
     * memory. Tests that need "this player has never seen anything" must start from a cleared slate.
     */
    private static void forgetEverything(Player player) {
        player.getFogTiles().clear();
    }

    /** An actor plus a planet that actor genuinely could not know about. */
    private record HiddenCase(Player actor, String planet, Player owner) {}

    /**
     * Finds an actor and a planet the actor could not know about: held by someone else, in a system the actor
     * cannot see, whose owner's stats the actor cannot see either.
     *
     * <p>The fixture is a real saved game, so it is not a given that any particular player is ignorant of any
     * particular opponent - players hold each other's promissory notes, sit in alliances, and have each
     * other's home systems in view. So this searches over every actor rather than assuming the first player
     * works, and the test asserts a case was found rather than silently passing on a vacuous one.
     */
    private static HiddenCase findHiddenCase(Game game) {
        for (Player actor : game.getRealPlayers()) {
            Set<String> visible = FoWHelper.getTilePositionsToShow(game, actor);
            for (Player other : game.getRealPlayers()) {
                if (other == actor || FoWHelper.canSeeStatsOfPlayer(game, other, actor)) continue;
                for (String planet : other.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    if (tile != null && !visible.contains(tile.getPosition())) {
                        return new HiddenCase(actor, planet, other);
                    }
                }
            }
        }
        return null;
    }

    @Test
    void targetButtons_includesPlanetOnRememberedButNotVisibleTile() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            HiddenCase c = findHiddenCase(game);
            assertThat(c)
                    .as("the default test map should have some player ignorant of some enemy planet")
                    .isNotNull();
            Player actor = c.actor();
            String hidden = c.planet();
            forgetEverything(actor);

            // Not known yet: never seen, and the owner's stats are not visible.
            assertThat(offers(build(game, actor, PlanetTargetSpec.of(PREFIX)), hidden))
                    .isFalse();

            // Scout it once, then lose sight of it. The memory alone must be enough to target it.
            Tile tile = game.getTileFromPlanet(hidden);
            actor.updateFogTile(tile, null);

            assertThat(FoWHelper.hasEverSeenTile(actor, tile.getPosition())).isTrue();
            assertThat(FoWHelper.knowsPlanetExists(game, actor, hidden)).isTrue();
            assertThat(offers(build(game, actor, PlanetTargetSpec.of(PREFIX)), hidden))
                    .isTrue();
        }
    }

    @Test
    void targetButtons_includesPlanetsOfPlayerWhoseStatsAreVisible() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            HiddenCase c = findHiddenCase(game);
            assertThat(c).isNotNull();
            Player actor = c.actor();
            String hidden = c.planet();
            Player owner = c.owner();
            forgetEverything(actor);

            assertThat(offers(build(game, actor, PlanetTargetSpec.of(PREFIX)), hidden))
                    .isFalse();

            // Alliance members can see each other's stats, which discloses their planets.
            actor.addAllianceMember(owner.getFaction());

            assertThat(FoWHelper.canSeeStatsOfPlayer(game, owner, actor)).isTrue();
            assertThat(offers(build(game, actor, PlanetTargetSpec.of(PREFIX)), hidden))
                    .isTrue();
        }
    }

    @Test
    void targetButtons_alwaysIncludeBypassesTheKnowledgeFilter() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            HiddenCase c = findHiddenCase(game);
            assertThat(c).isNotNull();
            forgetEverything(c.actor());

            assertThat(offers(build(game, c.actor(), PlanetTargetSpec.of(PREFIX)), c.planet()))
                    .isFalse();

            var spec = PlanetTargetSpec.of(PREFIX).withAlwaysInclude(Set.of(c.planet()));
            assertThat(offers(build(game, c.actor(), spec), c.planet())).isTrue();
        }
    }

    @Test
    void targetButtons_excludeSelfOwnedDropsOnlyTheActorsPlanets() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            assertThat(actor.getPlanets()).isNotEmpty();
            String own = actor.getPlanets().getFirst();

            assertThat(offers(build(game, actor, PlanetTargetSpec.of(PREFIX)), own))
                    .isTrue();
            assertThat(offers(build(game, actor, PlanetTargetSpec.of(PREFIX).excludingSelf()), own))
                    .isFalse();
        }
    }

    @Test
    void targetButtons_publicLegalityFiltersTheList() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();

            List<Button> all = build(game, actor, PlanetTargetSpec.of(PREFIX));
            List<Button> none = build(game, actor, PlanetTargetSpec.of(PREFIX).where(p -> false));

            assertThat(all.size()).isGreaterThan(none.size());
            // Blind Target survives even when every candidate is filtered out - an empty panel would itself
            // reveal that nothing on the map qualifies.
            assertThat(none).isNotEmpty();
        }
    }

    @Test
    void targetButtons_alwaysOffersBlindTarget() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();

            assertThat(build(game, actor, PlanetTargetSpec.of(PREFIX)))
                    .anyMatch(b -> b.getCustomId() != null && b.getCustomId().startsWith("blindSelection~MDL"));
        }
    }

    @Test
    void targetButtons_outsideFogReturnsTheCallersListUntouched() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(false);
            Player actor = game.getRealPlayers().getFirst();

            List<Button> original = new ArrayList<>(List.of(Button.primary("keepMe", "Keep Me")));
            List<Button> result = PlanetTargetService.targetButtons(game, actor, PlanetTargetSpec.of(PREFIX), original);

            assertThat(result).isSameAs(original).hasSize(1);
        }
    }

    // ---- resolve: everything that is not a real, owned, legal target comes to nothing ----------

    @Test
    void resolve_returnsNullForOffMapPlanet() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player actor = game.getRealPlayers().getFirst();
            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_notaplanetatall", null))
                    .isNull();
        }
    }

    @Test
    void resolve_returnsNullWhenTheTargetHasNoController() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player actor = game.getRealPlayers().getFirst();

            String uncontrolled = game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .map(uh -> uh.getName())
                    .filter(name -> game.getTileFromPlanet(name) != null)
                    .filter(name -> game.getPlayerThatControlsPlanet(name, true) == null)
                    .findFirst()
                    .orElse(null);
            assertThat(uncontrolled)
                    .as("the default test map should have at least one uncontrolled planet")
                    .isNotNull();

            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_" + uncontrolled, null))
                    .isNull();
        }
    }

    @Test
    void resolve_returnsNullWhenHiddenLegalityFails() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player actor = game.getRealPlayers().getFirst();
            String own = actor.getPlanets().getFirst();

            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_" + own, t -> true))
                    .isNotNull();
            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_" + own, t -> false))
                    .isNull();
        }
    }

    @Test
    void resolve_reRunsThePublicLegalityFilterFromTheSpec() {
        // The whole point: a target typed into Blind Target never passed through the candidate list, so a
        // builder-only filter would be no filter at all for it. resolve() must re-apply the spec.
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            String own = actor.getPlanets().getFirst();

            var permissive = PlanetTargetSpec.of(PREFIX).where(p -> true);
            var restrictive = PlanetTargetSpec.of(PREFIX).where(p -> false);

            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_" + own, permissive, null))
                    .isNotNull();
            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_" + own, restrictive, null))
                    .isNull();
        }
    }

    @Test
    void resolve_honoursExcludeSelfOwnedInFog() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();
            String own = actor.getPlanets().getFirst();

            assertThat(PlanetTargetService.resolve(game, actor, PREFIX + "_" + own, PlanetTargetSpec.of(PREFIX), null))
                    .isNotNull();
            assertThat(PlanetTargetService.resolve(
                            game,
                            actor,
                            PREFIX + "_" + own,
                            PlanetTargetSpec.of(PREFIX).excludingSelf(),
                            null))
                    .isNull();
        }
    }

    @Test
    void resolve_appliesNoSpecDerivedCheckOutsideFog() {
        // Blind Target exists only in fog, so outside it every button id came from the component's own
        // legacy builder, which already applied that component's rules. Re-applying a different rule set
        // here could only diverge from what that builder intended.
        //
        // This is not hypothetical. Yin hero's non-fog builder offers a *controlled* space station while its
        // fog spec excludes space stations; when this check was unconditional it silently removed a non-fog
        // capability. Infiltrate is the same shape: non-fog builds its ids from another player's planets,
        // so an unconditional SELF_ONLY would fizzle every non-fog play of it.
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(false);
            Player actor = game.getRealPlayers().getFirst();
            String own = actor.getPlanets().getFirst();

            assertThat(PlanetTargetService.resolve(
                            game,
                            actor,
                            PREFIX + "_" + own,
                            PlanetTargetSpec.of(PREFIX).where(p -> false),
                            null))
                    .as("publicLegality must not apply outside fog")
                    .isNotNull();
            assertThat(PlanetTargetService.resolve(
                            game,
                            actor,
                            PREFIX + "_" + own,
                            PlanetTargetSpec.of(PREFIX).excludingSelf(),
                            null))
                    .as("ownership must not apply outside fog")
                    .isNotNull();
            assertThat(PlanetTargetService.resolve(
                            game,
                            actor,
                            PREFIX + "_" + own,
                            PlanetTargetSpec.of(PREFIX).selfOnly(),
                            null))
                    .as("SELF_ONLY must not apply outside fog either")
                    .isNotNull();
        }
    }

    @Test
    void resolve_stillRefusesNonExistentTargetsOutsideFog() {
        // The existence guards are unconditional on purpose: they only replace a crash, so they cannot
        // change behaviour for any id that previously worked.
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(false);
            Player actor = game.getRealPlayers().getFirst();

            assertThat(PlanetTargetService.resolve(
                            game, actor, PREFIX + "_notaplanetatall", PlanetTargetSpec.of(PREFIX), null))
                    .isNull();
        }
    }

    @Test
    void targetButtons_labelHidesLiveStatsForRememberedOnlyPlanets() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);

            HiddenCase c = findHiddenCase(game);
            assertThat(c).isNotNull();
            forgetEverything(c.actor());
            Tile tile = game.getTileFromPlanet(c.planet());
            c.actor().updateFogTile(tile, null);

            String label = build(game, c.actor(), PlanetTargetSpec.of(PREFIX)).stream()
                    .filter(b -> (PREFIX + "_" + c.planet()).equals(b.getCustomId()))
                    .map(Button::getLabel)
                    .findFirst()
                    .orElse(null);

            // Live resources/influence would report attachment changes made since the player last looked.
            assertThat(label).isNotNull().doesNotContain("(").doesNotContain("[DMZ]");
        }
    }

    // ---- the shared fizzle pool ---------------------------------------------------------------

    @Test
    void fizzleMessages_comeFromOneSharedPool() {
        assertThat(PlanetTargetService.messagePool()).isNotEmpty();
        // Every message a component can emit for "this came to nothing" must be in the shared pool. A
        // component-specific sentence would tell the actor WHY it failed, which is the leak this closes.
        for (int i = 0; i < 200; i++) {
            assertThat(PlanetTargetService.messagePool()).contains(PlanetTargetService.fizzleMessage());
        }
    }

    @Test
    void fizzleMessages_neverNameAReason() {
        // The pool is drawn from for a genuine no-op as well as an illegal target, so no line may hint at
        // which of the two happened.
        for (String message : PlanetTargetService.messagePool()) {
            assertThat(message.toLowerCase())
                    .doesNotContain("invalid")
                    .doesNotContain("not valid")
                    .doesNotContain("cannot")
                    .doesNotContain("no longer")
                    .doesNotContain("does not exist")
                    .doesNotContain("unowned");
        }
    }
}
