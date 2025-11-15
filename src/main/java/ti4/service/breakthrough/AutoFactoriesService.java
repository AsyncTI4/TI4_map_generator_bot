package ti4.service.breakthrough;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.BreakthroughModel;
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

        int fleet = player.getFleetCC();
        player.setFleetCC(player.getFleetCC() + 1);
        BreakthroughModel model = player.getBreakthroughModel();
        String autoFactoriesMsg =
                player.getPing() + " gained a fleet token from their breakthrough " + model.getNameRepresentation();
        autoFactoriesMsg += "\n-# > Fleet tokens increased from (" + fleet + " -> " + (fleet + 1) + ")";
        ButtonHelper.sendMessageToRightStratThread(player, game, autoFactoriesMsg, buttonID);
    }
}
