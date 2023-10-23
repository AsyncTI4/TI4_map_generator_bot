package ti4.commands.franken;

import java.util.List;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

import ti4.map.Player;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.TechnologyModel.TechnologyType;

public class FactionTechAdd extends FactionTechAddRemove {
    public FactionTechAdd() {
        super(Constants.FACTION_TECH_ADD, "Add a faction tech to your faction");
    }

    @Override
    public void doAction(Player player, List<String> techIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveGame())).append(" added techs:\n");
        for (String techID : techIDs) {
            if (player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player had this faction tech)");
            } else {
                sb.append("> ").append(Helper.getTechRepresentation(techID));
            }
            sb.append("\n");
            player.addFactionTech(techID);

            // ADD BASE UNIT IF ADDING UNIT UPGRADE TECH
            TechnologyModel techModel = Mapper.getTech(techID);
            if (techModel == null) continue;
            if (techModel.getType().equals(TechnologyType.UNITUPGRADE)) {
                UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
                unitModel.getUpgradesFromUnitId().ifPresent(upgradesFromUnitId -> {
                    player.removeOwnedUnitByID(unitModel.getBaseType());
                    player.addOwnedUnitByID(upgradesFromUnitId);
                });
            }
        }
        sendMessage(sb.toString());
    }
}
