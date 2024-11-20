package ti4.commands.game;

import java.io.File;
import java.util.Map;
import java.util.NoSuchElementException;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;
import ti4.service.game.UndoService;

class Undo extends GameStateSubcommand {

    public Undo() {
        super(Constants.UNDO, "Undo the last action", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.UNDO_TO_BEFORE_COMMAND, "Command to undo back to").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!event.getChannel().getName().startsWith(game.getName() + "-")) {
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
            GameSaveLoadManager.undo(game, event);
            return;
        }
        if (!gameToUndoBackTo.contains(game.getName())) {
            MessageHelper.replyToMessage(event, "Undo failed - Parameter doesn't look right: " + gameToUndoBackTo);
            return;
        }
        String intToUndoBackTo = gameToUndoBackTo.replace(game.getName() + "_", "").replace(".txt", "");

        int gameToUndoBackToNumber = Integer.parseInt(intToUndoBackTo);

        Map<String, Game> undoFiles = UndoService.getAllUndoSavedGames(game);
        int maxSaveNumber = undoFiles.keySet().stream().map(s -> s.replace(game.getName() + "_", "").replace(".txt", ""))
            .mapToInt(Integer::parseInt).max().orElseThrow(NoSuchElementException::new);

        String undoFileToRestorePath = game.getName() + "_" + gameToUndoBackToNumber + ".txt";
        File undoFileToRestore = new File(Storage.getGameUndoDirectory(), undoFileToRestorePath);
        if (!undoFileToRestore.exists()) {
            MessageHelper.replyToMessage(event, "Undo failed - Couldn't find game to undo back to: " + undoFileToRestorePath);
            return;
        }
        Game gameToRestore = GameSaveLoadManager.loadGame(undoFileToRestore);
        if (gameToRestore == null) {
            MessageHelper.replyToMessage(event,
                "Undo failed - Couldn't load game to undo back to: " + undoFileToRestorePath);
            return;
        }

        StringBuilder sb = new StringBuilder(
            "Undoing Save #" + maxSaveNumber + " back to before Save #" + gameToUndoBackToNumber + ":\n");
        for (int i = maxSaveNumber; i > gameToUndoBackToNumber; i--) {
            String undoFile = game.getName() + "_" + i + ".txt";
            File undoFileToBeDeleted = new File(Storage.getGameUndoDirectory(), undoFile);
            if (undoFileToBeDeleted.exists()) {
                sb.append("> `").append(i).append("` ")
                    .append(undoFiles.get(undoFileToBeDeleted.getName()).getLatestCommand()).append("\n");
                undoFileToBeDeleted.delete();
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
        } else {
            ButtonHelper.findOrCreateThreadWithMessage(game, game.getName() + "-undo-log", sb.toString());
        }

        GameSaveLoadManager.undo(gameToRestore, event);
    }
}
