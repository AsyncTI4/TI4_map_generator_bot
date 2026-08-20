package ti4.service.fow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

/**
 * The single entry point for "let a player pick a planet belonging to someone else".
 *
 * <p>Every component that offers planet targets must go through here. Hand-rolling
 * {@code for (String planet : p2.getPlanets()) buttons.add(...)} is what produced the Fog of War leak this
 * class exists to close: in fog it handed the acting player the target's complete holdings.
 *
 * <p>Two calls make up the contract:
 * <ul>
 *   <li>{@link #targetButtons} builds a fog-safe candidate list — only planets the acting player could know
 *       exist — and appends the Blind Target escape hatch so anything else can still be named by typing it.
 *   <li>{@link #resolve} turns a pressed button back into a validated target, returning {@code null} when the
 *       target is off-map, unowned, or fails the component's own hidden-state rules. Callers answer a
 *       {@code null} with {@link #fizzle}.
 * </ul>
 *
 * <p>Components deliberately cannot word their own failure message: everything draws from
 * {@link #fizzleMessage()}, so "no legal target" is indistinguishable from "legal target, nothing happened".
 * A component-specific sentence would re-open the very oracle this design removes.
 */
@UtilityClass
public class PlanetTargetService {

    /** Whose planets may be targeted. Orthogonal to {@code requireController}. */
    public enum Ownership {
        /** Anyone's, and uncontrolled planets too. */
        ANY,
        /** Anyone but the acting player. */
        EXCLUDE_SELF,
        /** Only the acting player's own — for cards that resolve on a planet you already hold. */
        SELF_ONLY
    }

    /**
     * Which systems the candidate list is drawn from.
     *
     * <p>The distinction is not cosmetic. Knowledge has two independent sources — you have seen the system,
     * or you can see the owner's stats, which discloses every planet that player controls wherever it sits.
     * {@link #KNOWN} unions both. {@link #VISIBLE_NOW} takes neither on trust: seeing someone's stats tells
     * you what they control, not what units are sitting in a system right now.
     */
    public enum Visibility {
        /** Seen now, ever seen, or held by a player whose stats the actor can see. */
        KNOWN,
        /** Only systems currently in view. For targets that depend on live unit positions. */
        VISIBLE_NOW
    }

    /**
     * @param buttonPrefix    button id prefix; ends with {@link BlindSelectionService#TBD_FACTION} for flows
     *                        that resolve the owner at press time
     * @param excludeSelfOwned drop planets the acting player controls (they already know their own holdings)
     * @param publicLegality  optional filter, and <b>only</b> for facts a fog player could already know from
     *                        the map: planet trait, is-home-system, is-space-station. Anything depending on
     *                        hidden state (ownership, readied, units present, tokens) must NOT be filtered
     *                        here — it belongs in the hidden-legality predicate passed to {@link #resolve},
     *                        so that an illegal target fizzles rather than being visibly absent from the list.
     * @param alwaysInclude   planets forced into the list regardless of what the player knows. Used by agenda
     *                        voting, where an outcome someone already voted for must stay selectable.
     * @param requireOwned    the card needs a controller, so drop uncontrolled planets — but only where the
     *                        acting player can legitimately see that they are uncontrolled. See
     *                        {@link #targetButtons} for why that qualifier matters.
     */
    public record PlanetTargetSpec(
            String buttonPrefix,
            Ownership ownership,
            Visibility visibility,
            Predicate<Planet> publicLegality,
            Set<String> alwaysInclude,
            boolean requireController) {

        public PlanetTargetSpec {
            if (ownership == null) ownership = Ownership.ANY;
            if (visibility == null) visibility = Visibility.KNOWN;
        }

        /** Defaults: anyone's planets, everything the actor knows about, no controller requirement. */
        public static PlanetTargetSpec of(String buttonPrefix) {
            return new PlanetTargetSpec(buttonPrefix, Ownership.ANY, Visibility.KNOWN, null, null, false);
        }

        public PlanetTargetSpec ownership(Ownership o) {
            return new PlanetTargetSpec(buttonPrefix, o, visibility, publicLegality, alwaysInclude, requireController);
        }

        public PlanetTargetSpec excludingSelf() {
            return ownership(Ownership.EXCLUDE_SELF);
        }

        public PlanetTargetSpec selfOnly() {
            return ownership(Ownership.SELF_ONLY);
        }

        public PlanetTargetSpec visibility(Visibility v) {
            return new PlanetTargetSpec(buttonPrefix, ownership, v, publicLegality, alwaysInclude, requireController);
        }

        public PlanetTargetSpec visibleNowOnly() {
            return visibility(Visibility.VISIBLE_NOW);
        }

        /** Public map facts only — planet trait, home system, space station. Never hidden state. */
        public PlanetTargetSpec where(Predicate<Planet> legality) {
            return new PlanetTargetSpec(
                    buttonPrefix, ownership, visibility, legality, alwaysInclude, requireController);
        }

        public PlanetTargetSpec withAlwaysInclude(Set<String> planets) {
            return new PlanetTargetSpec(
                    buttonPrefix, ownership, visibility, publicLegality, planets, requireController);
        }

        /** For cards that cannot resolve against an uncontrolled planet (Uprising, Plague, Reparations…). */
        public PlanetTargetSpec requiringController() {
            return new PlanetTargetSpec(buttonPrefix, ownership, visibility, publicLegality, alwaysInclude, true);
        }

        /** True when this planet is filtered out for the acting player by the ownership axis. */
        boolean ownershipRejects(Player actor, String planetId) {
            boolean mine = actor.getPlanets().contains(planetId);
            return switch (ownership) {
                case ANY -> false;
                case EXCLUDE_SELF -> mine;
                case SELF_ONLY -> !mine;
            };
        }
    }

