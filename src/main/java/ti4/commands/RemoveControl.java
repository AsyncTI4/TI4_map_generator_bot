package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class RemoveControl extends AddRemoveCC {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, String color, Tile tile) {
        String planet = event.getOptions().get(2).getAsString().toLowerCase();
        planet = AliasHandler.resolvePlanet(planet);
        if (!tile.isSpaceHolderValid(planet)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: " + planet + " is not valid and not supported.");
            return;
        }
        
        String ccID = Mapper.getControlID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Control token: " + color + " is not valid and not supported.");
            return;
        }
        tile.removeControl(ccID, planet);
    }

    @Override
    protected String getActionDescription() {
        return "Remove control token to planet";
    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_CONTROL;
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
                        .addOptions(new OptionData(OptionType.STRING, Constants.PLANET_NAME, "Planet name")
                                .setRequired(true))
        );
    }
}
