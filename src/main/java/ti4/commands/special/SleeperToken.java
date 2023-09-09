package ti4.commands.special;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
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
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        sleeperForPlanet(event, activeGame, Constants.PLANET, player);
        sleeperForPlanet(event, activeGame, Constants.PLANET2, player);
        sleeperForPlanet(event, activeGame, Constants.PLANET3, player);
        sleeperForPlanet(event, activeGame, Constants.PLANET4, player);
        sleeperForPlanet(event, activeGame, Constants.PLANET5, player);
        sleeperForPlanet(event, activeGame, Constants.PLANET6, player);
    }

    private void sleeperForPlanet(SlashCommandInteractionEvent event, Game activeGame, String planet, Player player) {
        OptionMapping planetOption = event.getOption(planet);
        if (planetOption == null){
            return;
        }
        String planetName = planetOption.getAsString();
        addOrRemoveSleeper(event, activeGame, planetName, player);
    }
    public void addOrRemoveSleeper(GenericInteractionCreateEvent event, Game activeGame, String planetName, Player player) {
        if (!activeGame.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }
        Tile tile = null;
        UnitHolder unitHolder = null;
        for (Tile tile_ : activeGame.getTileMap().values()) {
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
            List<String> planetsWithSleepers = ButtonHelper.getAllPlanetsWithSleeperTokens(player, activeGame);
            String ident = Helper.getFactionIconFromDiscord(player.getFaction());
            if(planetsWithSleepers.size() > 5){
                String message2 = ident + " has more than 5 sleepers out. Use buttons to remove a sleeper token";
                List<Button> buttons = ButtonHelper.getButtonsForRemovingASleeper(player, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
            }
        }
    }


}
