package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class FactionCustodianPercentStatisticsService {

    static void getFactionCustodianPercent(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionCustodianCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getFactionCustodianPercent(game, factionCustodianCount, factionGameCount),
                ExecutionLockType.READ);

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Custodian Percent:").append('\n');
        factionGameCount.keySet().stream()
                .map(faction -> {
                    double custodianCount = factionCustodianCount.getOrDefault(faction, 0);
                    double gameCount = factionGameCount.getOrDefault(faction, 0);
                    return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * custodianCount / gameCount));
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
                (MessageChannelUnion) event.getMessageChannel(), "Faction Custodian Percent", sb.toString());
    }

    private static void getFactionCustodianPercent(
            Game game, Map<String, Integer> factionCustodianCount, Map<String, Integer> factionGameCount) {
        Player custodianTaker = game.getCustodiansTakerPlayer();
        if (custodianTaker == null) {
            return;
        }

        String winningFaction = custodianTaker.getFaction();
        FactionStatisticsHelper.incrementFactionsIntValue(factionCustodianCount, winningFaction);
        FactionStatisticsHelper.incrementFactionsIntValue(factionCustodianCount, "allCustodians");

        game.getRealAndEliminatedAndDummyPlayers()
                .forEach(player ->
                        FactionStatisticsHelper.incrementFactionsIntValue(factionGameCount, player.getFaction()));
    }
}
