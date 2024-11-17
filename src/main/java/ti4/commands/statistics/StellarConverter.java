package ti4.commands.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.Subcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

class StellarConverter extends Subcommand {

    public StellarConverter() {
        super(Constants.STELLAR_CONVERTER, "Number of times each planet has been converted");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getStellarConverts(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Stellar Converter Record", text);
    }

    private String getStellarConverts(SlashCommandInteractionEvent event) {
        Map<String, Game> games = GameManager.getGameNameToGame();
        Map<String, Integer> numberConverts = new HashMap<>();

        int count = 0;
        for (Game g : games.values()) {
            List<String> worldsThisGame = g.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream()) //planets
                .filter(uh -> uh.getTokenList().contains(Constants.WORLD_DESTROYED_PNG))
                .map(UnitHolder::getName)
                .toList();

            if (worldsThisGame.size() == 1) {
                count++;
                String planet = worldsThisGame.getFirst();
                if (numberConverts.containsKey(planet)) {
                    numberConverts.put(planet, numberConverts.get(planet) + 1);
                } else {
                    numberConverts.put(planet, 1);
                }
            }
        }

        Comparator<Entry<String, Integer>> comparator = (p1, p2) -> (-1) * p1.getValue().compareTo(p2.getValue());

        int index = 1;
        int width = (int) Math.round(Math.ceil(Math.log10(count + 1)));
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        StringBuilder output = new StringBuilder("## **__Stellar Converter Stats__**\n");
        for (Entry<String, Integer> planetStats : numberConverts.entrySet().stream().sorted(comparator).toList()) {
            String planetName = planetRepresentations.get(planetStats.getKey());
            output.append("`(").append(Helper.leftpad(String.valueOf(index), width)).append(")` ");
            output.append(planetName).append(": ").append(planetStats.getValue());
            output.append("\n");
            index++;
        }

        return output.toString();
    }
}
