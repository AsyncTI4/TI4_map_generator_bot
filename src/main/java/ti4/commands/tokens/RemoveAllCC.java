package ti4.commands.tokens;

import java.util.Collection;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Tile;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class RemoveAllCC implements ParentCommand {

    void parsingForTile(SlashCommandInteractionEvent event, Game game) {
        Collection<Tile> tileList = game.getTileMap().values();
        for (Tile tile : tileList) {
            tile.removeAllCC();
        }
    }

    public String getName() {
        return Constants.REMOVE_ALL_CC;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        if (!UserGameContextManager.doesUserHaveContextGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Game game = CommandHelper.getGameName(event);
            parsingForTile(event, game);
            GameSaveLoadManager.saveGame(game, event);
            ShowGame.simpleShowGame(game, event);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getName(), "Remove all CCs from entire map")
                .addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm")
                    .setRequired(true)));
    }
}
