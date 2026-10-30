package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.trade.ComponentValues;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
class TableCards {

    static final String SEIZE_ARTIFACT = "seize";
    static final String REPEAL_LAW = "repeal";
    static final String BRILLIANCE = "brilliance";
    private static final String SEIZE_PLAYER_PREFIX = "seizeArtifactStep2_";
    private static final String SEIZE_FRAGMENT_PREFIX = "seizeArtifactStep3_";
    private static final String REPEAL_PREFIX = "repealLaw_";
    private static final String READY_PREFIX = "biostimsReady_planet_";
    private static final String UNLOCK_PREFIX = "resolveBrillianceUnlock_";
    private static final String SEPARATOR = "_";
    private static final String NO_TECH = "no";
    private static final String READY = "ready:";
    private static final String UNLOCK = "unlock:";
    private static final double DENIAL_SHARE = 0.5;
    private static final double BREAKTHROUGH_VALUE = 2.0;
    private static final double MINISTER_DENIAL = 0.5;
    private static final double HARM_REPEALED = 3.0;
    private static final double PLANET_HARM_REPEALED = 1.5;

    static AiCard seizeArtifact() {
        return AiActionCards.component(
                SEIZE_ARTIFACT,
                TableCards::seizePlan,
                "resolveSeizeArtifactStep1",
                List.of(SEIZE_PLAYER_PREFIX, SEIZE_FRAGMENT_PREFIX),
                CardChains.chain(
                        CardChains.next(
                                SEIZE_PLAYER_PREFIX,
                                Source.MENTIONED,
                                (context, suffix) -> seizePlayerValue(context.game(), context.seat(), first(suffix)),
                                "choose the neighbour"),
                        CardChains.last(
                                SEIZE_FRAGMENT_PREFIX,
                                Source.ANYONE,
                                (context, suffix) -> seizeFragmentValue(context.game(), context.seat(), suffix),
                                "seize the fragment")));
    }

    static AiCard repealLaw() {
        return AiActionCards.component(
                REPEAL_LAW,
                TableCards::repealPlan,
                "getRepealLawButtons",
                List.of(REPEAL_PREFIX),
                CardChains.chain(CardChains.last(
                        REPEAL_PREFIX,
                        Source.ANYONE,
                        (context, number) -> repealValue(context.game(), context.seat(), number),
                        "repeal law")));
    }

    static AiCard brilliance() {
        return AiActionCards.component(
                BRILLIANCE,
                TableCards::brilliancePlan,
                "brilliance",
                List.of(READY_PREFIX, UNLOCK_PREFIX),
                TableCards::brillianceStep);
    }

    private static String first(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(0, suffix.indexOf(SEPARATOR)) : suffix;
    }

    private static String rest(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(suffix.indexOf(SEPARATOR) + 1) : "";
    }

    private static List<Player> neighboursWithFragments(Game game, Player seat) {
        return seat.getNeighbouringPlayers(true).stream()
                .filter(neighbour ->
                        neighbour != seat && !neighbour.getFragments().isEmpty())
                .toList();
    }

