package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

class Tags extends GameStateSubcommand {

    public Tags() {
        super("tags", "Add or remove a 'tag' to a game for sorting/reference", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Button button = Buttons.green("editTags~MDL", "Edit Tags");
        MessageHelper.sendMessageToChannelWithButton(
                event.getChannel(), "Press the below button to edit the game's tags:", button);
    }
}
