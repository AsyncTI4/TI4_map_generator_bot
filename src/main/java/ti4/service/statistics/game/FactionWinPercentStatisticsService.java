package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@UtilityClass
class FactionWinPercentStatisticsService {

    public static void getFactionWinPercent(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        Map<String, Integer> factionWinsWithRelics = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getFactionWinPercent(game, factionWinCount, factionGameCount, factionWinsWithRelics));

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Win Percent:").append("\n");
        Mapper.getFactionsValues().stream()
                .map(faction -> {
                    double winCount = factionWinCount.getOrDefault(faction.getAlias(), 0);
                    double gameCount = factionGameCount.getOrDefault(faction.getAlias(), 0);
                    return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
                })
                .filter(entry -> factionGameCount.containsKey(entry.getKey().getAlias()))
                .sorted(Map.Entry.<FactionModel, Long>comparingByValue().reversed())
                .forEach(entry -> sb.append("`")
                        .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                        .append("%` (")
                        .append(factionGameCount.getOrDefault(entry.getKey().getAlias(), 0))
                        .append(" games) ")
                        .append(entry.getKey().getFactionEmoji())
                        .append(" ")
                        .append(entry.getKey().getFactionNameWithSourceEmoji())
                        .append("\n"));
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Faction Win Percent", sb.toString());

        StringBuilder sb2 = new StringBuilder();
        sb2.append("Winning Faction Relic Holding Percent:").append("\n");

        Mapper.getFactionsValues().stream()
                .map(faction -> {
                    double winCount = factionWinsWithRelics.getOrDefault(faction.getAlias(), 0);
                    double gameCount = factionWinCount.getOrDefault(faction.getAlias(), 0);
                    return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
                })
                .filter(entry -> factionGameCount.containsKey(entry.getKey().getAlias()))
                .sorted(Map.Entry.<FactionModel, Long>comparingByValue().reversed())
                .forEach(entry -> sb2.append("`")
                        .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                        .append("%` (")
                        .append(factionWinCount.getOrDefault(entry.getKey().getAlias(), 0))
                        .append(" games) ")
                        .append(entry.getKey().getFactionEmoji())
                        .append(" ")
                        .append(entry.getKey().getFactionNameWithSourceEmoji())
                        .append("\n"));

        sb2.append("All winners: ")
                .append(factionWinsWithRelics.getOrDefault("allWinners", 0))
                .append(" wins with relics out of ")
                .append(factionWinCount.getOrDefault("allWinners", 0))
                .append(" total wins");
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(),
                "Winning Faction Relic Holding Percent",
                sb2.toString());
    }

    private static void getFactionWinPercent(
            Game game,
            Map<String, Integer> factionWinCount,
            Map<String, Integer> factionGameCount,
            Map<String, Integer> factionWinsWithRelics) {
        List<Player> winners = game.getWinners();
        if (winners.isEmpty()) {
            return;
        }

        for (Player winner : winners) {
            String winningFaction = winner.getFaction();

            incrementMapValue(factionWinCount, winningFaction);
            incrementMapValue(factionWinCount, "allWinners");

            boolean emphidiaScored = hasEmphidiaScored(game, winner);
            if (usedRelicsOrEmphidia(winner, emphidiaScored)) {
                incrementMapValue(factionWinsWithRelics, winningFaction);
                incrementMapValue(factionWinsWithRelics, "allWinners");
            }
        }

        game.getRealAndEliminatedAndDummyPlayers()
                .forEach(player -> incrementMapValue(factionGameCount, player.getFaction()));
    }

    private static boolean hasEmphidiaScored(Game game, Player winner) {
        return game.getScoredPublicObjectives().entrySet().stream()
                .filter(entry -> entry.getValue().contains(winner.getUserID()))
                .map(Map.Entry::getKey)
                .anyMatch(poID -> poID.toLowerCase().contains("emphidia"));
    }

    private static boolean usedRelicsOrEmphidia(Player winner, boolean emphidiaScored) {
        return emphidiaScored
                || winner.getRelics().stream()
                        .anyMatch(relic -> "shard".equalsIgnoreCase(relic) || "obsidian".equalsIgnoreCase(relic));
    }

    private static void incrementMapValue(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }
}
