package ti4.service.map;

import java.util.Map;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.model.UnitTokenPosition;

public record ReinforcementStock(
        UnitKey unitKey,
        UnitTokenPosition position,
        @Nullable UnitModel model,
        int remaining) {

    @Nullable
    public static ReinforcementStock of(Game game, Player player, String unitID, Map<UnitKey, Integer> unitsOnBoard) {
        if ("csd".equals(unitID)) return null;
        String playerColor = player.getColor();
        UnitKey unitKey = Mapper.getUnitKey(unitID, playerColor);
        if ("cff".equals(unitID)) unitKey = Mapper.getUnitKey("ff", playerColor);
        if ("cgf".equals(unitID)) unitKey = Mapper.getUnitKey("gf", playerColor);

        int count = unitsOnBoard.getOrDefault(unitKey, 0);
        if ((player.ownsUnit("cabal_spacedock")
                        || player.ownsUnit("cabal_spacedock2")
                        || player.hasTech("tf-dimensionaltear"))
                && "sd".equals(unitID)) {
            count += unitsOnBoard.getOrDefault(Mapper.getUnitKey("csd", playerColor), 0);
        }

        UnitTokenPosition position = MapPositionMapper.getReinforcementsPosition(unitID);
        if (position == null) {
            return null;
        }
        int unitCap = player.getUnitCap(unitID);
        if (unitCap == 0 && !"fs".equals(unitID)) {
            unitCap = position.getPositionCount(unitID);
        }
        unitCap -= ("ws".equals(unitID) && player.ownsUnit("tf-dragonfreed")) ? 1 : 0;

        UnitModel model = player.getUnitFromUnitKey(unitKey);
        int remaining = unitCap - count;
        if ((player.hasUnlockedBreakthrough("freesystemsbt") || player.hasTech("tf-rallyingmarshalls"))
                && model != null
                && model.getUnitType() == UnitType.Infantry) {
            remaining = withRallyingInfantry(game, player, remaining, unitCap);
        }
        return new ReinforcementStock(unitKey, position, model, remaining);
    }

    private static int withRallyingInfantry(Game game, Player player, int remaining, int unitCap) {
        for (String planet : player.getPlanetsAllianceMode()) {
            UnitHolder planetUh = game.getUnitHolderFromPlanet(planet);
            if (planetUh != null && planetUh.getUnitCount(UnitType.Infantry, player) > 0) {
                remaining += 1;
                remaining = Math.min(remaining, unitCap);
            }
        }
        return remaining;
    }

    public boolean overLimit(Game game) {
        return !game.isHasEnded() && remaining < 0 && game.isCcNPlasticLimit();
    }
}
