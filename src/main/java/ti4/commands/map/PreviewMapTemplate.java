package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.MapTemplateHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;

public class PreviewMapTemplate extends Subcommand {

    public PreviewMapTemplate() {
        super("preview_map_template", "Preview a map template.");
        addOption(OptionType.STRING, Constants.MAP_TEMPLATE, "Template to preview.", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapTemplate = event.getOption(Constants.MAP_TEMPLATE, null, OptionMapping::getAsString);
        Game game = new Game();
        postMapTemplate(event, mapTemplate, game);
    }

    private void postMapTemplate(GenericInteractionCreateEvent event, String mapTemplate, Game game) {
        if (!Mapper.isValidMapTemplate(mapTemplate)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid map template: " + mapTemplate);
        }
        MapTemplateModel model = Mapper.getMapTemplate(mapTemplate);
        FileUpload image = MapTemplateHelper.generateTemplatePreviewImage(event, game, model);
        MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), image);
    }
}
