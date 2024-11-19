package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SetMapTemplate extends MapSubcommandData {

    public SetMapTemplate() {
        super("set_map_template", "Set the template for the map.");
        addOptions(new OptionData(OptionType.STRING, Constants.MAP_TEMPLATE, "Template for the map.").setRequired(true).setAutoComplete(true));
        addOption(OptionType.BOOLEAN, "transform", "True to attempt to transform the current map to the new map template.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapTemplate = event.getOption(Constants.MAP_TEMPLATE, null, OptionMapping::getAsString);
        boolean transform = event.getOption("transform", false, OptionMapping::getAsBoolean);
        Game game = getActiveGame();

        setMapTemplate(event, mapTemplate, transform, game);
    }

    private void setMapTemplate(GenericInteractionCreateEvent event, String mapTemplate, boolean transform, Game game) {
        if (!Mapper.isValidMapTemplate(mapTemplate)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid map template: " + mapTemplate);
        }
        if (game.getMapTemplateID() != null && !game.getMapTemplateID().equals("null") && !transform) {
            MessageHelper.sendMessageToEventChannel(event, "Map template already set to: `" + game.getMapTemplateID() + "`\nUse the transform option to overwrite.");
            return;
        }

        if (transform) {
            // check if map templates are compatable
            MessageHelper.sendMessageToEventChannel(event, "New map template (" + mapTemplate + ") is not compatable with the old map template (" + game.getMapTemplateID() + ")");
            return;
        }

        game.setMapTemplateID(mapTemplate);
        MessageHelper.sendMessageToEventChannel(event, "Map template set to: " + mapTemplate);
    }
}
