package ti4.commands2.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.manage.GameSaveService;
import ti4.message.MessageHelper;

class Undo extends GameStateSubcommand {

    public Undo() {
        super(Constants.UNDO, "Undo the last action", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.UNDO_TO_BEFORE_COMMAND, "Command to undo back to").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String gameName = game.getName();
        if (!event.getChannel().getName().startsWith(gameName + "-")) {
            MessageHelper.replyToMessage(event, "Undo must be executed in game channel only!");
            return;
        }

        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Undo failed - Must confirm with YES");
            return;
        }

        String gameToUndoBackTo = event.getOption(Constants.UNDO_TO_BEFORE_COMMAND, null, OptionMapping::getAsString);
        if (gameToUndoBackTo == null || gameToUndoBackTo.isEmpty()) {
            MessageHelper.replyToMessage(event, "Must specify command to undo back to");
            return;
        }

        if (gameToUndoBackTo.toLowerCase().contains("fog of war")) {
            MessageHelper.replyToMessage(event, "Game is Fog of War - limited to a single undo at a time.");
            GameSaveService.undo(game, event);
            return;
        }

        if (!gameToUndoBackTo.contains(gameName)) {
            MessageHelper.replyToMessage(event, "Undo failed - Parameter doesn't look right: " + gameToUndoBackTo);
            return;
        }


    }
}
