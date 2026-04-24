package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GamesPage;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class FactionIxthPercentStatisticsService {

    static void getFactionIxthPercent(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionIxthCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getFactionIxthPercent(game, factionIxthCount, factionGameCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Ixth Percent:").append('\n');
        factionGameCount.keySet().stream()
                .map(faction -> {
                    double ixthCount = factionIxthCount.getOrDefault(faction, 0);
                    double gameCount = factionGameCount.getOrDefault(faction, 0);
                    return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * ixthCount / gameCount));
                })
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    FactionModel factionModel = Mapper.getFaction(entry.getKey());
                    String factionName =
                            factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : entry.getKey();
                    String factionEmoji = FactionStatisticsHelper.getFactionEmoji(entry.getKey());
                    sb.append('`')
                            .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                            .append("%` (")
                            .append(factionGameCount.get(entry.getKey()))
                            .append(" games) ")
                            .append(factionEmoji)
                            .append(' ')
                            .append(factionName)
                            .append('\n');
                });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Faction Ixth Percent", sb.toString());
    }

    private static void getFactionIxthPercent(
            Game game, Map<String, Integer> factionIxthCount, Map<String, Integer> factionGameCount) {
        Player ixthTaker = null;
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains("ixth")) {
                ixthTaker = player;
                break;
            }
        }
        if (ixthTaker == null) {
            return;
        }

        String winningFaction = ixthTaker.getFaction();
        FactionStatisticsHelper.incrementFactionsIntValue(factionIxthCount, winningFaction);
        FactionStatisticsHelper.incrementFactionsIntValue(factionIxthCount, "allIxth");

        game.getRealAndEliminatedAndDummyPlayers()
                .forEach(player ->
                        FactionStatisticsHelper.incrementFactionsIntValue(factionGameCount, player.getFaction()));
    }
}