    /**
     * A specific unit holder inside a system: the space area, or one planet's surface.
     *
     * <p>Separate from {@link PlanetTargetSpec} because a planet-shaped API cannot express "the space area of
     * a system", and a system can hold the same unit in space <i>and</i> on each of its planets — so naming
     * only the system is ambiguous.
     *
     * <p>Defaults to {@link Visibility#VISIBLE_NOW}. A unit is live hidden state: offering a remembered
     * system as a live button would assert the unit is still there. Memory stays reachable through Blind
     * Target, which for this shape names the holder rather than just the system.
     *
     * @param publicLegality takes the tile as well as the holder, because the space holder has no planet to
     *                       look a tile up from.
     */
    public record UnitHolderTargetSpec(
            String buttonPrefix,
            UnitType unit,
            Ownership ownership,
            Visibility visibility,
            BiPredicate<Tile, UnitHolder> publicLegality) {

        public UnitHolderTargetSpec {
            if (ownership == null) ownership = Ownership.ANY;
            if (visibility == null) visibility = Visibility.VISIBLE_NOW;
        }

        public static UnitHolderTargetSpec of(String buttonPrefix, UnitType unit) {
            return new UnitHolderTargetSpec(buttonPrefix, unit, Ownership.ANY, Visibility.VISIBLE_NOW, null);
        }

        public UnitHolderTargetSpec excludingSelf() {
            return new UnitHolderTargetSpec(buttonPrefix, unit, Ownership.EXCLUDE_SELF, visibility, publicLegality);
        }

        public UnitHolderTargetSpec visibility(Visibility v) {
            return new UnitHolderTargetSpec(buttonPrefix, unit, ownership, v, publicLegality);
        }

        public UnitHolderTargetSpec where(BiPredicate<Tile, UnitHolder> legality) {
            return new UnitHolderTargetSpec(buttonPrefix, unit, ownership, visibility, legality);
        }
    }

    /** A unit holder that exists, contains the spec's unit, and has an owner the spec permits. */
    public record ResolvedHolder(Tile tile, UnitHolder holder, Player owner) {}

