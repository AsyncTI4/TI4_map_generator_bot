package ti4.commands.special;

import java.util.List;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.leader.CommanderUnlockCheckService;

class AddFactionCCToFleetSupply extends AddRemoveFactionCCToFromFleet {

    AddFactionCCToFleetSupply() {
        super(Constants.ADD_CC_TO_FS, "Add Faction Command Token to Fleet Pool");
    }

    @Override
    void action(List<String> colors, Game game, Player player) {
        for (String color : colors) {
            player.addMahactCC(color);
            Helper.isCCCountCorrect(game, color);
        }
        CommanderUnlockCheckService.checkPlayer(player, "mahact");
    }
}
