package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AgendaSummaryHelper;
import ti4.image.Mapper;
import ti4.model.AgendaModel;

@UtilityClass
public class AgendaVoting {

    private static final String BALLOT_KEY = "agendaBallot|";
    private static final String OUTCOME_PREFIX = "outcome_";
    private static final String PLANET_OUTCOMES_PREFIX = "planetOutcomes_";
    private static final String PLANET_VOTE_PREFIX = "exhaustForVotes_planet_";
    private static final String CONFIRM_PREFIX = "resolveAgendaVote_";
    private static final String SPENT_PLANET_PREFIX = "planet_";
    private static final String VOTED_AT_KEY = "agendaVotedAt|";
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final String MUTINY = "mutiny";
    private static final double ALL_IN_SCORE = 2;
    private static final String FIELD = "~";
    private static final String GALACTIC_THREAT = "galactic_threat";
    private static final String XXCHA_COMMANDER = "xxchacommander";

    record Ballot(String outcome, String planetOwner, int votes) {

        String encode() {
            return String.join(FIELD, outcome, planetOwner, String.valueOf(votes));
        }

        static Optional<Ballot> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 3 || !StringUtils.isNumeric(fields[2])) return Optional.empty();
            return Optional.of(new Ballot(fields[0], fields[1], Integer.parseInt(fields[2])));
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
        Optional<Ballot> ballot = context.memory().get(key).flatMap(Ballot::decode);
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

    private static boolean confirmsVotes(String handlerId) {
        String votes = StringUtils.removeStart(handlerId, CONFIRM_PREFIX);
        return handlerId.startsWith(CONFIRM_PREFIX) && StringUtils.isNumeric(votes) && !"0".equals(votes);
    }

    static boolean cannotVote(Game game, Player seat) {
        return seat.hasAbility(GALACTIC_THREAT) && !game.playerHasLeaderUnlockedOrAlliance(seat, XXCHA_COMMANDER);
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
                                new Ballot(ballot.outcome(), ballot.planetOwner(), 0).encode());
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
        int spent = seat.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith(SPENT_PLANET_PREFIX))
                .map(thing -> StringUtils.removeStart(thing, SPENT_PLANET_PREFIX))
                .filter(seat.getPlanets()::contains)
                .mapToInt(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(seat, game, planet))
                .sum();
        if (spent < ballot.votes()) {
            Optional<PromptButton> planet = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned() && button.handlerId().startsWith(PLANET_VOTE_PREFIX))
                    .max(Comparator.comparingInt(button -> AgendaHelper.getSpecificPlanetsVoteWorth(
                            seat, game, StringUtils.removeStart(button.handlerId(), PLANET_VOTE_PREFIX))));
            if (planet.isPresent())
                return Optional.of(AiDecision.press(prompt, planet.get(), "exhaust a planet for votes"));
        }
        return Optional.of(finish.get().press("finish casting its votes"));
    }

    static Optional<Ballot> plan(Game game, Player seat) {
        if (cannotVote(game, seat)) return Optional.empty();
        String agenda = AgendaPolicy.currentAgenda(game);
        AgendaModel model = agenda.isBlank() ? null : Mapper.getAgenda(agenda);
        if (model == null || model.getTarget() == null) return Optional.empty();
        int available = seat.getReadiedPlanets().stream()
                .mapToInt(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(seat, game, planet))
                .sum();
        if (available <= 0) return Optional.empty();
        String target = model.getTarget().toLowerCase();
        List<String> outcomes = new ArrayList<>();
        if (target.contains("for/against")) {
            outcomes.add("for");
            outcomes.add("against");
        } else if (target.contains("elect player")) {
            game.getRealPlayers().forEach(player -> outcomes.add(player.getFaction()));
        } else if (target.contains("planet")) {
            game.getRealPlayers()
                    .forEach(player -> player.getPlanets().stream()
                            .filter(planet -> electable(game, agenda, target, planet))
                            .forEach(outcomes::add));
        } else {
            return Optional.empty();
        }
        Optional<String> best = outcomes.stream()
                .filter(outcome -> score(game, seat, agenda, outcome, available) > 0)
                .max(Comparator.comparingDouble(outcome -> score(game, seat, agenda, outcome, available)));
        if (best.isEmpty()) return Optional.empty();
        double score = score(game, seat, agenda, best.get(), available);
        int votes = score >= ALL_IN_SCORE ? available : (available + 1) / 2;
        String owner = "";
        Planet planet = game.getPlanetsInfo().get(best.get());
        if (planet != null) {
            Player planetOwner = BoardView.controller(game, best.get());
            if (planetOwner == null) return Optional.empty();
            owner = planetOwner.getFaction();
        }
        return Optional.of(new Ballot(best.get(), owner, votes));
    }

    private static boolean electable(Game game, String agenda, String target, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || planet.isSpaceStation(game) || !AgendaPolicy.hasRequiredTrait(agenda, planet)) {
            return false;
        }
        for (String trait : List.of("cultural", "industrial", "hazardous")) {
            if (target.contains(trait) && !planet.getPlanetTypes().contains(trait)) return false;
        }
        if (target.contains("non-home")) {
            ti4.game.Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null || tile.isHomeSystem(game) || tile.isMecatol(game)) return false;
        }
        return true;
    }

    private static double score(Game game, Player seat, String agenda, String outcome, int available) {
        if (MUTINY.equals(agenda)) return mutiny(game, outcome, available);
        return AgendaPolicy.score(game, seat, agenda, outcome);
    }

    private static double mutiny(Game game, String outcome, int available) {
        if (!"for".equalsIgnoreCase(outcome)) return 0;
        Map<String, Integer> tally = AgendaSummaryHelper.getCurrentOutcomeVoteCounts(game);
        int forVotes = tally.getOrDefault("for", 0) + available;
        int againstVotes = tally.getOrDefault("against", 0);
        return forVotes > againstVotes ? ALL_IN_SCORE : 0;
    }

    private static boolean ownedOrOpen(PromptButton button, String faction) {
        return button.isUnowned() || button.isOwnedBy(faction);
    }
}
