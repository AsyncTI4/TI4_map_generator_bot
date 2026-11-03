package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.StrategyCardModel;

@UtilityClass
public class FactionRecordOfStrategyCardPickService {

    private static final int FIRST_ROUND = 1;
    private static final int LAST_ROUND = 6;

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> getFactionStrategyCardPickRecord(event));
    }

    private void getFactionStrategyCardPickRecord(SlashCommandInteractionEvent event) {
        String faction = event.getOption(Constants.FACTION, "", OptionMapping::getAsString);
        FactionModel factionM = Mapper.getFaction(faction);
        if (factionM == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No faction known as " + faction);
            return;
        }

        Map<Integer, RoundRecord> recordsByRound = new HashMap<>();
        for (int round = FIRST_ROUND; round <= LAST_ROUND; round++) {
            recordsByRound.put(round, new RoundRecord());
        }

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> recordScPicks(game, faction, recordsByRound),
                ExecutionLockType.READ);

        boolean sortOrderAscending = event.getOption("ascending", Boolean.FALSE, OptionMapping::getAsBoolean);
        StringBuilder text = new StringBuilder();
        for (int round = FIRST_ROUND; round <= LAST_ROUND; round++) {
            text.append(formatRound(factionM, round, recordsByRound.get(round), sortOrderAscending));
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Strategy Card Pick Record", text.toString());
    }

    private String formatRound(FactionModel factionM, int round, RoundRecord record, boolean sortOrderAscending) {
        StringBuilder sb = new StringBuilder();

        sb.append("## __**SCs Picked By ")
                .append(factionM.getFactionName())
                .append(" In Round #")
                .append(round)
                .append(" (From ")
                .append(record.gamesThatHadThem)
                .append(" Games)**__\n");

        Comparator<Map.Entry<String, Integer>> comparator = (o1, o2) -> {
            int o1total = o1.getValue();
            int o2total = o2.getValue();
            return sortOrderAscending ? Integer.compare(o1total, o2total) : -Integer.compare(o1total, o2total);
        };

        AtomicInteger index = new AtomicInteger(1);

        record.scsPicked.entrySet().stream().sorted(comparator).forEach(scPicked -> {
            sb.append('`')
                    .append(Helper.leftpad(String.valueOf(index.get()), 3))
                    .append(". ");
            sb.append("` ").append(scPicked.getKey());
            sb.append(": ").append(scPicked.getValue());
            if (round == FIRST_ROUND) {
                sb.append(" (Took Custodians a total of  ")
                        .append(record.custodians.getOrDefault(scPicked.getKey(), 0))
                        .append(" times)");
            }
            sb.append('\n');
            index.getAndIncrement();
        });

        return sb.toString();
    }

    private void recordScPicks(Game game, String faction, Map<Integer, RoundRecord> recordsByRound) {
        boolean factionInGame = game.getRealPlayers().stream()
                .anyMatch(player -> player.getFaction().equalsIgnoreCase(faction));
        if (!factionInGame) {
            return;
        }
        boolean factionTookCustodians = faction.equalsIgnoreCase(game.getCustodiansTaker());
        for (int round = FIRST_ROUND; round <= LAST_ROUND; round++) {
            String scs = game.getStoredValue("Round" + round + "SCPickFor" + faction);
            if (scs.isEmpty()) {
                continue;
            }
            recordsByRound.get(round).add(game, scs, factionTookCustodians);
        }
    }

    private static class RoundRecord {
        private int gamesThatHadThem;
        private final Map<String, Integer> scsPicked = new HashMap<>();
        private final Map<String, Integer> custodians = new HashMap<>();

        private void add(Game game, String scs, boolean factionTookCustodians) {
            gamesThatHadThem++;
            for (String sc : scs.split("_")) {
                if (!Helper.isInteger(sc)) {
                    continue;
                }
                String scName = game.getStrategyCardModelByInitiative(Integer.parseInt(sc))
                        .map(StrategyCardModel::getName)
                        .orElse(null);
                if (scName == null) {
                    continue;
                }
                scsPicked.merge(scName, 1, Integer::sum);
                if (factionTookCustodians) {
                    custodians.merge(scName, 1, Integer::sum);
                }
            }
        }
    }
}
