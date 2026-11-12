package ti4.ai.agenda;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.promise.Promise;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.model.AgendaModel;
import ti4.model.Mapper;

@UtilityClass
public class AgendaVoting {

    private static final String BALLOT_KEY = "agendaBallot|";
    private static final String OUTCOME_PREFIX = "outcome_";
    private static final String PLANET_OUTCOMES_PREFIX = "planetOutcomes_";
    private static final String PLANET_VOTE_PREFIX = "exhaustForVotes_planet_";
    private static final String CONFIRM_PREFIX = "resolveAgendaVote_";
    private static final String PREDICTIVE_VOTES = "predictive_3";
    private static final String PREDICTIVE_INTELLIGENCE = "pi";
    private static final int PREDICTIVE_VOTE_COUNT = 3;
    private static final String PREDICTIVE_BUTTON = "exhaustForVotes_" + PREDICTIVE_VOTES;
    private static final String SPENT_PLANET_PREFIX = "planet_";
    private static final String VOTED_AT_KEY = "agendaVotedAt|";
    private static final String GILA_KEY = "agendaGilaTradeGoods|";
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final String FIELD = "~";
    private static final String GALACTIC_THREAT = "galactic_threat";
    private static final String XXCHA_COMMANDER = "xxchacommander";
    private static final String ASSASSINATED = "AssassinatedReps";

    record Ballot(String outcome, String planetOwner, int votes, boolean predictive) {

        Ballot(String outcome, String planetOwner, int votes) {
            this(outcome, planetOwner, votes, false);
        }

        String encode() {
            return String.join(FIELD, outcome, planetOwner, String.valueOf(votes), String.valueOf(predictive));
        }

        static Optional<Ballot> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length < 3 || fields.length > 4 || !StringUtils.isNumeric(fields[2])) {
                return Optional.empty();
            }
            boolean predictive = fields.length == 4 && Boolean.parseBoolean(fields[3]);
            return Optional.of(new Ballot(fields[0], fields[1], Integer.parseInt(fields[2]), predictive));
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Game game = context.game();
        if (!"agendaVoting".equalsIgnoreCase(game.getPhaseOfGame()) || !context.isActivePlayer()) {
            return Optional.empty();
        }
        String faction = context.faction();
        List<AiPrompt> turn = Prompts.thisTurn(context);
        String key = BALLOT_KEY + game.getCurrentAgendaInfo();
        Optional<Match> vote = Prompts.owned(turn, faction, "vote"::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (vote.isPresent()) {
            Optional<Ballot> ballot = plan(game, context.seat());
            if (ballot.isEmpty()) return Optional.empty();
            context.memory().put(key, ballot.get().encode());
            context.memory().put(VOTED_AT_KEY + game.getCurrentAgendaInfo(), String.valueOf(context.now()));
            return Optional.of(vote.get().press("vote for " + ballot.get().outcome()));
        }
        Optional<Ballot> ballot =
                context.memory().get(key).flatMap(Ballot::decode).map(planned -> keepingPromise(context, key, planned));
        if (ballot.isEmpty()) {
            Optional<AiPrompt> offered =
                    outcomePrompts(turn, faction, 0).stream().findFirst();
            if (offered.isPresent()) {
                Ballot planned = plan(game, context.seat()).orElse(new Ballot("", "", 0));
                context.memory().put(key, planned.encode());
                context.memory()
                        .put(
                                VOTED_AT_KEY + game.getCurrentAgendaInfo(),
                                String.valueOf(offered.get().createdAtMillis()));
                ballot = Optional.of(planned);
            }
        }
        if (ballot.isEmpty()) return Optional.empty();
        Optional<AiDecision> confirm = Prompts.owned(turn, faction, AgendaVoting::confirmsVotes)
                .map(match -> {
                    context.memory().remove(key);
                    return match.press("confirm its votes");
                });
        if (confirm.isPresent()) return confirm;
        Optional<AiDecision> planets = spendPlanets(context, turn, ballot.get());
        if (planets.isPresent()) return planets;
        return chooseOutcome(context, turn, ballot.get());
    }

