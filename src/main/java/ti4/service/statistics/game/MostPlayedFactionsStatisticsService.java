package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class MostPlayedFactionsStatisticsService {

    static void showMostPlayedFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionCount = new HashMap<>();
        Map<String, Integer> custodians = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event), game -> calculate(game, factionCount, custodians));

        StringBuilder sb = new StringBuilder();
        sb.append("Plays per Faction:").append("\n");
        factionCount.entrySet().stream()
                .filter(entry -> Mapper.isValidFaction(entry.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
                .forEach(entry -> sb.append("`")
                        .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                        .append("x` ")
                        .append(entry.getKey().getFactionEmoji())
                        .append(" ")
                        .append(entry.getKey().getFactionNameWithSourceEmoji())
                        .append(" (Took Custodians a total of  ")
                        .append(custodians.getOrDefault(entry.getKey().getAlias(), 0))
                        .append(" times, or ")
                        .append((float) custodians.getOrDefault(entry.getKey().getAlias(), 0) / entry.getValue())
                        .append(")")
                        .append("\n"));
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Plays per Faction", sb.toString());
    }

    private static void calculate(Game game, Map<String, Integer> factionCount, Map<String, Integer> custodians) {
        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            String faction = player.getFaction();
            factionCount.put(faction, 1 + factionCount.getOrDefault(faction, 0));
            if (game.getCustodiansTaker() != null && game.getCustodiansTaker().equalsIgnoreCase(faction)) {
                if (custodians.containsKey(faction)) {
                    custodians.put(faction, custodians.get(faction) + 1);
                } else {
                    custodians.put(faction, 1);
                }
            }
        }
    }
}
