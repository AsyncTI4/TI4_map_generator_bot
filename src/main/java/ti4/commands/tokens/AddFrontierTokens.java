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
import ti4.model.TileModel;

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

    public void parsingForTile(GenericInteractionCreateEvent event, Map activeMap) {
        Collection<Tile> tileList = activeMap.getTileMap().values();
        List<String> frontierTileList = Mapper.getFrontierTileIds();
        for (Tile tile : tileList) {
            if (frontierTileList.contains(tile.getTileID())) {
                boolean hasMirage = false;
                for (UnitHolder unitholder : tile.getUnitHolders().values()) {
                    if (unitholder.getName().equals(Constants.MIRAGE)) hasMirage = true;
                }
                if (!hasMirage) AddToken.addToken(event, tile, Constants.FRONTIER, activeMap);
            }
        }
        if(activeMap.getRound() == 1){
            List<Button> buttons = new ArrayList<Button>();
            buttons.add(Button.success("deal2SOToAll" , "Deal 2 SO To All"));
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), "Press this button after every player is setup", buttons);
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        if (!mapManager.isUserWithActiveMap(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Map activeMap = mapManager.getUserActiveMap(userID);
            parsingForTile(event, activeMap);
            MapSaveLoadManager.saveMap(activeMap, event);
            File file = GenerateMap.getInstance().saveImage(activeMap, event);
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
