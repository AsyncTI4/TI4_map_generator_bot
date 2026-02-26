package ti4.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;

class CustomCommand extends Subcommand {

    CustomCommand() {
        super("custom_command", "Custom command written for a custom purpose.");
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
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can't find map for game name " + gameName);
            return;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Unable to determine which player should receive the Alliance Rider buttons.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "allianceRiderRandomAlly", "Random Ally"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", after checking for Sabos, please use buttons to resolve _Alliance Rider_.",
                buttons);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Spawned _Alliance Rider_ buttons for " + player.getRepresentationUnfogged() + " in " + game.getName()
                        + ".");
    }
}
