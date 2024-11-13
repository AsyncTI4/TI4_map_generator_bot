package ti4.commands.special;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
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
        Game game = getGame();
        doResearch(event, game);
    }

    public static void doResearch(GenericInteractionCreateEvent event, Game game) {

        List<Player> players = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
                for (Player p2 : game.getRealPlayers()) {
                    if (FoWHelper.playerHasShipsInSystem(p2, tile) && !players.contains(p2)) {
                        players.add(p2);
                    }
                }
            }
            if (FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition())) {
                UnitHolder uH = tile.getUnitHolders().get(Constants.SPACE);
                for (Player player : game.getRealPlayers()) {
                    uH.removeAllShips(player);
                }
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelper.checkFleetInEveryTile(p2, game, event);
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Removed all ships from alphas/betas\nYou may use the button to get your tech.", List.of(Buttons.GET_A_TECH));
        StringBuilder msg = new StringBuilder(" may research tech due to Wormhole Research.");
        if (game.isFowMode()) {
            for (Player p2 : players) {
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), p2.getRepresentation() + msg);
            }
        } else {
            for (Player p2 : players) {
                msg.insert(0, p2.getRepresentation());
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg.toString());
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
