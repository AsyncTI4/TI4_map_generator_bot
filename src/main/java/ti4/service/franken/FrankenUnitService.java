package ti4.service.franken;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftCategory;
import ti4.game.Player;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.VeiledHeartService;

@UtilityClass
public class FrankenUnitService {

    private static void removeDuplicates(Player player, UnitModel addedUnit) {
        boolean keepUpgrades = false;
        if (player.getGame().isTwilightsFallMode()
                && List.of(Units.UnitType.Flagship, Units.UnitType.Mech).contains(addedUnit.getUnitType())) {
            if (addedUnit.getIsUpgrade()) {
                // Adding a TF flagship/mech upgrade never removes existing units
                return;
            }
            // Because the added flagship/mech is not an upgrade, it must belong to a Mahact King.
            // These are normally only added during the draft, which means the franken faction's
            // base factionless flagship/mech may still exist and must be removed.
            // However, it's possible a flagship/mech upgrade has already been drafted.
            // The Mahact King's units should not override that upgrade, so upgrades are kept.
            keepUpgrades = true;
        }
        Stream<UnitModel> unitsToRemove = player.getUnitsByAsyncID(addedUnit.getAsyncId()).stream();
        if (keepUpgrades) {
            unitsToRemove = unitsToRemove.filter(Predicate.not(UnitModel::getIsUpgrade));
        }
        unitsToRemove.map(UnitModel::getAlias).forEach(player::removeOwnedUnitByID);
    }

    public static void addUnits(
            GenericInteractionCreateEvent event, Player player, List<String> unitIDs, boolean dupes) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added units:\n");
        for (String unitID : unitIDs) {
            if (VeiledHeartService.canBeVeiled(player.getGame(), unitID)) {
                VeiledHeartService.addVeiledCard(player, unitID);
                sb.append("> ").append(" veiled unit (reveal using the button in the `#cards-info` thread)");
                continue;
            }
            if (player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player had this unit)");
            } else {
                UnitModel unitModel = Mapper.getUnit(unitID);
                if (!dupes) {
                    removeDuplicates(player, unitModel);
                }
                String unitText = unitID;
                DraftCategory category = FrankenAlternateTextService.getUnitCategory(unitID);
                if (category != null) {
                    unitText = FrankenAlternateTextService.getRepresentationWithAlternateText(
                            player.getGame(),
                            category,
                            unitID,
                            unitModel.getNameRepresentation(),
                            unitModel.getUnitRepresentation());
                }
                sb.append("> ").append(unitText);
                player.addOwnedUnitByID(unitID);
            }
            if ("naaz_mech".equalsIgnoreCase(unitID)) {
                player.addOwnedUnitByID("naaz_mech_space");
                sb.append("> naaz_mech_space");
            }
            sb.append('\n');
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }

    public static void removeUnits(GenericInteractionCreateEvent event, Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed units:\n");
        for (String unitID : unitIDs) {
            if (player.getGame().isVeiledHeartMode() && VeiledHeartService.hasVeiledCard(player, unitID)) {
                VeiledHeartService.removeVeiledCard(player, unitID);
                sb.append("> veiled ").append(unitID);
            } else {
                if (!player.ownsUnit(unitID)) {
                    sb.append("> ").append(unitID).append(" (player did not have this unit)");
                } else {
                    sb.append("> ").append(unitID);
                }
                sb.append('\n');
                player.removeOwnedUnitByID(unitID);
                UnitModel u = Mapper.getUnit(unitID);
                if (u.getUnitType() != UnitType.Flagship && u.getUnitType() != UnitType.Mech) {
                    String replacementUnit = u.getBaseType();
                    player.addOwnedUnitByID(replacementUnit);
                }

                if ("naaz_mech".equalsIgnoreCase(unitID)) {
                    player.removeOwnedUnitByID("naaz_mech_space");
                }
            }
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }

    public static void removeMonuments(GenericInteractionCreateEvent event, Player player, List<String> monumentIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed monuments:\n");
        for (String monumentID : monumentIDs) {
            if (!player.ownsUnit(monumentID)) {
                sb.append("> ").append(monumentID).append(" (player did not have this monument)");
            } else {
                sb.append("> ").append(monumentID);
                player.removeOwnedUnitByID(monumentID);
            }
            sb.append('\n');
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }
}
