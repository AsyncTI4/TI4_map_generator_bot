package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@UtilityClass
public class FactionRecordOfStrategyCardPickService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> getFactionStrategyCardPickRecord(event));
    }

    private void getFactionStrategyCardPickRecord(SlashCommandInteractionEvent event) {
        StringBuilder text = new StringBuilder();
        for (int x = 1; x < 7; x++) {
            text.append(getSCPick(event, x));
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Strategy Card Pick Record", text.toString());
    }

    private String getSCPick(SlashCommandInteractionEvent event, int round) {
        String faction = event.getOption(Constants.FACTION, "", OptionMapping::getAsString);
        FactionModel factionM = Mapper.getFaction(faction);
        if (factionM == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No faction known as " + faction);
            return "UNKNOWN FACTION";
        }
        Map<String, Integer> scsPicked = new HashMap<>();
        Map<String, Integer> custodians = new HashMap<>();
        AtomicInteger gamesThatHadThem = new AtomicInteger();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> getScPick(game, round, faction, gamesThatHadThem, scsPicked, custodians));

        StringBuilder sb = new StringBuilder();

        sb.append("## __**SCs Picked By ")
                .append(factionM.getFactionName())
                .append(" In Round #")
                .append(round)
                .append(" (From ")
                .append(gamesThatHadThem)
                .append(" Games)**__\n");

        boolean sortOrderAscending = event.getOption("ascending", false, OptionMapping::getAsBoolean);
        Comparator<Map.Entry<String, Integer>> comparator = (o1, o2) -> {
            int o1total = o1.getValue();
            int o2total = o2.getValue();
            return sortOrderAscending ? Integer.compare(o1total, o2total) : -Integer.compare(o1total, o2total);
        };

        AtomicInteger index = new AtomicInteger(1);

        scsPicked.entrySet().stream().sorted(comparator).forEach(techResearched -> {
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index.get()), 3))
                    .append(". ");
            sb.append("` ").append(techResearched.getKey());
            sb.append(": ").append(techResearched.getValue());
            if (round == 1) {
                sb.append(" (Took Custodians a total of  ")
                        .append(custodians.getOrDefault(techResearched.getKey(), 0))
                        .append(" times)");
            }
            sb.append("\n");
            index.getAndIncrement();
        });

        return sb.toString();
    }

    private void getScPick(
            Game game,
            int round,
            String faction,
            AtomicInteger gamesThatHadThem,
            Map<String, Integer> scsPicked,
            Map<String, Integer> custodians) {
        for (Player player : game.getRealPlayers()) {
            String scs = game.getStoredValue("Round" + round + "SCPickFor" + faction);
            if (!player.getFaction().equalsIgnoreCase(faction) || scs.isEmpty()) {
                continue;
            }
            gamesThatHadThem.incrementAndGet();
            String[] scList = scs.split("_");
            for (String sc : scList) {
                sc = game.getStrategyCardModelByInitiative(Integer.parseInt(sc))
                        .get()
                        .getName();
                if (scsPicked.containsKey(sc)) {
                    scsPicked.put(sc, scsPicked.get(sc) + 1);
                } else {
                    scsPicked.put(sc, 1);
                }
                if (game.getCustodiansTaker() != null
                        && game.getCustodiansTaker().equalsIgnoreCase(faction)) {
                    if (custodians.containsKey(sc)) {
                        custodians.put(sc, custodians.get(sc) + 1);
                    } else {
                        custodians.put(sc, 1);
                    }
                }
            }
        }
    }
}
