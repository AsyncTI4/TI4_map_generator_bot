package ti4.commands.map;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.map.MapJsonIOService;

class ExportToJson extends GameStateSubcommand {

    public ExportToJson() {
        super(Constants.EXPORT_MAP_JSON, "Export map data to JSON", false, false);
        addOptions(new OptionData(OptionType.BOOLEAN, "include_tokens", "Include tokens (default: true)"));
        // addOptions(new OptionData(OptionType.BOOLEAN, "include_attachments", "Include attachments (default: true)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "include_lore", "Include lore (default: true)"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game to export").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isHasEnded()
                && game.isFowMode()
                && !FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            MessageHelper.replyToMessage(event, "Only useable by GM in FoW");
            return;
        }

        boolean includeTokens = event.getOption("include_tokens") != null
                ? event.getOption("include_tokens").getAsBoolean()
                : true;
        boolean includeAttachments = false; /*event.getOption("include_attachments") != null
                ? event.getOption("include_attachments").getAsBoolean()
                : true;*/
        boolean includeLore = event.getOption("include_lore") != null
                ? event.getOption("include_lore").getAsBoolean()
                : true;
        String json = MapJsonIOService.exportMapAsJson(event, game, includeTokens, includeAttachments, includeLore);
        if (json == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Failed to export map to JSON.");
            return;
        }

        File exportFile = new File(game.getName() + "_map.json");
        try (FileWriter writer = new FileWriter(exportFile)) {
            writer.write(json);
        } catch (IOException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Failed to export map to JSON: " + e.getMessage());
            return;
        }

        event.getChannel()
                .sendFiles(FileUpload.fromData(exportFile))
                .setContent("### Export of " + game.getName() + " Map:\n-# note that this feature is still WIP")
                .queue();
    }
}
