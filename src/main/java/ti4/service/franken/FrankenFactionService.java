package ti4.service.franken;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.UnitModel;
import ti4.service.game.MonumentsService;

@UtilityClass
public class FrankenFactionService {
    private static Stream<String> getFactionUnits(FactionModel faction) {
        return faction.getUnits().stream().filter(u -> faction.getAlias()
                .equals(Mapper.getUnit(u).getFaction().orElse(null)));
    }

    private static void removeFactionComponents(
            GenericInteractionCreateEvent event, Player player, FactionModel faction) {
        if (faction == null) {
            return;
        }
        // Remove Faction Commodities
        player.setCommoditiesBase(player.getCommoditiesBase() - faction.getCommodities());
        // Remove Faction Units
        getFactionUnits(faction).forEach(player::removeOwnedUnitByID);
        // Remove Faction Monument
        if (player.getGame().isMonumentsMode()) {
            UnitModel monument = MonumentsService.getFactionMonument(faction);
            if (monument != null) {
                player.removeOwnedUnitByID(monument.getId());
            }
        }
    }

    private static void addFactionComponents(GenericInteractionCreateEvent event, Player player, FactionModel faction) {
        // Add Faction Commodities
        player.setCommoditiesBase(player.getCommoditiesBase() + faction.getCommodities());
        // Add Faction Units
        FrankenUnitService.addUnits(event, player, getFactionUnits(faction).toList(), false);
        // Add Faction Monument
        if (player.getGame().isMonumentsMode()) {
            UnitModel monument = MonumentsService.getFactionMonument(faction);
            if (monument != null && !player.ownsUnit(monument.getId())) {
                player.addOwnedUnitByID(monument.getId());
            }
        }
    }

    private static void fillEmptyUnitTypes(GenericInteractionCreateEvent event, Player player) {
        Map<UnitType, String> unitsToAdd = Stream.of(
                        UnitType.Infantry,
                        UnitType.Mech,
                        UnitType.Pds,
                        UnitType.Spacedock,
                        UnitType.Fighter,
                        UnitType.Destroyer,
                        UnitType.Cruiser,
                        UnitType.Carrier,
                        UnitType.Dreadnought,
                        UnitType.Flagship)
                .collect(Collectors.toMap(Function.identity(), UnitType::plainName));
        if (player.getGame().isTwilightsFallMode()) {
            unitsToAdd.put(UnitType.Warsun, "tf_warsun");
        }
        if (player.getGame().isMonumentsMode()) {
            unitsToAdd.put(UnitType.Monument, "monument");
        }
        player.getUnitsOwned().stream()
                .map(Mapper::getUnit)
                .map(UnitModel::getUnitType)
                .forEach(unitsToAdd::remove);
        unitsToAdd.values().forEach(player::addOwnedUnitByID);
    }

    private static String getUnusedFrankenFaction(Game game) {
        Set<String> usedFactions = game.getFactions();
        for (int i = 3; i <= 26; i++) {
            String faction = "franken" + i;
            if (!usedFactions.contains(faction)) {
                return faction;
            }
        }
        return null;
    }

    public static void setFaction(GenericInteractionCreateEvent event, Player player, String factionId) {
        FactionModel faction = Mapper.getFaction(factionId);
        if (faction == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, String.format("Can't set faction to `%s`. No such faction found.", factionId));
            return;
        }

        removeFactionComponents(event, player, player.getFactionSetupInfo());
        addFactionComponents(event, player, faction);
        fillEmptyUnitTypes(event, player);
        player.setFaction(factionId);

        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Successfully set faction to " + faction.getFactionName());
    }

    public static void unsetFaction(GenericInteractionCreateEvent event, Player player, String factionId) {
        FactionModel faction = Mapper.getFaction(factionId);
        if (faction == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, String.format("Can't unset faction `%s`. No such faction found.", factionId));
            return;
        }

        removeFactionComponents(event, player, faction);
        fillEmptyUnitTypes(event, player);
        player.setFaction(getUnusedFrankenFaction(player.getGame()));

        MessageHelper.sendEphemeralMessageToEventChannel(
                event,
                String.format(
                        "Successfully unset faction %s. New faction: %s",
                        faction.getFactionName(), player.getFaction()));
    }
}
