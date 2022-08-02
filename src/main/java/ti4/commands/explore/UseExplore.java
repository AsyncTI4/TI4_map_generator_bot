package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class UseExplore extends ExploreSubcommandData {

    public UseExplore() {
        super(Constants.USE, "Draw and activate an explore card from the deck or discard");
        addOptions(idOption.setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        @SuppressWarnings("ConstantConditions")
        String id = event.getOption(Constants.EXPLORE_CARD_ID).getAsString();
        if (activeMap.pickExplore(id) != null) {
            OptionMapping planetOption = event.getOption(Constants.PLANET);
            String planetName = null;
            if (planetOption != null) {
                planetName = planetOption.getAsString();
            }
            if (!activeMap.getPlanets().contains(planetName)) {
                MessageHelper.replyToMessage(event, "Planet not found in map");
                return;
            }
            Tile tile = null;
            for (Tile tile_ : activeMap.getTileMap().values()) {
                if (tile != null) {
                    break;
                }
                for (java.util.Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                    if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                        tile = tile_;
                        break;
                    }
                }
            }
            if (tile == null) {
                MessageHelper.replyToMessage(event, "System not found that contains planet");
                return;
            }
            String messageText = "Used card: " + id + " by player: " + activeMap.getPlayer(event.getUser().getId()).getUserName();
            resolveExplore(event, id, tile, planetName, messageText);
        } else {
            MessageHelper.replyToMessage(event, "Invalid card ID");
        }
    }

}
