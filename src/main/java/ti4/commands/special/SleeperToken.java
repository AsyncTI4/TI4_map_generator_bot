package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

public class SleeperToken extends SpecialSubcommandData {

    public SleeperToken() {
        super(Constants.SLEEPER_TOKEN, "Select planets were to add/remove sleeper tokens");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "2nd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET3, "3rd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET4, "4th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET5, "5th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET6, "6th Planet").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        sleeperForPlanet(event, activeMap, Constants.PLANET);
        sleeperForPlanet(event, activeMap, Constants.PLANET2);
        sleeperForPlanet(event, activeMap, Constants.PLANET3);
        sleeperForPlanet(event, activeMap, Constants.PLANET4);
        sleeperForPlanet(event, activeMap, Constants.PLANET5);
        sleeperForPlanet(event, activeMap, Constants.PLANET6);
    }

    private void sleeperForPlanet(SlashCommandInteractionEvent event, Map activeMap, String planet) {
        OptionMapping planetOption = event.getOption(planet);
        if (planetOption == null){
            return;
        }
        String planetName = planetOption.getAsString();
        if (!activeMap.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }
        Tile tile = null;
        UnitHolder unitHolder = null;
        for (Tile tile_ : activeMap.getTileMap().values()) {
            if (tile != null) {
                break;
            }
            for (java.util.Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    tile = tile_;
                    unitHolder = unitHolderEntry.getValue();
                    break;
                }
            }
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet");
            return;
        }

        if (unitHolder.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)){
            tile.removeToken(Constants.TOKEN_SLEEPER_PNG, unitHolder.getName());
        } else {
            tile.addToken(Constants.TOKEN_SLEEPER_PNG, unitHolder.getName());
        }
    }
}
