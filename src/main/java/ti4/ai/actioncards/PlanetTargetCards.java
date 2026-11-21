package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.explore.CardValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.trade.ComponentValues;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.model.ExploreModel;
import ti4.model.Mapper;

@UtilityClass
class PlanetTargetCards {

    static final String UPRISING = "uprising";
    static final String ARCHAEOLOGICAL_EXPEDITION = "arch_expedition";
    private static final String UPRISING_PLAYER_PREFIX = "uprisingStep2_";
    private static final String UPRISING_PLANET_PREFIX = "uprisingStep3_";
    private static final String EXPEDITION_PREFIX = "arcExp_";
    private static final String SEPARATOR = "_";
    private static final double DENIAL_SHARE = 0.25;
    private static final int EXPEDITION_DRAWS = 3;
    private static final List<String> EXPEDITION_TYPES = List.of("industrial", "cultural", "hazardous");

    private record Target(Player owner, String planet, double value) {

        String suffix() {
            return owner.getFaction() + SEPARATOR + planet;
        }
    }

    static AiCard uprising() {
        return AiActionCards.component(
                UPRISING,
                PlanetTargetCards::uprisingPlan,
                "resolveUprisingStep1",
                List.of(UPRISING_PLAYER_PREFIX, UPRISING_PLANET_PREFIX),
                PlanetTargetCards::uprisingStep);
    }

    static AiCard archaeologicalExpedition() {
        return AiActionCards.component(
                ARCHAEOLOGICAL_EXPEDITION,
                PlanetTargetCards::expeditionPlan,
                null,
                List.of(EXPEDITION_PREFIX),
                PlanetTargetCards::expeditionStep);
    }

    static Optional<Plan> uprisingPlan(Game game, Player seat) {
        return targets(game, seat).stream()
                .max(Comparator.comparingDouble(Target::value).thenComparing(Target::suffix, Comparator.reverseOrder()))
                .map(target -> Plan.aimedAt(
                        target.value(),
                        target.suffix(),
                        "on " + planetName(game, target.planet()) + ", a planet of "
                                + target.owner().getRepresentationNoPing() + "."));
    }

    private static List<Target> targets(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(owner -> owner != seat)
                .flatMap(owner -> owner.getReadiedPlanets().stream()
                        .filter(planet -> isNotHome(game, planet))
                        .map(planet -> new Target(owner, planet, uprisingValue(game, seat, owner, planet))))
                .toList();
    }

    private static boolean isNotHome(Game game, String planet) {
        Tile tile = game.getTileFromPlanet(planet);
        return tile != null && !tile.isHomeSystem(game);
    }

    static double uprisingValue(Game game, Player seat, Player owner, String planet) {
        int resources = BoardView.planetResources(game, planet);
        int influence = BoardView.planetInfluence(game, planet);
        double gain = resources + ScoringStakes.scoreValue(game, seat, SpendUnlock.pointDelta(game, seat, resources));
        double denial =
                owner.isPassed() ? 0 : DENIAL_SHARE * (resources + influence) * TableStanding.of(game, seat, owner);
        double broken = ScoringStakes.rivalStake(game, owner, SpendUnlock.pointsLostByExhausting(game, owner, planet));
        return gain + denial + broken;
    }

    private static String planetName(Game game, String planet) {
        Planet holder = game.getPlanetsInfo().get(planet);
        return holder == null ? planet : holder.getName();
    }

    private static Optional<AiDecision> uprisingStep(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        List<Offer> planets = CardPrompts.targetOffers(context, play, UPRISING_PLANET_PREFIX);
        if (!planets.isEmpty()) {
            Optional<Offer> choice =
                    CardPrompts.choose(planets, play.extra(), suffix -> planetValue(game, seat, suffix));
            choice.ifPresent(offer -> PlayMemory.moveTo(context, key, play, Stage.DONE));
            return choice.map(offer -> offer.match().press("exhaust " + offer.suffix() + " with Uprising"));
        }
        List<Offer> players = CardPrompts.targetOffers(context, play, UPRISING_PLAYER_PREFIX);
        String plannedOwner = StringUtils.substringBefore(play.extra(), SEPARATOR);
        return CardPrompts.choose(players, plannedOwner, faction -> ownerValue(game, seat, faction))
                .map(offer -> offer.match().press("rise up against " + offer.suffix()));
    }

    private static double planetValue(Game game, Player seat, String suffix) {
        Player owner = game.getPlayerFromColorOrFaction(StringUtils.substringBefore(suffix, SEPARATOR));
        return owner == null
                ? Double.NEGATIVE_INFINITY
                : uprisingValue(game, seat, owner, StringUtils.substringAfter(suffix, SEPARATOR));
    }

    private static double ownerValue(Game game, Player seat, String faction) {
        Player owner = game.getPlayerFromColorOrFaction(faction);
        if (owner == null) return Double.NEGATIVE_INFINITY;
        return targets(game, seat).stream()
                .filter(target -> target.owner() == owner)
                .mapToDouble(Target::value)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    static Optional<Plan> expeditionPlan(Game game, Player seat) {
        Set<String> owned = ButtonHelper.getTypesOfPlanetPlayerHas(game, seat);
        return EXPEDITION_TYPES.stream()
                .filter(owned::contains)
                .map(type -> Plan.aimedAt(
                        EXPEDITION_DRAWS * fragmentValuePerCard(game, seat, type),
                        type,
                        "on the " + type + " exploration deck."))
                .max(Comparator.comparingDouble(Plan::value));
    }

    private static double fragmentValuePerCard(Game game, Player seat, String type) {
        List<String> deck = game.getExploreDeck(type);
        if (deck.isEmpty()) deck = game.getExploreDiscard(type);
        if (deck.isEmpty()) return 0;
        double total = 0;
        for (String card : deck) {
            ExploreModel model = Mapper.getExplore(card);
            if (model == null || !Constants.FRAGMENT.equalsIgnoreCase(model.getResolution())) continue;
            total += ComponentValues.gainedFragment(seat, CardValue.kind(card).toUpperCase());
        }
        return total / deck.size();
    }

    private static Optional<AiDecision> expeditionStep(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        List<Offer> offers = CardPrompts.targetOffers(context, play, EXPEDITION_PREFIX);
        Optional<Offer> choice =
                CardPrompts.choose(offers, play.extra(), type -> fragmentValuePerCard(game, seat, type));
        choice.ifPresent(offer -> PlayMemory.moveTo(context, key, play, Stage.DONE));
        return choice.map(offer -> offer.match().press("explore the " + offer.suffix() + " deck three times"));
    }
}
