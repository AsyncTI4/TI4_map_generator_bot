package ti4.commands.explore;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class UseExplore extends ExploreSubcommandData {

    public UseExplore() {
        super(Constants.USE, "Draw and activate an explore card from the deck or discard");
        addOptions(idOption.setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        String id = event.getOption(Constants.EXPLORE_CARD_ID).getAsString();
        if (activeGame.pickExplore(id) != null) {
            OptionMapping planetOption = event.getOption(Constants.PLANET);
            String planetName = null;
            if (planetOption != null) {
                planetName = planetOption.getAsString();
            }
            Tile tile = null;
            if (activeGame.getPlanets().contains(planetName)) {
                for (Tile tile_ : activeGame.getTileMap().values()) {
                    if (tile != null) {
                        break;
                    }
                    for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                        if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                            tile = tile_;
                            break;
                        }
                    }
                }
                if (tile == null) {
                    sendMessage("System not found that contains planet");
                    return;
                }
            }
            Player player = activeGame.getPlayer(event.getUser().getId());
            player = Helper.getGamePlayer(activeGame, player, event, null);
            String messageText = "Used card: " + id + " by player: " + player.getUserName();
            resolveExplore(event, id, tile, planetName, messageText, ExpFrontier.checkIfEngimaticDevice(player, id), player, activeGame);
        } else {
            sendMessage("Invalid card ID");
        }
    }

}
