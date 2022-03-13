package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class AddControl extends AddRemoveCC {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile) {
        OptionMapping option = event.getOption(Constants.PLANET_NAME);
        if (option != null) {
            String planetInfo = option.getAsString().toLowerCase();
            addControlToken(event, colors, tile, planetInfo);
        } else {
            Set<String> unitHolderIDs = tile.getUnitHolders().keySet();
            if (unitHolderIDs.size() == 2){
                HashSet<String> unitHolder = new HashSet<>(unitHolderIDs);
                unitHolder.remove(Constants.SPACE);
                addControlToken(event, colors, tile, unitHolder.iterator().next());
            } else {
                String message = "Multiple planets present in system, need to specify planet.";
                if (unitHolderIDs.size() == 1){
                    message = "No planets present in system.";
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
            }
        }
    }

    private void addControlToken(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile, String planetInfo) {
        planetInfo = planetInfo.replace(" ", "");
        StringTokenizer planetTokenizer = new StringTokenizer(planetInfo, ",");
        while (planetTokenizer.hasMoreTokens()) {
            String planet = planetTokenizer.nextToken();
            planet = AliasHandler.resolvePlanet(planet);
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
    public String getActionID() {
        return Constants.ADD_CONTROL;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color: red, green etc.")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.PLANET_NAME, "Planet name"))
        );
    }
}
