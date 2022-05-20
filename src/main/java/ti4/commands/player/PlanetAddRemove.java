package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.List;
import java.util.Set;

public abstract class PlanetAddRemove extends PlayerSubcommandData{
    public PlanetAddRemove(String id, String description) {
        super(id, description);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "2dn Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET3, "3rd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET4, "4th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET5, "5th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET6, "6th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (activeMap.getPlayer(playerID) != null) {
                player = activeMap.getPlayers().get(playerID);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerOption.getAsUser().getName() + " could not be found in map:" + activeMap.getName());
                return;
            }
        }

        parseParameter(event, player, event.getOption(Constants.PLANET), activeMap);
        parseParameter(event, player, event.getOption(Constants.PLANET2), activeMap);
        parseParameter(event, player, event.getOption(Constants.PLANET3), activeMap);
        parseParameter(event, player, event.getOption(Constants.PLANET4), activeMap);
        parseParameter(event, player, event.getOption(Constants.PLANET5), activeMap);
        parseParameter(event, player, event.getOption(Constants.PLANET6), activeMap);
    }

    private void parseParameter(SlashCommandInteractionEvent event, Player player, OptionMapping option, Map map) {
        if (option != null) {
            String planetID = option.getAsString();
            if (Mapper.isValidPlanet(planetID)) {
                doAction(player, planetID, map);
            } else {
                Set<String> planets = map.getPlanets();
                List<String> possiblePlanets = planets.stream().filter(value -> value.toLowerCase().contains(planetID)).toList();
                if (possiblePlanets.isEmpty()){
                    MessageHelper.sendMessageToChannel(event.getChannel(), "No matching Planet found");
                    return;
                } else if (possiblePlanets.size() > 1){
                    MessageHelper.sendMessageToChannel(event.getChannel(), "More that one matching Planet found");
                    return;
                }
                doAction(player, possiblePlanets.get(0), map);
            }
        }
    }

    public abstract void doAction(Player player, String techID, Map map);
}
