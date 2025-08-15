package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SetMapTemplate extends GameStateSubcommand {

    public SetMapTemplate() {
        super("set_map_template", "Set the template for the map.", true, false);
        addOption(OptionType.STRING, Constants.MAP_TEMPLATE, "Template for the map.", true, true);
        addOption(
                OptionType.BOOLEAN,
                "transform",
                "True to attempt to transform the current map to the new map template.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapTemplate = event.getOption(Constants.MAP_TEMPLATE, null, OptionMapping::getAsString);
        boolean transform = event.getOption("transform", false, OptionMapping::getAsBoolean);
        setMapTemplate(event, mapTemplate, transform, getGame());
    }

    private void setMapTemplate(GenericInteractionCreateEvent event, String mapTemplate, boolean transform, Game game) {
        if (!Mapper.isValidMapTemplate(mapTemplate)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid map template: " + mapTemplate);
        }
        if (game.getMapTemplateID() != null && !"null".equals(game.getMapTemplateID()) && !transform) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Map template already set to: `" + game.getMapTemplateID()
                            + "`\nUse the transform option to overwrite.");
            return;
        }

        if (transform) {
            // check if map templates are compatible
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "New map template (" + mapTemplate + ") is not compatible with the old map template ("
                            + game.getMapTemplateID() + ")");
            return;
        }

        game.setMapTemplateID(mapTemplate);
        MessageHelper.sendMessageToEventChannel(event, "Map template set to: " + mapTemplate);
    }
}
