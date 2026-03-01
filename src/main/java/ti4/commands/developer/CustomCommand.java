package ti4.commands.developer;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;

class CustomCommand extends GameStateSubcommand {

    CustomCommand() {
        super("custom_command", "Custom command written for a custom purpose.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the command against.")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.FACTION_COLOR,
                        "Faction/color of the player to spawn Alliance Rider buttons for")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME, null, OptionMapping::getAsString);
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found: " + gameName);
            return;
        }

        Player player = getPlayer();

        List<Button> buttons = List.of(
                Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveOracle", "Resolve Oracle"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " may use this button to resolve _Oracle_.",
                buttons);
    }
}
