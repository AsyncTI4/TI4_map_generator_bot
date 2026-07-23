package ti4.discord.interactions.commands.special;

import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.service.abilities.MahactTokenService;
import ti4.service.leader.CommanderUnlockCheckService;

class AddFactionCCToFleetSupply extends AddRemoveFactionCCToFromFleet {

    AddFactionCCToFleetSupply() {
        super(Constants.ADD_CC_TO_FS, "Add Faction Command Token to Fleet Pool");
    }

    @Override
    void action(List<String> colors, Game game, Player player) {
        for (String color : colors) {
            MahactTokenService.addMahactToken(game, player, color);
            Helper.isCCCountCorrect(game, color);
        }
        CommanderUnlockCheckService.checkPlayer(player, "mahact");
    }
}
