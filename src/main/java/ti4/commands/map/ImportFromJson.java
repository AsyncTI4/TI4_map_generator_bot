package ti4.commands.map;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ImportFromJson extends GameStateSubcommand {

    public ImportFromJson() {
        super(Constants.IMPORT_MAP_JSON, "Import map data from external JSON", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !FoWHelper.isGameMaster(event.getUser().getId(), game)) {
            MessageHelper.replyToMessage(event, "Only useable by GM in FoW");
            return;
        }

        TextInput.Builder url = TextInput.create("url", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("http://your.url/fow123_map.json");

        Modal importMapModal = Modal.create("importMapFromJSON", "Import map")
                .addComponents(Label.of("URL", url.build()))
                .build();
        event.replyModal(importMapModal).queue();
    }
}
