package ti4.service.breakthrough;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.UnitModel;

@UtilityClass
public class AutoFactoriesService {

    public String autoFactories() {
        return Mapper.getBreakthrough("hacanbt").getNameRepresentation();
    }

    private static int getNumberOfProducedNonFighterShips(Player player, Game game) {
        int count = 0;
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        for (Map.Entry<String, Integer> entry : producedUnits.entrySet()) {
            String unit2 = entry.getKey().split("_")[0];
            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
            UnitModel producedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();

            if (producedUnit.getIsShip() && !"ff".equals(producedUnit.getAsyncId())) {
                count += entry.getValue();
            }
        }
        return count;
    }

    public void resolveAutoFactories(Game game, Player player, String buttonID) {
        if (!player.hasUnlockedBreakthrough("hacanbt")) return;
        if (getNumberOfProducedNonFighterShips(player, game) < 3) return;

        String message =
                player.getPing() + " gained a command token into their fleet pool from " + autoFactories() + ".";
        message += "\n-# Fleet pool increased from " + player.gainFleetCC(1) + ".";

        if (game.getLaws().containsKey("regulations") && player.getEffectiveFleetCC() > 4) {
            String msg = player.getRepresentation() + ", reminder that _Fleet Regulations_ is a";
            msg += " law in play, which is limiting fleet pool to 4 tokens.";
            ButtonHelper.sendMessageToRightStratThread(player, game, msg, buttonID);
        }
        ButtonHelper.sendMessageToRightStratThread(player, game, message, buttonID);
    }
}
