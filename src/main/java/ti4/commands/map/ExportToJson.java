package ti4.commands.map;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            MessageHelper.replyToMessage(event, "Only useable by GM in FoW");
            return;
        }

        String json = MapJsonIOService.exportMapAsJson(event, game);
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
                .setContent("### Export of " + game.getName() + " Map:")
                .queue();
    }
}