    static Ballot keepingPromise(AiTurnContext context, String key, Ballot planned) {
        Game game = context.game();
        boolean breaksPromise = PromisedVotes.binding(game, context.seat())
                .filter(promise -> !promise.target().equalsIgnoreCase(planned.outcome())
                        || castsTooFew(game, context.seat(), promise, planned))
                .isPresent();
        if (!breaksPromise) return planned;
        Ballot kept = plan(game, context.seat()).orElse(planned);
        context.memory().put(key, kept.encode());
        return kept;
    }

    private static boolean castsTooFew(Game game, Player seat, Promise promise, Ballot planned) {
        int promised = promisedVotes(promise, readiedPlanetVotes(game, seat), game);
        return planned.votes() < promised && planned.votes() < castableVotes(game, seat);
    }

    private static boolean confirmsVotes(String handlerId) {
        String votes = StringUtils.removeStart(handlerId, CONFIRM_PREFIX);
        return handlerId.startsWith(CONFIRM_PREFIX) && StringUtils.isNumeric(votes) && !"0".equals(votes);
    }

    static boolean cannotVote(Game game, Player seat) {
        return seat.hasAbility(GALACTIC_THREAT) && !game.playerHasLeaderUnlockedOrAlliance(seat, XXCHA_COMMANDER);
    }

    private static boolean sitsOutThisAgenda(Game game, Player seat) {
        if (game.playerHasLeaderUnlockedOrAlliance(seat, XXCHA_COMMANDER)) return false;
        return game.getStoredValue(ASSASSINATED).contains(seat.getFaction()) || placedARider(game, seat);
    }

    private static boolean placedARider(Game game, Player seat) {
        return game.getCurrentAgendaVotes().values().stream()
                .flatMap(votes -> Arrays.stream(votes.split(";")))
                .filter(piece -> piece.contains("Rider") || piece.contains("Sanction"))
                .map(piece -> StringUtils.substringBefore(piece, "_").toLowerCase())
                .anyMatch(owner -> game.getPlayerFromColorOrFaction(owner) == seat);
    }

