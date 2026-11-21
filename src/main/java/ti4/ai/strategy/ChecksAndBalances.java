package ti4.ai.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ChecksAndBalances {

    public static final String GIVE_PREFIX = "checksNBalancesPt2_";
    private static final double TRADE_GOOD_WEIGHT = 0.5;

    public record Plan(PromptButton card, String recipientFaction, String reason) {}

    public static boolean inPlay(Game game) {
        return game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks");
    }

    public static Optional<Plan> plan(Game game, Player picker, List<PromptButton> pickButtons) {
        List<Player> recipients = eligibleRecipients(game, picker);
        if (recipients.isEmpty() || pickButtons.isEmpty()) return Optional.empty();
        Player rival = strongestAmong(recipients);
        if (rival != picker && isAheadOf(game, rival, picker)) {
            return pickButtons.stream()
                    .min(Comparator.comparingDouble(
                            card -> recipientValue(game, rival, card) - pickerTradeGoods(game, card)))
                    .map(card -> new Plan(
                            card,
                            rival.getFaction(),
                            "give the leader a card it can't use well (Checks and Balances)"));
        }
        Player trailer = recipients.stream()
                .min(Comparator.comparingInt(Player::getTotalVictoryPoints)
                        .thenComparingInt(player -> player.getPlanets().size()))
                .orElseThrow();
        return pickButtons.stream()
                .max(Comparator.comparingDouble(
                        card -> recipientValue(game, trailer, card) + pickerTradeGoods(game, card)))
                .map(card -> new Plan(
                        card,
                        trailer.getFaction(),
                        "give the player furthest behind a good card (Checks and Balances)"));
    }

    public static Optional<PromptButton> recipient(
            Game game, Player picker, @Nullable String plannedFaction, List<PromptButton> giveButtons) {
        if (plannedFaction != null) {
            Optional<PromptButton> planned = giveButtons.stream()
                    .filter(button -> plannedFaction.equals(factionOf(button)))
                    .findFirst();
            if (planned.isPresent()) return planned;
        }
        Optional<PromptButton> toStrongest = giveButtons.stream()
                .filter(button -> game.getPlayerFromColorOrFaction(factionOf(button)) != null)
                .max(Comparator.comparingInt(button -> victoryPointsOf(game, button)));
        if (toStrongest.isPresent()) {
            Player strongest = game.getPlayerFromColorOrFaction(factionOf(toStrongest.get()));
            if (strongest != picker && isAheadOf(game, strongest, picker)) return toStrongest;
        }
        return giveButtons.stream().min(Comparator.comparingInt(button -> victoryPointsOf(game, button)));
    }

    static List<Player> eligibleRecipients(Game game, Player picker) {
        int perPlayer = Math.max(1, game.getStrategyCardsPerPlayer());
        List<Player> others = game.getRealPlayers().stream()
                .filter(player -> player != picker)
                .filter(player -> player.getSCs().size() < perPlayer)
                .toList();
        return others.isEmpty() && picker.getSCs().size() < perPlayer ? List.of(picker) : others;
    }

    private static Player strongestAmong(List<Player> players) {
        return players.stream()
                .max(Comparator.comparingInt(Player::getTotalVictoryPoints)
                        .thenComparingInt(player -> player.getPlanets().size()))
                .orElseThrow();
    }

    private static boolean isAheadOf(Game game, Player player, Player picker) {
        int points = player.getTotalVictoryPoints();
        int leaderPoints = game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(points);
        return points > picker.getTotalVictoryPoints() || points == leaderPoints;
    }

    private static double recipientValue(Game game, Player recipient, PromptButton card) {
        return StrategyCardRanking.baseValue(game, recipient, StrategyCardRanking.initiative(card));
    }

    private static double pickerTradeGoods(Game game, PromptButton card) {
        return TRADE_GOOD_WEIGHT * game.getScTradeGoods().getOrDefault(StrategyCardRanking.initiative(card), 0);
    }

    private static String factionOf(PromptButton giveButton) {
        return StringUtils.substringAfterLast(giveButton.handlerId(), "_");
    }

    private static int victoryPointsOf(Game game, PromptButton giveButton) {
        Player player = game.getPlayerFromColorOrFaction(factionOf(giveButton));
        return player == null ? Integer.MAX_VALUE : player.getTotalVictoryPoints();
    }
}
