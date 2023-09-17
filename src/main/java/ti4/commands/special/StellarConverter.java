package ti4.commands.special;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

public class StellarConverter extends SpecialSubcommandData {

    public StellarConverter() {
        super(Constants.STELLAR_CONVERTER, "Select planet to use Stellar Converter on it");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping planetOption = event.getOption(Constants.PLANET);
        if (planetOption == null){
            return;
        }
        String planetName = planetOption.getAsString();
        if (!activeGame.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }
        secondHalfOfStellar(activeGame, planetName, event);
    }
    public void secondHalfOfStellar(Game activeGame, String planetName, GenericInteractionCreateEvent event){
        Tile tile = null;
        UnitHolder unitHolder = null;
        for (Tile tile_ : activeGame.getTileMap().values()) {
            if (tile != null) {
                break;
            }
            for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
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
        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "There is a great disturbance in the Force, as if millions of voices suddenly cried out in terror and were suddenly silenced");
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2.getPlanets().contains(planetName)){
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+ " we regret to inform you but "+ Mapper.getPlanet(planetName).getName() + " has been stellar converted");
            }
        }
        activeGame.removePlanet(unitHolder);
        tile.addToken(Constants.WORLD_DESTROYED_PNG, unitHolder.getName());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Mapper.getPlanet(planetName).getName() + " has been stellar converted");

    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
