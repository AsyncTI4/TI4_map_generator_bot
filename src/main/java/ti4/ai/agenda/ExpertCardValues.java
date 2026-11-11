package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.livePlayers;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.DrawnCardValue;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretPhase;
import ti4.ai.secrets.SecretValue;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class ExpertCardValues {

    static final double OWN_SECRET_LEAKED = 3.0;
    static final double HARD_SECRETS_BONUS = 0.5;
    static final double ACTION_CONTENDER_BONUS = 0.5;
    static final double EASY_SECRET_KEEP = 1.0;
    static final int FAR_BEHIND_POINTS = 3;
    static final double VOTE_SHARE = 0.5;
    static final int TECHNOLOGY_RESOURCES = 4;
    static final int FULL_PRODUCTION_RESOURCES = 4;
    static final double CONSTRUCTION_SECONDARY = 2.0;
    static final double WARFARE_SECONDARY = 2.0;
    static final double RIVAL_SECRET_DRAW_POINTS = 0.4;
    private static final double ONE_STEP_SHORT_CHANCE = 0.5;
    private static final double UNMODELLED_CHANCE = 0.25;
    private static final double LATER_CHANCE_PER_ROUND = 0.1;
    private static final double MAX_LATER_CHANCE = 0.5;
    private static final int LEADERSHIP_TOKEN_CAP = 3;
    private static final int LEADERSHIP_INFLUENCE_PER_TOKEN = 3;
    private static final int POLITICS_CARDS = 2;
    static final int LEADERSHIP = 1;
    static final int DIPLOMACY = 2;
    static final int POLITICS = 3;
    static final int CONSTRUCTION = 4;
    static final int TRADE = 5;
    static final int WARFARE = 6;
    static final int TECHNOLOGY = 7;
    static final int IMPERIAL = 8;

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        return switch (agenda) {
            case "classified" -> classifiedDocumentLeaks(view, outcome);
            case "crisis" -> galacticCrisisPact(view, outcome);
            default -> null;
        };
    }

    private static double classifiedDocumentLeaks(AgendaView view, String outcome) {
        Game game = view.game();
        Player owner = secretOwner(game, outcome);
        if (owner == null) return 0;
        return relativeToRivals(game, view.voter(), seat -> leakGain(view, seat, owner, outcome));
    }

    static double leakGain(AgendaView view, Player seat, Player owner, String secret) {
        if (seat == owner) return OWN_SECRET_LEAKED;
        Game game = view.game();
        return publicScoreChance(game, seat, secret)
                * ScoringStakes.scoreValue(game, seat, 1)
                * leakWeight(view, seat, secret);
    }

    static double leakWeight(AgendaView view, Player seat, String secret) {
        return switch (SecretPhase.of(secret)) {
            case STATUS -> 1 + HARD_SECRETS_BONUS * hardSecretsChance(view, seat);
            case ACTION -> isLeaderOrFarBehind(view.game(), seat) ? 1 + ACTION_CONTENDER_BONUS : 1;
            case AGENDA, UNKNOWN -> 1;
        };
    }

    static double hardSecretsChance(AgendaView view, Player seat) {
        if (view.readsHandOf(seat)) return hasOnlyHardSecrets(view.game(), seat) ? 1 : 0;
        int held = seat.getSo();
        if (held <= 0) return 0;
        return Math.pow(1 - SecretOdds.BASE_RATE, held);
    }

    private static boolean hasOnlyHardSecrets(Game game, Player seat) {
        List<String> unscored = List.copyOf(seat.getSecretsUnscored().keySet());
        if (unscored.isEmpty()) return false;
        return unscored.stream().noneMatch(secret -> SecretValue.keepValue(game, seat, secret) >= EASY_SECRET_KEEP);
    }

    private static boolean isLeaderOrFarBehind(Game game, Player seat) {
        int top = livePlayers(game).stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
        int points = seat.getTotalVictoryPoints();
        return points >= top || top - points >= FAR_BEHIND_POINTS;
    }

    @Nullable
    private static Player secretOwner(Game game, String secret) {
        return game.getRealPlayers().stream()
                .filter(player -> player.getSecretsScored().containsKey(secret))
                .findFirst()
                .orElse(null);
    }

    private static double publicScoreChance(Game game, Player player, String objective) {
        double ready = readiness(game, player, objective);
        double later = Math.min(MAX_LATER_CHANCE, lawRounds(game) * LATER_CHANCE_PER_ROUND);
        return ready + (1 - ready) * later;
    }

    private static double readiness(Game game, Player player, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return UNMODELLED_CHANCE;
        ObjectiveValue board = ObjectiveValue.publicOnly(game, player);
        int progress = board.progress(objective, board.before());
        if (progress == ObjectiveValue.NOT_MODELLED) return UNMODELLED_CHANCE;
        if (progress >= threshold) return 1;
        return progress + 1 >= threshold ? ONE_STEP_SHORT_CHANCE : 0;
    }

    private static double galacticCrisisPact(AgendaView view, String outcome) {
        if (!StringUtils.isNumeric(outcome)) return 0;
        int card = Integer.parseInt(outcome);
        return relativeToRivals(view.game(), view.voter(), seat -> secondaryGain(view, seat, card));
    }

    static double secondaryGain(AgendaView view, Player seat, int card) {
        Game game = view.game();
        return switch (card) {
            case LEADERSHIP -> leadershipSecondary(game, seat);
            case DIPLOMACY -> 0;
            case POLITICS -> politicsSecondary(view, seat);
            case CONSTRUCTION -> CONSTRUCTION_SECONDARY;
            case TRADE -> replenishValue(seat);
            case WARFARE -> warfareSecondary(game, seat);
            case TECHNOLOGY -> technologySecondary(game, seat);
            case IMPERIAL -> imperialSecondary(view, seat);
            default -> 0;
        };
    }

    private static double leadershipSecondary(Game game, Player seat) {
        int influence = planetsLeftAfterVoting(game, seat).stream()
                .mapToInt(Planet::getInfluence)
                .sum();
        int tokens = Math.min(LEADERSHIP_TOKEN_CAP, influence / LEADERSHIP_INFLUENCE_PER_TOKEN);
        return tokens > 0 ? CommandTokenValue.tactic(game, seat, tokens) : 0;
    }

    private static double warfareSecondary(Game game, Player seat) {
        int resources = resourcesLeftAfterVoting(game, seat);
        return WARFARE_SECONDARY * Math.min(1.0, (double) resources / FULL_PRODUCTION_RESOURCES);
    }

    private static double technologySecondary(Game game, Player seat) {
        if (resourcesLeftAfterVoting(game, seat) < TECHNOLOGY_RESOURCES) return 0;
        double cost = TECHNOLOGY_RESOURCES * ValueScale.RESOURCE;
        return Math.max(0, ResearchPolicy.freeResearchWorth(game, seat) - cost);
    }

    private static double politicsSecondary(AgendaView view, Player seat) {
        if (view.readsHandOf(seat)) return DrawnCardValue.of(view.game(), seat, POLITICS_CARDS);
        return POLITICS_CARDS * ActionCardPrior.AVERAGE_CARD;
    }

    private static double imperialSecondary(AgendaView view, Player seat) {
        Game game = view.game();
        if (view.readsHandOf(seat)) return SecretDrawValue.of(game, seat);
        boolean room = seat.getSo() < Math.max(0, seat.getMaxSOCount() - seat.getSoScored());
        if (!room) return SecretDrawValue.SWAP_VALUE;
        return RIVAL_SECRET_DRAW_POINTS * ScoringStakes.scoreValue(game, seat, 1);
    }

    private static double replenishValue(Player seat) {
        return Math.max(0, seat.getCommoditiesTotal() - seat.getCommodities()) * ValueScale.COMMODITY;
    }

    static int resourcesLeftAfterVoting(Game game, Player seat) {
        return planetsLeftAfterVoting(game, seat).stream()
                .mapToInt(Planet::getResources)
                .sum();
    }

    static List<Planet> planetsLeftAfterVoting(Game game, Player seat) {
        List<Planet> ready = seat.getReadiedPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Planet::getInfluence).reversed().thenComparing(Planet::getName))
                .toList();
        double voteShare =
                VOTE_SHARE * ready.stream().mapToInt(Planet::getInfluence).sum();
        int exhausted = 0;
        int spent = 0;
        while (exhausted < ready.size() && spent < voteShare) {
            spent += ready.get(exhausted).getInfluence();
            exhausted++;
        }
        return ready.subList(exhausted, ready.size());
    }
}
