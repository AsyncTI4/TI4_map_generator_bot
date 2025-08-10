package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
public class FactionRecordOfTechService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> getFactionRecordOfTech(event));
    }

    private void getFactionRecordOfTech(SlashCommandInteractionEvent event) {
        String text = getTechResearched(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Technology Acquisition Record", text);
    }

    private String getTechResearched(SlashCommandInteractionEvent event) {
        String faction = event.getOption(Constants.FACTION, "", OptionMapping::getAsString);
        FactionModel factionModel = Mapper.getFaction(faction);
        if (factionModel == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No faction known as " + faction);
            return "UNKNOWN FACTION";
        }
        Map<String, Integer> techsResearched = new HashMap<>();
        AtomicInteger gamesThatHadThem = new AtomicInteger();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> getFactionRecordOfTech(game, techsResearched, gamesThatHadThem, faction, factionModel));

        StringBuilder sb = new StringBuilder();

        sb.append("## __**Technologies Researched By ")
                .append(factionModel.getFactionName())
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

        techsResearched.entrySet().stream().sorted(comparator).forEach(techResearched -> {
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index.get()), 3))
                    .append(". ");
            sb.append("` ").append(techResearched.getKey());
            sb.append(": ").append(techResearched.getValue());
            sb.append("\n");
            index.getAndIncrement();
        });

        return sb.toString();
    }

    private void getFactionRecordOfTech(
            Game game,
            Map<String, Integer> techsResearched,
            AtomicInteger gamesThatHadThem,
            String faction,
            FactionModel factionModel) {
        for (Player player : game.getRealPlayers()) {
            if (!player.getFaction().equalsIgnoreCase(faction)) {
                continue;
            }
            gamesThatHadThem.getAndIncrement();
            for (String tech : player.getTechs()) {
                List<String> startingTech = new ArrayList<>();
                if (factionModel.getStartingTech() != null) {
                    startingTech = factionModel.getStartingTech();
                }
                if (startingTech != null && !startingTech.contains(tech)) {
                    String techName = Mapper.getTech(tech).getName();
                    if (techsResearched.containsKey(techName)) {
                        techsResearched.put(techName, techsResearched.get(techName) + 1);
                    } else {
                        techsResearched.put(techName, 1);
                    }
                }
            }
        }
    }
}
