package ti4.commands.special;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
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

    public static void doResearch(GenericInteractionCreateEvent event, Game activeGame) {
        for(Tile tile : activeGame.getTileMap().values()){
            if(FoWHelper.doesTileHaveAlphaOrBeta(activeGame, tile.getPosition())){
                UnitHolder uH = tile.getUnitHolders().get(Constants.SPACE);
                for(Player player : activeGame.getRealPlayers()){
                   uH.removeAllUnits(player.getColor());
                }
            }
        }
        activeGame.setComponentAction(true);
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "Removed all ships from alphas/betas\nYou can use the button to get your tech", List.of(Buttons.GET_A_TECH));
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