    private static long votedAt(AiTurnContext context) {
        return context.memory()
                .get(VOTED_AT_KEY + context.game().getCurrentAgendaInfo())
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong)
                .orElse(0L);
    }

    private static List<AiPrompt> outcomePrompts(List<AiPrompt> turn, String faction, long since) {
        return turn.stream()
                .filter(prompt -> !prompt.isHidden() && prompt.createdAtMillis() >= since)
                .filter(prompt -> prompt.firstEnabled(button -> ownedOrOpen(button, faction)
                                && (button.handlerId().startsWith(OUTCOME_PREFIX)
                                        || button.handlerId().startsWith(PLANET_OUTCOMES_PREFIX)))
                        .isPresent())
                .sorted(Comparator.comparingLong(AiPrompt::createdAtMillis))
                .toList();
    }

    private static Optional<AiDecision> chooseOutcome(AiTurnContext context, List<AiPrompt> turn, Ballot ballot) {
        String faction = context.faction();
        for (AiPrompt prompt : outcomePrompts(turn, faction, votedAt(context) - CLOCK_SKEW_MILLIS)) {
            Optional<PromptButton> outcome = prompt.firstEnabled(button -> ownedOrOpen(button, faction)
                    && button.handlerId().equalsIgnoreCase(OUTCOME_PREFIX + ballot.outcome()));
            if (outcome.isPresent()) {
                return Optional.of(AiDecision.press(prompt, outcome.get(), "vote for " + ballot.outcome()));
            }
            if (!ballot.planetOwner().isBlank()) {
                Optional<PromptButton> owner = prompt.firstEnabled(button -> ownedOrOpen(button, faction)
                        && button.handlerId().equalsIgnoreCase(PLANET_OUTCOMES_PREFIX + ballot.planetOwner()));
                if (owner.isPresent()) {
                    return Optional.of(
                            AiDecision.press(prompt, owner.get(), "look at " + ballot.planetOwner() + "'s planets"));
                }
            }
            Optional<PromptButton> anyOutcome = prompt.firstEnabled(button -> ownedOrOpen(button, faction)
                    && (button.handlerId().startsWith(OUTCOME_PREFIX)
                            || button.handlerId().startsWith(PLANET_OUTCOMES_PREFIX)));
            if (anyOutcome.isPresent()) {
                context.memory()
                        .put(
                                BALLOT_KEY + context.game().getCurrentAgendaInfo(),
                                new Ballot(ballot.outcome(), ballot.planetOwner(), 0, false).encode());
                return Optional.of(
                        AiDecision.press(prompt, anyOutcome.get(), "its outcome is not offered, so cast no votes"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> spendPlanets(AiTurnContext context, List<AiPrompt> turn, Ballot ballot) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Match> finish = Prompts.owned(turn, context.faction(), "proceedToFinalizingVote"::equals);
        if (finish.isEmpty()) return Optional.empty();
        AiPrompt prompt = finish.get().prompt();
        boolean expert = AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE);
        boolean wantsPredictive = expert ? ballot.predictive() : ballot.votes() > 0;
        int spent = seat.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith(SPENT_PLANET_PREFIX))
                .map(thing -> StringUtils.removeStart(thing, SPENT_PLANET_PREFIX))
                .filter(seat.getPlanets()::contains)
                .mapToInt(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(seat, game, planet))
                .sum();
        boolean predictiveSpent = seat.getSpentThingsThisWindow().contains(PREDICTIVE_VOTES);
        if (wantsPredictive && !predictiveSpent && castsAPlanetVote(game, seat, prompt, spent)) {
            Optional<PromptButton> predictive =
                    prompt.firstEnabled(button -> button.isUnowned() && PREDICTIVE_BUTTON.equals(button.handlerId()));
            if (predictive.isPresent()) {
                return Optional.of(
                        AiDecision.press(prompt, predictive.get(), "cast 3 votes with Predictive Intelligence"));
            }
        }
        boolean countsPredictive = (expert || !AgendaRound.isFinalAgenda(game)) && predictiveSpent;
        int predictive = countsPredictive ? PREDICTIVE_VOTE_COUNT : 0;
        int needed = Math.max(ballot.votes() - spent - predictive, predictiveSpent && spent == 0 ? 1 : 0);
        if (needed > 0) {
            Optional<PromptButton> planet = planetToExhaust(game, seat, prompt, needed);
            if (planet.isPresent())
                return Optional.of(AiDecision.press(prompt, planet.get(), "exhaust a planet for votes"));
        }
        Optional<AiDecision> tradeGoods = spendTradeGoods(context, prompt, ballot, spent);
        if (tradeGoods.isPresent()) return tradeGoods;
        return Optional.of(finish.get().press("finish casting its votes"));
    }

    private static boolean castsAPlanetVote(Game game, Player seat, AiPrompt prompt, int spent) {
        return spent > 0 || planetToExhaust(game, seat, prompt, 1).isPresent();
    }

    private static Optional<PromptButton> planetToExhaust(Game game, Player seat, AiPrompt prompt, int needed) {
        List<PromptButton> planets = prompt.enabledButtons().stream()
                .filter(button -> button.isUnowned() && button.handlerId().startsWith(PLANET_VOTE_PREFIX))
                .filter(button -> planetVotes(game, seat, button) > 0)
                .filter(button -> !seat.getSpentThingsThisWindow()
                        .contains(
                                SPENT_PLANET_PREFIX + StringUtils.removeStart(button.handlerId(), PLANET_VOTE_PREFIX)))
                .toList();
        if (AgendaRound.keepsAVoteBack(game) && planets.size() <= 1) return Optional.empty();
        Comparator<PromptButton> byVotes = Comparator.comparingInt(button -> planetVotes(game, seat, button));
        return planets.stream()
                .filter(button -> planetVotes(game, seat, button) >= needed)
                .min(byVotes)
                .or(() -> planets.stream().max(byVotes));
    }

    private static int planetVotes(Game game, Player seat, PromptButton button) {
        return AgendaHelper.getSpecificPlanetsVoteWorth(
                seat, game, StringUtils.removeStart(button.handlerId(), PLANET_VOTE_PREFIX));
    }

    private static Optional<AiDecision> spendTradeGoods(
            AiTurnContext context, AiPrompt prompt, Ballot ballot, int planetVotes) {
        Optional<PromptButton> spend =
                prompt.firstEnabled(button -> button.isUnowned() && GilaVotes.BUTTON.equals(button.handlerId()));
        if (spend.isEmpty() || ballot.votes() <= 0) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        String key = GILA_KEY + game.getRound() + "|" + game.getCurrentAgendaInfo();
        int planned = context.memory()
                .get(key)
                .filter(StringUtils::isNumeric)
                .map(Integer::parseInt)
                .orElseGet(() -> {
                    int tradeGoods = GilaVotes.tradeGoodsToSpend(game, seat, ballot.outcome(), planetVotes);
                    context.memory().put(key, String.valueOf(tradeGoods));
                    return tradeGoods;
                });
        if (seat.getSpentTgsThisWindow() >= planned || seat.getTg() < 1) return Optional.empty();
        return Optional.of(
                AiDecision.press(prompt, spend.get(), "spend a trade good for 2 votes with Gila the Silvertongue"));
    }

    public static AgendaForecast forecastWithItsBallot(Game game, Player seat) {
        AgendaForecast forecast = AgendaForecast.of(game, seat);
        if (forecast.isEmpty()) return forecast;
        return plan(game, seat)
                .map(ballot -> forecast.withVotes(seat, ballot.outcome(), ballot.votes()))
                .orElseGet(() -> forecast.without(seat));
    }

    static Optional<Ballot> plan(Game game, Player seat) {
        try (Thinking.Span timing = Thinking.measure("agenda.ballot")) {
            if (cannotVote(game, seat) || sitsOutThisAgenda(game, seat)) return Optional.empty();
            String agenda = AgendaPolicy.currentAgenda(game);
            AgendaModel model = agenda.isBlank() ? null : Mapper.getAgenda(agenda);
            if (model == null || model.getTarget() == null) return Optional.empty();
            List<Integer> planetVotes = readiedPlanetVotes(game, seat);
            Optional<Ballot> promised = PromisedVotes.binding(game, seat)
                    .filter(promise -> castable(game, planetVotes) > 0)
                    .flatMap(promise ->
                            promisedBallot(game, seat, promise.target(), promisedVotes(promise, planetVotes, game)));
            if (promised.isPresent()) return promised;
            return BallotPlanner.choose(game, seat, agenda, planetVotes)
                    .flatMap(choice -> ballotFor(game, choice.outcome(), choice.votes(), choice.predictive()));
        }
    }

    private static int promisedVotes(Promise promise, List<Integer> planetVotes, Game game) {
        return PromisedVotes.promisedVotes(promise, castable(game, planetVotes));
    }

    private static Optional<Ballot> promisedBallot(Game game, Player seat, String outcome, int votes) {
        return ballotFor(game, outcome, votes, plansPredictive(seat) && votes > BallotPlanner.PREDICTIVE_VOTES);
    }

    static boolean plansPredictive(Player seat) {
        return predictiveReady(seat) && AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE);
    }

    static boolean predictiveReady(Player seat) {
        return seat.hasTechReady(PREDICTIVE_INTELLIGENCE);
    }

    public static Optional<String> ballotInProgress(AiTurnContext context) {
        return context.memory()
                .get(BALLOT_KEY + context.game().getCurrentAgendaInfo())
                .flatMap(Ballot::decode)
                .map(Ballot::outcome);
    }

    public static int castableVotes(Game game, Player seat) {
        if (cannotVote(game, seat) || sitsOutThisAgenda(game, seat)) return 0;
        return castable(game, readiedPlanetVotes(game, seat));
    }

    private static int castable(Game game, List<Integer> planetVotes) {
        int available = planetVotes.stream().mapToInt(Integer::intValue).sum();
        return AgendaRound.keepsAVoteBack(game) ? available - BallotPlanner.reserve(planetVotes) : available;
    }

    static List<Integer> readiedPlanetVotes(Game game, Player seat) {
        return seat.getReadiedPlanets().stream()
                .map(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(seat, game, planet))
                .toList();
    }

    private static Optional<Ballot> ballotFor(Game game, String outcome, int votes, boolean predictive) {
        String owner = "";
        Planet planet = game.getPlanetsInfo().get(outcome);
        if (planet != null) {
            Player planetOwner = BoardView.controller(game, outcome);
            if (planetOwner == null) return Optional.empty();
            owner = planetOwner.getFaction();
        }
        return Optional.of(new Ballot(outcome, owner, votes, predictive));
    }

    private static boolean ownedOrOpen(PromptButton button, String faction) {
        return button.isUnowned() || button.isOwnedBy(faction);
    }
}
