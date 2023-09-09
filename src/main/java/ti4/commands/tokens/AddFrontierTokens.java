package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
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

    public void parsingForTile(GenericInteractionCreateEvent event, Game activeGame) {
        Collection<Tile> tileList = activeGame.getTileMap().values();
        List<String> frontierTileList = Mapper.getFrontierTileIds();
        for (Tile tile : tileList) {
            if (frontierTileList.contains(tile.getTileID())) {
                boolean hasMirage = false;
                for (UnitHolder unitholder : tile.getUnitHolders().values()) {
                    if (unitholder.getName().equals(Constants.MIRAGE)) {
                        hasMirage = true;
                        break;
                    }
                }
                if (!hasMirage) AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
            }
        }
        if(activeGame.getRound() == 1){
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("deal2SOToAll" , "Deal 2 SO To All"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "Press this button after every player is setup", buttons);
        }
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
