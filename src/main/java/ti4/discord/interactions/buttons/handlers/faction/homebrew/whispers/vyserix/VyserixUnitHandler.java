package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.model.UnitModel;

@UtilityClass
public class VyserixUnitHandler {

    public static Map<UnitModel, Integer> getVyserixFlagshipAfbUnits(Player player, Tile tile) {
        Map<UnitModel, Integer> afbUnits = new HashMap<>();
        if (player == null || !ButtonHelper.doesPlayerHaveFSHere("vyserix_flagship", player, tile)) {
            return afbUnits;
        }

        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = tile.getSpaceUnitHolder().getUnitAsyncIdsOnHolder(colorID);
        for (var entry : unitsByAsyncId.entrySet()) {
            UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
            if (model == null
                    || !model.getIsShip()
                    || "fighter".equalsIgnoreCase(model.getBaseType())
                    || "vyserix_flagship".equalsIgnoreCase(model.getId())
                    || model.getAfbDieCount(player) > 0) {
                continue;
            }
            UnitModel afbUnit = new UnitModel();
            afbUnit.setId(model.getId() + "_vyserixflagshipafb");
            afbUnit.setBaseType(model.getBaseType());
            afbUnit.setAsyncId(model.getAsyncId());
            afbUnit.setName(model.getName());
            afbUnit.setFaction(player.getFaction());
            afbUnit.setIsShip(true);
            afbUnit.setAfbHitsOn(7);
            afbUnit.setAfbDieCount(1);
            afbUnits.put(afbUnit, entry.getValue());
        }
        return afbUnits;
    }
}
