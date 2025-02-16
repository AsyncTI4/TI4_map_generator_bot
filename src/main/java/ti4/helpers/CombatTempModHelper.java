package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ti4.image.Mapper;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.TileModel;
import ti4.service.combat.CombatRollType;

public class CombatTempModHelper {

    public static void InitializeNewTempMods(Player player, TileModel tile, UnitHolder holder) {
        List<TemporaryCombatModifierModel> unusedMods = player.getNewTempCombatModifiers();
        unusedMods = unusedMods.stream().filter(mod -> mod.getUseInTurn() == player.getNumberTurns())
            .toList();
        for (TemporaryCombatModifierModel mod : unusedMods) {
            mod.setUseInSystem(tile.getId());
            mod.setUseInUnitHolder(holder.getName());
            player.addTempCombatMod(mod);
        }
        player.clearNewTempCombatModifiers();
    }

    public static void EnsureValidTempMods(Player player, TileModel tile, UnitHolder holder) {
        List<TemporaryCombatModifierModel> tempMods = new ArrayList<>(player.getTempCombatModifiers());

        for (TemporaryCombatModifierModel mod : tempMods) {
            if (mod.getUseInTurn() != player.getNumberTurns()) {
                player.removeTempMod(mod);
                continue;
            }

            switch (mod.getModifier().getPersistenceType()) {
                case Constants.MOD_TEMP_ONE_COMBAT -> {
                    if (!mod.getUseInUnitHolder().equals(holder.getName())
                        || !mod.getUseInSystem().equals(tile.getId())) {
                        player.removeTempMod(mod);
                    }
                }
                case Constants.MOD_TEMP_ONE_TACTICAL_ACTION -> {
                    if (!mod.getUseInSystem().equals(tile.getId())) {
                        player.removeTempMod(mod);
                    }
                }
            }
        }
    }

    public static List<NamedCombatModifierModel> BuildCurrentRoundTempNamedModifiers(
        Player player,
        TileModel tile,
        UnitHolder holder,
        Boolean isApplyToOpponent,
        CombatRollType rollType
    ) {
        EnsureValidTempMods(player, tile, holder);
        List<TemporaryCombatModifierModel> tempMods = new ArrayList<>(player.getTempCombatModifiers());
        List<NamedCombatModifierModel> currentRoundResults = new ArrayList<>();
        for (TemporaryCombatModifierModel mod : tempMods) {
            currentRoundResults.add(new NamedCombatModifierModel(mod.getModifier(),
                Mapper.getRelatedName(mod.getRelatedID(), mod.getRelatedType())));
            if (mod.getModifier().getPersistenceType().equals(Constants.MOD_TEMP_ONE_ROUND) && rollType != CombatRollType.AFB) {
                player.removeTempMod(mod);
            }
        }
        currentRoundResults = currentRoundResults.stream()
            .filter(mod -> mod.getModifier().getApplyToOpponent().equals(isApplyToOpponent))
            .filter(mod -> mod.getModifier().getForCombatAbility().equals(rollType)).toList();

        return currentRoundResults;
    }

    public static TemporaryCombatModifierModel getPossibleTempModifier(String relatedType, String relatedID, int currentTurnCount) {
        TemporaryCombatModifierModel result = null;
        var combatModifiers = Mapper.getCombatModifiers();
        Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
            .filter(modifier -> modifier.isRelevantTo(relatedType, relatedID)
                && (modifier.getPersistenceType().equals(Constants.MOD_TEMP_ONE_ROUND)
                    || modifier.getPersistenceType().equals(Constants.MOD_TEMP_ONE_COMBAT)
                    || modifier.getPersistenceType().equals(Constants.MOD_TEMP_ONE_TACTICAL_ACTION)))
            .findFirst();
        if (relevantMod.isPresent()) {
            result = new TemporaryCombatModifierModel(relatedType, relatedID, relevantMod.get(), currentTurnCount);
        }
        return result;
    }
}