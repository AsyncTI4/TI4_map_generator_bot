package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.Collection;

public class RemoveAllCC implements Command {

    void parsingForTile(SlashCommandInteractionEvent event, Game activeGame) {
        Collection<Tile> tileList = activeGame.getTileMap().values();
        for (Tile tile : tileList) {
            tile.removeAllCC();
        }
    }

    public String getActionID() {
        return Constants.REMOVE_ALL_CC;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Game activeGame = gameManager.getUserActiveGame(userID);
            parsingForTile(event, activeGame);
            GameSaveLoadManager.saveMap(activeGame, event);
            File file = GenerateMap.getInstance().saveImage(activeGame, event);
            MessageHelper.replyToMessage(event, file);
        }
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Remove all cc from entire map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm")
                                .setRequired(true))
        );
    }
}
