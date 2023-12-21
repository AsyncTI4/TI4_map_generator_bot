package ti4.commands.special;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class WormholeResearchFor extends SpecialSubcommandData {
    public WormholeResearchFor() {
        super(Constants.WORMHOLE_RESEARCH_FOR, "Destroy all ships in alpha/beta");
        
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        
        doResearch(event, activeGame);
    }

    public void doResearch(GenericInteractionCreateEvent event, Game activeGame) {
        for(Tile tile : activeGame.getTileMap().values()){
            if(FoWHelper.doesTileHaveAlphaOrBeta(activeGame, tile.getPosition())){
                UnitHolder uH = tile.getUnitHolders().get(Constants.SPACE);
                for(Player player : activeGame.getRealPlayers()){
                   uH.removeAllUnits(player.getColor());
                }
            }
        }
        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),"Removed all ships from alphas/betas");
         activeGame.setComponentAction(true);
        Button getTech = Button.success("acquireATech", "Get a tech");
        List<Button> buttons = new ArrayList<>();
        buttons.add(getTech);
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "You can use the button to get your tech", buttons);


    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
