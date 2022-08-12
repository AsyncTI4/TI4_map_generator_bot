package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class ExpPlanet extends ExploreSubcommandData {

    public ExpPlanet() {
        super(Constants.PLANET, "Explore a specific planet.");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TRAIT, "Planet trait to explore").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Colort").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        @SuppressWarnings("ConstantConditions")
        String planetName = planetOption.getAsString();
        Map activeMap = getActiveMap();
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
        planetName = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet(planetName));
        String planet = Mapper.getPlanet(planetName);
        if (planet == null) {
            MessageHelper.replyToMessage(event, "Invalid planet");
            return;
        }
        String[] planetInfo = planet.split(",");
        String drawColor = planetInfo[1];
        OptionMapping traitOption = event.getOption(Constants.TRAIT);
        if (traitOption != null){
            drawColor = traitOption.getAsString();
        }
        String cardID = activeMap.drawExplore(drawColor);
        if (cardID == null) {
            MessageHelper.replyToMessage(event, "Planet cannot be explored");
            return;
        }
        String messageText = displayExplore(cardID);
        Player player = activeMap.getPlayer(event.getUser().getId());
        messageText += "\n" + "Explored: " + planetName + " by player: " + (player != null ? player.getUserName() : event.getUser().getName());
        resolveExplore(event, cardID, tile, planetName, messageText);
    }
}
