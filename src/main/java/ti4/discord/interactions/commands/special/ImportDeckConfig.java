package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.URLReaderHelper;
import ti4.message.MessageHelper;
import ti4.service.game.DeckConfigImportService;

class ImportDeckConfig extends GameStateSubcommand {

    ImportDeckConfig() {
        super(
                Constants.IMPORT_DECK_CONFIG,
                "Import a deck-set config (from the Deck-editor tool) into this game",
                true,
                false);
        addOptions(
                new OptionData(OptionType.ATTACHMENT, "file", "Deck config .json exported from the Deck-editor tool"),
                new OptionData(OptionType.STRING, "url", "URL to the deck config JSON (alternative to file)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            MessageHelper.replyToMessage(event, "Only useable by GM in FoW");
            return;
        }

        OptionMapping fileOption = event.getOption("file");
        OptionMapping urlOption = event.getOption("url");
        String url;
        if (fileOption != null) {
            Attachment attachment = fileOption.getAsAttachment();
            if (!"json".equalsIgnoreCase(attachment.getFileExtension())) {
                MessageHelper.sendMessageToEventChannel(event, "Deck config must be a .json file.");
                return;
            }
            url = attachment.getUrl();
        } else if (urlOption != null) {
            url = urlOption.getAsString();
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Provide either a `file` attachment or a `url`.");
            return;
        }

        String json = URLReaderHelper.readFromURL(url, event.getChannel());
        if (json == null) {
            // URLReaderHelper already reported the failure to the channel.
            return;
        }

        DeckConfigImportService.importDeckConfig(event, game, json);
    }
}