    /**
     * Buttons for every holder containing {@code spec.unit()}, as {@code <prefix>_<position>_<space|planetId>}.
     * Appends the unit-holder Blind Target button. Outside fog the caller's list is returned untouched.
     */
    public static List<Button> unitHolderTargetButtons(
            Game game, Player actor, UnitHolderTargetSpec spec, List<Button> nonFogButtons) {
        if (!game.isFowMode()) {
            return nonFogButtons;
        }
        Set<String> positions = spec.visibility() == Visibility.VISIBLE_NOW
                ? FoWHelper.getTilePositionsToShow(game, actor)
                : FoWHelper.getKnownTilePositions(game, actor);
        UnaryOperator<String> label = fogSafeLabeller(game, actor);

        List<Button> buttons = new ArrayList<>(nonFogButtons);
        for (Tile tile : game.getTileMap().values()) {
            if (!positions.contains(tile.getPosition())) continue;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (ownerOfUnitIn(game, actor, spec, holder) == null) continue;
                if (spec.publicLegality() != null && !spec.publicLegality().test(tile, holder)) continue;
                boolean isSpace = Constants.SPACE.equals(holder.getName());
                buttons.add(Buttons.gray(
                        spec.buttonPrefix() + "_" + tile.getPosition() + "_"
                                + (isSpace ? Constants.SPACE : holder.getName()),
                        isSpace ? tile.getRepresentationForButtons(game, actor) : label.apply(holder.getName())));
            }
        }
        buttons.sort((a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        BlindSelectionService.appendBlindUnitHolderTargetButton(buttons, spec.buttonPrefix());
        return buttons;
    }

    /**
     * Validates a pressed unit-holder button. Null when the system, holder, unit or a permitted owner is
     * missing, or the spec's public legality rejects it.
     *
     * <p>As with {@link #resolve}, the spec's rules apply in fog only — outside it the component's own
     * builder is authoritative.
     */
    public static ResolvedHolder resolveUnitHolder(
            Game game, Player actor, String buttonID, UnitHolderTargetSpec spec) {
        String[] parts = buttonID.split("_");
        if (parts.length < 4) return null;
        Tile tile = game.getTileByPosition(parts[2]);
        if (tile == null) return null;

        UnitHolder holder = Constants.SPACE.equalsIgnoreCase(parts[3])
                ? tile.getUnitHolders().get(Constants.SPACE)
                : tile.getUnitHolderFromPlanet(parts[3]);
        if (holder == null) return null;

        Player owner = BlindSelectionService.TBD_FACTION.equals(parts[1])
                ? ownerOfUnitIn(game, actor, spec, holder)
                : game.getPlayerFromColorOrFaction(parts[1]);
        if (owner == null || owner.getColor() == null) return null;
        if (holder.getUnitCount(spec.unit(), owner.getColor()) <= 0) return null;

        if (game.isFowMode()
                && spec.publicLegality() != null
                && !spec.publicLegality().test(tile, holder)) {
            return null;
        }
        return new ResolvedHolder(tile, holder, owner);
    }

    /** First player the spec's ownership permits who has the spec's unit in this holder, or null. */
    private static Player ownerOfUnitIn(Game game, Player actor, UnitHolderTargetSpec spec, UnitHolder holder) {
        for (Player candidate : game.getRealPlayers()) {
            if (candidate.getColor() == null) continue;
            boolean self = candidate == actor;
            boolean permitted =
                    switch (spec.ownership()) {
                        case ANY -> true;
                        case EXCLUDE_SELF -> !self;
                        case SELF_ONLY -> self;
                    };
            if (!permitted) continue;
            if (holder.getUnitCount(spec.unit(), candidate.getColor()) > 0) return candidate;
        }
        return null;
    }

    /** A target that exists on the map and has a controller. */
    public record ResolvedTarget(String planetId, Planet unitHolder, Player owner, Tile tile) {}

    /**
     * Flavour for anything that came to nothing. One shared pool, drawn from for BOTH a fizzle and a
     * legitimate no-effect outcome, so the two cannot be told apart. Lines must never name a unit type, an
     * owner, a planet, or a reason.
     */
    private static final List<String> FIZZLE_MESSAGES = List.of(
            "Your operatives reached the coordinates and found nothing worth the fuel.",
            "The strike package launched, drifted through empty orbit, and self-terminated.",
            "Telemetry returned static. Whatever was there is not there now.",
            "The Winnaran archives list that designation as \"disputed, unsurveyed, or invented\".",
            "Your agents filed a report. It was three pages long and said nothing.",
            "The coordinates resolved to a debris field and a very old warning beacon.",
            "Something in the Shaleri Passage ate the transmission. Nothing was accomplished.",
            "The order was carried out to the letter. The letter, it turns out, addressed no one.",
            "A gravity rift swallowed the courier drone. The operation lapsed.",
            "Local command acknowledged, saluted, and did absolutely nothing.",
            "The target designation matched a system that has not existed since the Twilight Wars.",
            "Your fleet held position for six hours, found no purchase, and withdrew.",
            "The saboteurs got in, got bored, and got out.",
            "The Mecatol relay logged your request and quietly filed it under \"later\".",
            "The plan was flawless. The planet was not where the plan said it would be.",
            "Comms fog. By the time it cleared, there was nothing left to act upon.",
            "Your quartermaster reports the expenditure. Your admiral reports no result.",
            "The jump completed. The manifest did not match the sky.",
            "Whatever your intelligence promised, the void declined to provide.",
            "The operation is recorded as concluded. No further details are available.");

    /**
     * Button text for a candidate planet.
     *
     * <p>{@code Helper.getPlanetRepresentation} appends <i>live</i> resources/influence and a {@code [DMZ]}
     * marker. For a system the player can see right now that is fine - they can read it off the map. For one
     * they merely remember, it would report attachment and token changes made since they last looked, so
     * those fall back to the planet's static name.
     */
    /**
     * Labels planets the way {@link #targetButtons} does, with the viewer's visibility computed once.
     *
     * <p>{@code Helper.getPlanetRepresentation} appends <i>live</i> resources/influence and a {@code [DMZ]}
     * marker. For a system the viewer can see right now that is fine - they can read it off the map. For one
     * they merely remember, or have never seen at all, it would report attachment and token changes made
     * since, so those fall back to the planet's static name.
     *
     * <p>Outside fog everything is live; with a null viewer everything falls back to static names.
     */
    public static UnaryOperator<String> fogSafeLabeller(Game game, Player viewer) {
        // Computed once, not per planet: getTilePositionsToShow walks the map and its adjacency, so calling
        // it inside a labelling loop is quadratic.
        Set<String> visibleNow =
                game.isFowMode() && viewer != null ? FoWHelper.getTilePositionsToShow(game, viewer) : Set.of();
        boolean liveEverywhere = !game.isFowMode();
        return planetId -> {
            Tile tile = game.getTileFromPlanet(planetId);
            if (liveEverywhere || (tile != null && visibleNow.contains(tile.getPosition()))) {
                return Helper.getPlanetRepresentation(planetId, game);
            }
            String staticName = Mapper.getPlanetRepresentations().get(planetId);
            return staticName == null ? planetId : staticName;
        };
    }

    /** A random line from the shared pool. Use for fizzles AND for legal-but-no-effect outcomes alike. */
    public static String fizzleMessage() {
        return FIZZLE_MESSAGES.get(ThreadLocalRandom.current().nextInt(0, FIZZLE_MESSAGES.size()));
    }

    /** Unmodifiable view of the shared pool, for tests asserting both paths draw from it. */
    public static List<String> messagePool() {
        return Collections.unmodifiableList(FIZZLE_MESSAGES);
    }

    /**
     * Every planet the acting player could know exists: on a system they can see or have ever seen, or held
     * by a player whose stats they can see. Plus anything in {@code alwaysInclude}, which bypasses the filter.
     *
     * <p>Exposed for the handful of flows whose button ids are not {@code prefix_planetId} and so cannot use
     * {@link #targetButtons} - they still have to apply the same rule.
     */
    public static Set<String> knownPlanetIds(Game game, Player actor, Set<String> alwaysInclude) {
        return candidatePlanetIds(game, actor, Visibility.KNOWN, alwaysInclude);
    }

    /** The candidate set for a given {@link Visibility}. */
    public static Set<String> candidatePlanetIds(
            Game game, Player actor, Visibility visibility, Set<String> alwaysInclude) {
        Set<String> candidates = new HashSet<>();
        if (alwaysInclude != null) {
            candidates.addAll(alwaysInclude);
        }
        if (!game.isFowMode()) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder uh : tile.getUnitHolders().values()) {
                    if (uh instanceof Planet) candidates.add(uh.getName());
                }
            }
            return candidates;
        }

        // Compute visibility once. canSeeStatsOfPlayer runs initializeFog internally, so calling either of
        // these per planet instead of per player is quadratic on big maps.
        Set<String> positions = visibility == Visibility.VISIBLE_NOW
                ? FoWHelper.getTilePositionsToShow(game, actor)
                : FoWHelper.getKnownTilePositions(game, actor);
        for (Tile tile : game.getTileMap().values()) {
            if (!positions.contains(tile.getPosition())) continue;
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                // Not getPlanetUnitHolders(): that drops space stations, which several cards can target.
                if (uh instanceof Planet) {
                    candidates.add(uh.getName());
                }
            }
        }
        // The second half of KNOWN, and it must stay inside this branch: seeing a player's stats discloses
        // every planet they control, wherever it is, which is knowledge VISIBLE_NOW deliberately does not
        // grant. Guarded by targetButtons_includesPlanetsOfPlayerWhoseStatsAreVisible.
        if (visibility == Visibility.KNOWN) {
            for (Player p2 : game.getRealPlayers()) {
                if (FoWHelper.canSeeStatsOfPlayer(game, p2, actor)) {
                    candidates.addAll(p2.getPlanets());
                }
            }
        }
        return candidates;
    }

    /**
     * Outside fog, returns {@code nonFogButtons} untouched — existing behaviour is preserved exactly.
     * In fog, replaces it with planets the player could know about, plus a Blind Target button.
     */
    public static List<Button> targetButtons(
            Game game, Player actor, PlanetTargetSpec spec, List<Button> nonFogButtons) {
        if (!game.isFowMode()) {
            return nonFogButtons;
        }

        Set<String> candidates = candidatePlanetIds(game, actor, spec.visibility(), spec.alwaysInclude());
        Set<String> visibleNow = FoWHelper.getTilePositionsToShow(game, actor);
        UnaryOperator<String> label = fogSafeLabeller(game, actor);

        List<Button> buttons = new ArrayList<>();
        for (String planetId : candidates) {
            Planet planet = ButtonHelper.getUnitHolderFromPlanetName(planetId, game);
            if (planet == null) continue;
            if (spec.ownershipRejects(actor, planetId)) continue;
            if (spec.publicLegality() != null && !spec.publicLegality().test(planet)) continue;
            // Cards that need a controller can drop uncontrolled planets - but only where the player can see
            // that they are uncontrolled. On a system they can see right now, control is visible on the map,
            // so filtering tells them nothing new. On one they merely remember, whether anybody has taken it
            // since is exactly the hidden fact that must not be answered by a planet's presence in the list,
            // so those stay in and come to nothing at resolution instead.
            if (spec.requireController()) {
                Tile tile = game.getTileFromPlanet(planetId);
                boolean seeItNow = tile != null && visibleNow.contains(tile.getPosition());
                if (seeItNow && game.getPlayerThatControlsPlanet(planetId, true) == null) continue;
            }
            buttons.add(Buttons.gray(spec.buttonPrefix() + "_" + planetId, label.apply(planetId)));
        }
        // Sort by label so ordering carries no signal about why a planet is in the list.
        buttons.sort((a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));

        BlindSelectionService.appendBlindTargetButton(buttons, spec.buttonPrefix(), true);
        return buttons;
    }

    /**
     * Validates a pressed target button. Returns {@code null} when the planet is not on the map, has no
     * controller, or fails either legality check — all of which the caller must answer with {@link #fizzle}.
     *
     * <p><b>Pass the same {@code spec} used to build the buttons.</b> Its {@code publicLegality} is re-run
     * here, not just at list-build time: a target typed into Blind Target never passed through the list, so a
     * builder-only filter would be no filter at all for it. Blind selection deliberately does not reject
     * unknown targets up front — telling the player their guess was illegal is exactly the yes/no oracle this
     * design removes — so every rule has to be enforced at resolution instead.
     *
     * @param spec           the spec the buttons were built from; nullable when the component has no
     *                       public-info rules.
     * @param hiddenLegality rules depending on state a fog player cannot see (readied, units present).
     *                       Nullable when the component has none.
     */
    public static ResolvedTarget resolve(
            Game game, Player actor, String buttonID, PlanetTargetSpec spec, Predicate<ResolvedTarget> hiddenLegality) {
        String[] parts = buttonID.split("_");
        if (parts.length < 3) return null;
        String planetId = parts[2];

        Planet unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planetId, game);
        if (unitHolder == null) return null;

        Player owner = BlindSelectionService.ownerOf(game, parts[1], planetId);
        if (owner == null) return null;

        Tile tile = game.getTileFromPlanet(planetId);
        if (tile == null) return null;

        // Spec-derived checks apply in fog ONLY. Blind Target exists only in fog, so outside it every button
        // id came from the component's own legacy builder, which already applied that component's rules -
        // re-applying a different rule set here could only diverge from what the builder intended. The
        // existence guards above stay unconditional, because they only replace a crash.
        if (game.isFowMode() && spec != null) {
            if (spec.publicLegality() != null && !spec.publicLegality().test(unitHolder)) return null;
            if (spec.ownershipRejects(actor, planetId)) return null;
        }

        ResolvedTarget target = new ResolvedTarget(planetId, unitHolder, owner, tile);
        if (hiddenLegality != null && !hiddenLegality.test(target)) return null;
        return target;
    }

    /** For components with no public-info rules of their own. */
    public static ResolvedTarget resolve(
            Game game, Player actor, String buttonID, Predicate<ResolvedTarget> hiddenLegality) {
        return resolve(game, actor, buttonID, null, hiddenLegality);
    }

    /**
     * Ends a target flow that came to nothing. Deletes the prompt and tells the actor only that it came to
     * nothing — never why. Must be called before any state is mutated.
     */
    public static void fizzle(ButtonInteractionEvent event, Player actor) {
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                actor.getCorrectChannel(), actor.getRepresentationUnfogged() + ", " + fizzleMessage());
    }

    /** Convenience for handlers that have no event to delete (e.g. inside a regex matcher). */
    public static void fizzle(Player actor) {
        MessageHelper.sendMessageToChannel(
                actor.getCorrectChannel(), actor.getRepresentationUnfogged() + ", " + fizzleMessage());
    }
}
