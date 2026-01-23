package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class EditStoredValue extends GameStateSubcommand {

    public EditStoredValue() {
        super(
                "edit_stored_value",
                "Edit a stored value. (Warning: dont mess with what you dont understand)",
                true,
                false);
        addOption(OptionType.STRING, "stored_key", "Key to the value", true, false);
        addOption(OptionType.STRING, "stored_value", "Value to set", true, false);
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to check", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        game.setStoredValue(
                event.getOption("stored_key").getAsString(),
                event.getOption("stored_value").getAsString().trim());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Edited \"" + event.getOption("stored_key").getAsString() + "\" to \""
                        + event.getOption("stored_value").getAsString().trim() + "\"");
    }
}
