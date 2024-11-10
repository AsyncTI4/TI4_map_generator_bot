package ti4.commands.tokens;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class AddControl extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {
        OptionMapping option = event.getOption(Constants.PLANET_NAME);
        if (option != null) {
            String planetInfo = option.getAsString().toLowerCase();
            addControlToken(event, colors, tile, planetInfo);
        } else {
            Set<String> unitHolderIDs = tile.getUnitHolders().keySet();
            if (unitHolderIDs.size() == 2) {
                Set<String> unitHolder = new HashSet<>(unitHolderIDs);
                unitHolder.remove(Constants.SPACE);
                addControlToken(event, colors, tile, unitHolder.iterator().next());
            } else {
                String message = "Multiple planets present in system, need to specify planet.";
                if (unitHolderIDs.size() == 1) {
                    message = "No planets present in system.";
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
            }
        }
    }

    private void addControlToken(SlashCommandInteractionEvent event, List<String> colors, Tile tile, String planetInfo) {
        planetInfo = planetInfo.replace(" ", "");
        StringTokenizer planetTokenizer = new StringTokenizer(planetInfo, ",");
        while (planetTokenizer.hasMoreTokens()) {
            String planet = planetTokenizer.nextToken();
            planet = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet(planet));
            if (!tile.isSpaceHolderValid(planet)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: " + planet + " is not valid and not supported.");
                continue;
            }

            for (String color : colors) {
                String ccID = Mapper.getControlID(color);
                String ccPath = tile.getCCPath(ccID);
                if (ccPath == null) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Control token: " + color + " is not valid and not supported.");
                }
                tile.addControl(ccID, planet);
            }
        }
    }

    @Override
    protected String getActionDescription() {
        return "Add control token to planet";
    }

    @Override
    public String getActionId() {
        return Constants.ADD_CONTROL;
    }
}
