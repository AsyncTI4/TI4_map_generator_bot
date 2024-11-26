package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

@UtilityClass
public class StellarConverterStatisticsService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(
            new StatisticsPipeline.StatisticsEvent("getStellarConverterStatistics", event, () -> getStellarConverterStatistics(event)));
    }

    private void getStellarConverterStatistics(SlashCommandInteractionEvent event) {
        String text = getStellarConverts();
        MessageHelper.sendMessageToThread(event.getChannel(), "Stellar Converter Record", text);
    }

    private String getStellarConverts() {
        Map<String, Integer> numberConverts = new HashMap<>();
        AtomicInteger count = new AtomicInteger();

        GamesPage.consumeAllGames(game -> getStellarConverterInfo(game, count, numberConverts));

        Comparator<Map.Entry<String, Integer>> comparator = (p1, p2) -> (-1) * p1.getValue().compareTo(p2.getValue());

        int index = 1;
        int width = (int) Math.round(Math.ceil(Math.log10(count.get() + 1)));
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        StringBuilder output = new StringBuilder("## **__Stellar Converter Stats__**\n");
        for (Map.Entry<String, Integer> planetStats : numberConverts.entrySet().stream().sorted(comparator).toList()) {
            String planetName = planetRepresentations.get(planetStats.getKey());
            output.append("`(").append(Helper.leftpad(String.valueOf(index), width)).append(")` ");
            output.append(planetName).append(": ").append(planetStats.getValue());
            output.append("\n");
            index++;
        }

        return output.toString();
    }

    private void getStellarConverterInfo(Game game, AtomicInteger count, Map<String, Integer> numberConverts) {
        List<String> worldsThisGame = game.getTileMap().values().stream()
            .flatMap(tile -> tile.getPlanetUnitHolders().stream()) //planets
            .filter(uh -> uh.getTokenList().contains(Constants.WORLD_DESTROYED_PNG))
            .map(UnitHolder::getName)
            .toList();

        if (worldsThisGame.size() != 1) {
            return;
        }
        count.incrementAndGet();
        String planet = worldsThisGame.getFirst();
        if (numberConverts.containsKey(planet)) {
            numberConverts.put(planet, numberConverts.get(planet) + 1);
        } else {
            numberConverts.put(planet, 1);
        }
    }
}
