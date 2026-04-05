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
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class MostPlayedFactionsStatisticsService {

    static void showMostPlayedFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionCount = new HashMap<>();
        Map<String, Integer> custodians = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event), game -> calculate(game, factionCount, custodians));

        StringBuilder sb = new StringBuilder();
        sb.append("Plays per Faction:").append('\n');
        factionCount.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
            FactionModel factionModel = Mapper.getFaction(entry.getKey());
            String factionName = factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : entry.getKey();
            String factionEmoji = FactionStatisticsHelper.getFactionEmoji(entry.getKey());
            sb.append('`')
                    .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                    .append("x` ")
                    .append(factionEmoji)
                    .append(" ")
                    .append(factionName)
                    .append(" (Took Custodians a total of  ")
                    .append(custodians.getOrDefault(entry.getKey(), 0))
                    .append(" times, or ")
                    .append((float) custodians.getOrDefault(entry.getKey(), 0) / entry.getValue())
                    .append(")")
                    .append('\n');
        });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Plays per Faction", sb.toString());
    }

    private static void calculate(Game game, Map<String, Integer> factionCount, Map<String, Integer> custodians) {
        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            String faction = player.getFaction();
            FactionStatisticsHelper.incrementFactionsIntValue(factionCount, faction);
            if (game.getCustodiansTaker() != null && game.getCustodiansTaker().equalsIgnoreCase(faction)) {
                FactionStatisticsHelper.incrementFactionsIntValue(custodians, faction);
            }
        }
    }
}
