package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.buttons.handlers.actioncards.ActionCardDeck2ButtonHandler;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
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
        String gameName = event.getOption(Constants.GAME_NAME, null, OptionMapping::getAsString);
        String factionColor = event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString);

        if (gameName == null || factionColor == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Missing required options.");
            return;
        }
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found: " + gameName);
            return;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        Player player = Helper.getGamePlayer(game, null, event, factionColor);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Player not found for faction/color `" + factionColor + "` in game `" + gameName + "`.");
            return;
        }

        ActionCardDeck2ButtonHandler.resolveOracle(player, game, event.getChannel());
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Triggered _Oracle_ resolution for " + player.getRepresentationNoPing() + " in game `" + gameName + "`.");
    }
}
