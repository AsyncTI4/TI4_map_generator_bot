package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.model.UnitModel;

@UtilityClass
public class MyrrUnitsHandler {
    
    // Replicators
    public static String getReplicatorProductionReminder(Player player, Tile tile) {
        int replicatorCount = 0;
        int replicatorProduction = 0;
    
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                if (!unitKey.getColor().equalsIgnoreCase(player.getColor())) {
                    continue;
                }
    
                UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), unitHolder);
                if (unit == null
                        || (!"myrr_dreadnought".equals(unit.getId())
                                && !"myrr_dreadnought2".equals(unit.getId()))) {
                    continue;
                }
    
                int count = unitHolder.getUnitCount(unitKey);
                replicatorCount += count;
                replicatorProduction += count * unit.getProductionValue();
            }
        }
    
        if (replicatorCount == 0) {
            return "";
        }
    
        return "You have " + replicatorCount + " Replicator"
                + (replicatorCount == 1 ? "" : "s")
                + " in this system with a total PRODUCTION of " + replicatorProduction + ". "
                + "A reminder that these units may only produce dreadnoughts or units of the same type as those it transported.\n";
    }
}
