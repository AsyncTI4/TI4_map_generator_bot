package ti4.service.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@UtilityClass
public class GameWinsWithOtherFactionsService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> getGameWinsWithOtherFactions(event));
    }

    private void getGameWinsWithOtherFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        List<String> reqFactions = new ArrayList<>();
        reqFactions.add(event.getOption("faction").getAsString());
        for (int x = 2; x < 7; x++) {
            OptionMapping option = event.getOption("faction" + x);
            if (option != null) {
                reqFactions.add(option.getAsString());
            }
        }

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getGameWinsWithOtherFactions(game, factionWinCount, factionGameCount, reqFactions));

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Win Percent:").append("\n");

        factionGameCount.keySet().stream()
                .map(faction -> {
                    double winCount = factionWinCount.getOrDefault(faction, 0);
                    double gameCount = factionGameCount.getOrDefault(faction, 0);
                    return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
                })
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    FactionModel factionModel = Mapper.getFaction(entry.getKey());
                    String factionEmoji = factionModel != null ? factionModel.getFactionEmoji() : "\uD83D\uDC7B";
                    String factionName =
                            factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : entry.getKey();
                    sb.append("`")
                            .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                            .append("%` (")
                            .append(factionGameCount.getOrDefault(entry.getKey(), 0))
                            .append(" games) ")
                            .append(factionEmoji)
                            .append(" ")
                            .append(factionName)
                            .append("\n");
                });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Faction Win Percent", sb.toString());
    }

    private void getGameWinsWithOtherFactions(
            Game game,
            Map<String, Integer> factionWinCount,
            Map<String, Integer> factionGameCount,
            List<String> reqFactions) {
        List<Player> winners = game.getWinners();
        if (winners.isEmpty()) {
            return;
        }
        boolean count = true;
        List<String> factions = new ArrayList<>();
        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            factions.add(player.getFaction());
        }
        for (String faction : reqFactions) {
            if (!factions.contains(faction)) {
                count = false;
                break;
            }
        }
        if (!count) {
            return;
        }

        for (Player player : winners) {
            String winningFaction = player.getFaction();
            FactionStatisticsHelper.incrementFactionsIntValue(factionWinCount, winningFaction);
        }

        game.getRealAndEliminatedAndDummyPlayers().forEach(player -> {
            String faction = player.getFaction();
            FactionStatisticsHelper.incrementFactionsIntValue(factionGameCount, faction);
        });
    }
}
