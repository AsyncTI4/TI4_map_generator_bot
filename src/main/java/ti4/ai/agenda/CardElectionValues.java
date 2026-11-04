package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.lawRounds;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.relativeToRivals;

import java.util.Objects;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.DrawnCardValue;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class CardElectionValues {

    private static final double FREED_SLOT_POINTS = 0.25;
    private static final double ONE_STEP_SHORT_CHANCE = 0.5;
    private static final double UNMODELLED_CHANCE = 0.25;
    private static final double LATER_CHANCE_PER_ROUND = 0.1;
    private static final double MAX_LATER_CHANCE = 0.5;
    private static final double DIPLOMACY_SECONDARY = 1.0;
    private static final double CONSTRUCTION_SECONDARY = 2.0;
    private static final double WARFARE_SECONDARY = 2.0;
    private static final double TECHNOLOGY_SECONDARY_COST = 1.0;
    private static final int LEADERSHIP_TOKEN_CAP = 3;
    private static final int LEADERSHIP_INFLUENCE_PER_TOKEN = 3;
    private static final int POLITICS_CARDS = 2;
    private static final int LEADERSHIP = 1;
    private static final int DIPLOMACY = 2;
    private static final int POLITICS = 3;
    private static final int CONSTRUCTION = 4;
    private static final int TRADE = 5;
    private static final int WARFARE = 6;
    private static final int TECHNOLOGY = 7;
    private static final int IMPERIAL = 8;

    static double classifiedDocumentLeaks(Game game, Player voter, String outcome) {
        Player owner = secretOwner(game, outcome);
        if (owner == null) return 0;
        double value = leakedPoints(game, voter, owner, outcome) * ScoringStakes.scoreValue(game, voter, 1);
        for (Player rival : liveRivals(game, voter)) {
            value -= leakedPoints(game, rival, owner, outcome) * ScoringStakes.rivalStake(game, rival, 1);
        }
        return value;
    }

    private static double leakedPoints(Game game, Player player, Player owner, String secret) {
        if (player == owner) return FREED_SLOT_POINTS;
        return publicScoreChance(game, player, secret);
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

    static double galacticCrisisPact(Game game, Player voter, String outcome) {
        if (!StringUtils.isNumeric(outcome)) return 0;
        int card = Integer.parseInt(outcome);
        return relativeToRivals(game, voter, player -> freeSecondary(game, player, card));
    }

    private static double freeSecondary(Game game, Player player, int card) {
        return switch (card) {
            case LEADERSHIP -> leadershipSecondary(game, player);
            case DIPLOMACY -> DIPLOMACY_SECONDARY;
            case POLITICS -> DrawnCardValue.of(game, player, POLITICS_CARDS);
            case CONSTRUCTION -> CONSTRUCTION_SECONDARY;
            case TRADE -> replenishValue(player);
            case WARFARE -> WARFARE_SECONDARY;
            case TECHNOLOGY -> Math.max(0, ResearchPolicy.freeResearchWorth(game, player) - TECHNOLOGY_SECONDARY_COST);
            case IMPERIAL -> SecretDrawValue.of(game, player);
            default -> 0;
        };
    }

    private static double leadershipSecondary(Game game, Player player) {
        int tokens = Math.min(LEADERSHIP_TOKEN_CAP, readyInfluence(game, player) / LEADERSHIP_INFLUENCE_PER_TOKEN);
        return tokens > 0 ? CommandTokenValue.tactic(game, player, tokens) : 0;
    }

    private static int readyInfluence(Game game, Player player) {
        return player.getReadiedPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(Objects::nonNull)
                .mapToInt(Planet::getInfluence)
                .sum();
    }

    private static double replenishValue(Player player) {
        return Math.max(0, player.getCommoditiesTotal() - player.getCommodities()) * ValueScale.COMMODITY;
    }
}
