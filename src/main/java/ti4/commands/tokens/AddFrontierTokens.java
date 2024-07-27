package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import ti4.commands.Command;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AddFrontierTokens implements Command {

    @Override
    public String getActionID() {
        return Constants.ADD_FRONTIER_TOKENS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    public static void parsingForTile(GenericInteractionCreateEvent event, Game game) {
        Collection<Tile> tileList = game.getTileMap().values();
        for (Tile tile : tileList) {
            if (((tile.getPlanetUnitHolders().size() == 0 && tile.getUnitHolders().size() == 2) || Mapper.getFrontierTileIds().contains(tile.getTileID())) && !game.isBaseGameMode()) {
                boolean hasMirage = false;
                for (UnitHolder unitholder : tile.getUnitHolders().values()) {
                    if (unitholder.getName().equals(Constants.MIRAGE)) {
                        hasMirage = true;
                        break;
                    }
                }
                if (!hasMirage) AddToken.addToken(event, tile, Constants.FRONTIER, game);
            }
        }
        if (game.getRound() == 1) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("deal2SOToAll", "Deal 2 SO To All"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Press this button after every player is setup", buttons);
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Game game = gameManager.getUserActiveGame(userID);
            parsingForTile(event, game);
            GameSaveLoadManager.saveMap(game, event);
            ShowGame.simpleShowGame(game, event);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), "Add Frontier tokens to all possible tiles")
                .addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm")
                    .setRequired(true))

        );
    }
}