    static Optional<Plan> seizePlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return neighboursWithFragments(game, seat).stream()
                .flatMap(neighbour -> neighbour.getFragments().stream().map(fragment -> new Seize(neighbour, fragment)))
                .max(Comparator.comparingDouble((Seize seize) -> seizeValue(game, seat, seize))
                        .thenComparing(seize -> seize.neighbour().getFaction())
                        .thenComparing(Seize::fragment))
                .map(seize -> Plan.aimedAt(
                        seizeValue(game, seat, seize),
                        CardChains.target(
                                seize.neighbour().getFaction() + SEPARATOR + NO_TECH,
                                seize.neighbour().getFaction() + SEPARATOR + seize.fragment()),
                        "from " + seize.neighbour().getRepresentationNoPing() + "."));
    }

    private record Seize(Player neighbour, String fragment) {}

    private static double seizeValue(Game game, Player seat, Seize seize) {
        double gained = ComponentValues.gainedFragment(seat, kindOf(seize.fragment()));
        return gained * (1 + DENIAL_SHARE * TableStanding.of(game, seat, seize.neighbour()));
    }

    private static String kindOf(String fragment) {
        return fragment.length() >= 3 ? fragment.substring(0, 3).toUpperCase() : fragment.toUpperCase();
    }

    private static double seizePlayerValue(Game game, Player seat, String faction) {
        Player other = game.getPlayerFromColorOrFaction(faction);
        if (other == null || other == seat || other.getFragments().isEmpty()) return Double.NEGATIVE_INFINITY;
        return other.getFragments().stream()
                .mapToDouble(fragment -> seizeValue(game, seat, new Seize(other, fragment)))
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    private static double seizeFragmentValue(Game game, Player seat, String suffix) {
        Player other = game.getPlayerFromColorOrFaction(first(suffix));
        String fragment = rest(suffix);
        if (other == null || other == seat || !other.getFragments().contains(fragment)) {
            return Double.NEGATIVE_INFINITY;
        }
        return seizeValue(game, seat, new Seize(other, fragment));
    }

    static Optional<Plan> repealPlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return game.getLaws().keySet().stream()
                .map(law -> Plan.aimedAt(
                        lawHarm(game, seat, law), String.valueOf(game.getLaws().get(law)), "on " + law + "."))
                .filter(plan -> plan.value() > 0)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target));
    }

    private static double repealValue(Game game, Player seat, String number) {
        return game.getLaws().entrySet().stream()
                .filter(law -> number.equals(String.valueOf(law.getValue())))
                .mapToDouble(law -> lawHarm(game, seat, law.getKey()))
                .filter(harm -> harm > 0)
                .findFirst()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    static double lawHarm(Game game, Player seat, String law) {
        String subject = game.getLawsInfo().getOrDefault(law, "");
        Player elected = game.getPlayerFromColorOrFaction(subject);
        boolean seatIsElected = elected == seat;
        if (AgendaPolicy.punishesElectedPlayer(law)) return seatIsElected ? HARM_REPEALED : 0;
        if (AgendaPolicy.favoursElectedPlayer(law)) return favoursRival(game, seat, law, elected);
        if (AgendaPolicy.punishesPlanetOwner(law))
            return seat.getPlanets().contains(subject) ? PLANET_HARM_REPEALED : 0;
        return 0;
    }

    private static double favoursRival(Game game, Player seat, String law, Player elected) {
        if (elected == null || elected == seat) return 0;
        double standing = TableStanding.of(game, seat, elected);
        if (AgendaPolicy.givesElectedPlayerAPoint(law)) return ScoringStakes.rivalStake(game, elected, 1) * standing;
        return MINISTER_DENIAL * standing;
    }

    static Optional<Plan> brilliancePlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        List<Option> options = new ArrayList<>();
        for (String planet : seat.getExhaustedPlanets()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                options.add(new Option(READY + planet, ReadyingValue.of(game, seat, List.of(planet))));
            }
        }
        for (Player player : game.getRealPlayers()) {
            if (lockedBreakthroughs(player) > 0) {
                options.add(new Option(UNLOCK + player.getFaction(), unlockValue(game, seat, player)));
            }
        }
        return options.stream()
                .filter(option -> option.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Option::value).thenComparing(Option::target))
                .map(option -> Plan.aimedAt(option.value(), option.target(), "."));
    }

    private record Option(String target, double value) {}

    private static long lockedBreakthroughs(Player player) {
        return player.getBreakthroughIDs().stream()
                .filter(id -> !player.isBreakthroughUnlocked(id))
                .count();
    }

    private static double unlockValue(Game game, Player seat, Player player) {
        if (player == seat) return BREAKTHROUGH_VALUE;
        if (ScoringStakes.contender(game, seat, player)) return Double.NEGATIVE_INFINITY;
        return -player.getTotalVictoryPoints() * 1e-3;
    }

    private static Optional<AiDecision> brillianceStep(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        List<Offer> ready = CardPrompts.unmentionedTargetOffers(context, play, READY_PREFIX).stream()
                .map(offer -> new Offer(offer.match(), READY + offer.suffix()))
                .toList();
        List<Offer> unlock = CardPrompts.unmentionedTargetOffers(context, play, UNLOCK_PREFIX).stream()
                .map(offer -> new Offer(offer.match(), UNLOCK + offer.suffix()))
                .toList();
        List<Offer> offers = new ArrayList<>(ready);
        offers.addAll(unlock);
        offers = offers.stream()
                .filter(offer -> optionValue(game, seat, offer.suffix()) > Double.NEGATIVE_INFINITY)
                .toList();
        Optional<Offer> choice = CardPrompts.choose(offers, play.extra(), suffix -> optionValue(game, seat, suffix));
        choice.ifPresent(offer -> PlayMemory.moveTo(context, key, play, Stage.DONE));
        return choice.map(offer -> offer.match().press("use Brilliance: " + offer.suffix()));
    }

    private static double optionValue(Game game, Player seat, String option) {
        if (option.startsWith(READY)) {
            return ReadyingValue.of(game, seat, List.of(option.substring(READY.length())));
        }
        Player player = game.getPlayerFromColorOrFaction(option.substring(UNLOCK.length()));
        return player == null ? Double.NEGATIVE_INFINITY : unlockValue(game, seat, player);
    }
}
