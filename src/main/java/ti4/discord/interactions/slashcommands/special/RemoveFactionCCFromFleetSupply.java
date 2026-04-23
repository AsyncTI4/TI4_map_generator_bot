package ti4.discord.interactions.slashcommands.special;

import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

class RemoveFactionCCFromFleetSupply extends AddRemoveFactionCCToFromFleet {

    RemoveFactionCCFromFleetSupply() {
        super(Constants.REMOVE_CC_FROM_FS, "Remove faction command token from fleet pool");
    }

    @Override
    void action(List<String> colors, Game game, Player player) {
        for (String color : colors) {
            player.removeMahactCC(color);
        }
    }
}
