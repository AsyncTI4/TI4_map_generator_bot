package ti4.commands.special;

import java.io.File;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.GenerateMap;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class IonFlip extends SpecialSubcommandData {

    public IonFlip() {
        super(Constants.ION_TOKEN_FLIP, "Flip ION Storm Token");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        UnitHolder spaceUnitHolder = tile.getUnitHolders().get(Constants.SPACE);
        if (spaceUnitHolder == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "No valid Space found");
            return;
        }
        if (spaceUnitHolder.getTokenList().contains(Constants.TOKEN_ION_ALPHA_PNG)){
            tile.removeToken(Constants.TOKEN_ION_ALPHA_PNG, spaceUnitHolder.getName());
            tile.addToken(Constants.TOKEN_ION_BETA_PNG, spaceUnitHolder.getName());
        } else if (spaceUnitHolder.getTokenList().contains(Constants.TOKEN_ION_BETA_PNG)){
            tile.removeToken(Constants.TOKEN_ION_BETA_PNG, spaceUnitHolder.getName());
            tile.addToken(Constants.TOKEN_ION_ALPHA_PNG, spaceUnitHolder.getName());
        }
        File file = GenerateMap.getInstance().saveImage(activeGame, event);
        MessageHelper.replyToMessage(event, file);
    }
}
