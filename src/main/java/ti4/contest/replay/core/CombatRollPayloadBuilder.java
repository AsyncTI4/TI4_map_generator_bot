package ti4.contest.replay.core;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.substringBetween;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.UnitRollType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CombatModHelper;
import ti4.helpers.DiceHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.MiscEmojis;

/** Records live combat-roll details in the structured format consumed by combat replay. */
public final class CombatRollPayloadBuilder {
    private final List<CombatRollPayload.CombatRollNote> notes = new ArrayList<>();
    private final List<CombatRollPayload.CombatRollNote> delayedNotes = new ArrayList<>();
    private final List<CombatRollPayload.ModifierDisplay> modifiers = new ArrayList<>();
    private final List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
    private int diceRolled;

    public static CombatRollPayload.RollHeader buildHeader(
            Player player,
            Player opponent,
            Game game,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            String combatSummary) {
        String combatDisplayName = substringBetween(combatSummary, "rolls for ", " " + MiscEmojis.RollDice + " :");
        if (combatDisplayName == null) combatDisplayName = substringBetween(combatSummary, "rolls for ", " :");
        Integer combatRound = getCombatRound(player, game, tile, combatOnHolder, rollType);
        boolean thalnosReroll = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));
        return new CombatRollPayload.RollHeader(
                player.getFaction(),
                player.getColor(),
                player.getFactionEmoji(),
                opponent == null ? null : opponent.getFaction(),
                opponent == null ? null : opponent.getColor(),
                tile.getPosition(),
                tile.getTileID(),
                combatOnHolder.getName(),
                combatDisplayName,
                rollType,
                combatRound,
                thalnosReroll,
                game.isFowMode());
    }

    public static CombatRollPayload attachHeader(
            CombatRollPayload payload,
            Player player,
            Player opponent,
            Game game,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            String combatSummary) {
        return payload.withHeader(buildHeader(player, opponent, game, tile, combatOnHolder, rollType, combatSummary));
    }

    public void recordSingleUnitModifier(String sourceId, String unitId, int modifier) {
        addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                sourceId,
                unitId,
                1,
                Map.of("modifier", Integer.toString(modifier))));
    }

    public void recordRerollAvailable(int misses, String actorEmoji) {
        addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.REROLL_AVAILABLE,
                CombatRollNotePlacement.AFTER_TOTAL,
                "thalnos",
                null,
                misses,
                Map.of("actorEmoji", actorEmoji)));
    }

    public void recordUnitRepair(String unitId) {
        addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.UNIT_REPAIRED,
                CombatRollNotePlacement.BEFORE_MODIFIERS,
                unitId,
                unitId,
                1,
                Map.of("timing", "START_OF_COMBAT_ROUND")));
    }

    public void recordUnitDestroyed(String unitId, String unitName, int count, String actorEmoji) {
        delayedNotes.add(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.UNIT_DESTROYED_FROM_ROLL,
                CombatRollNotePlacement.AFTER_TOTAL,
                "thalnos",
                unitId,
                count,
                Map.of("actorEmoji", actorEmoji, "unitName", unitName)));
    }

    public void flushDelayedNotes() {
        notes.addAll(delayedNotes);
        delayedNotes.clear();
    }

    public void recordOpponentUnitDestroyed(String sourceId, String unitId, int count, String opponent) {
        addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.OPPONENT_UNIT_DESTROYED_FROM_ROLL,
                CombatRollNotePlacement.AFTER_UNIT_ROLLS,
                sourceId,
                unitId,
                count,
                Map.of("opponent", opponent)));
    }

    public void addModifierDisplays(
            List<NamedCombatModifierModel> namedModifiers,
            Map<UnitModel, Integer> units,
            Player player,
            Player opponent,
            Game game,
            CombatRollType rollType,
            Tile tile,
            UnitHolder combatOnHolder) {
        if (namedModifiers.isEmpty()) return;
        List<UnitModel> playerUnits = new ArrayList<>(units.keySet());
        for (NamedCombatModifierModel namedModifier : namedModifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            Map<String, Integer> effectiveValues = new HashMap<>();
            for (Map.Entry<UnitModel, Integer> unitEntry : units.entrySet()) {
                int effectiveValue = CombatModHelper.getCombinedModifierForUnit(
                        unitEntry.getKey(),
                        unitEntry.getValue(),
                        List.of(namedModifier),
                        player,
                        opponent,
                        game,
                        playerUnits,
                        rollType,
                        tile,
                        combatOnHolder);
                if (effectiveValue != 0) {
                    effectiveValues.put(unitEntry.getKey().getAsyncId(), effectiveValue);
                }
            }
            modifiers.add(new CombatRollPayload.ModifierDisplay(
                    modifier.getAlias(),
                    namedModifier.getName(),
                    modifier.getValue(),
                    modifier.getType(),
                    modifier.getScope(),
                    resolveScopeDisplay(modifier, units),
                    effectiveValues));
        }
    }

    public void addUnitRoll(
            UnitModel unitModel,
            int toHit,
            int modifierToHit,
            int unitCount,
            int dicePerUnit,
            int extraRolls,
            UnitRollType payloadRollType,
            List<DiceHelper.Die> dice,
            int hits,
            DieRollSource source) {
        diceRolled += dice.size();
        unitRolls.add(new CombatRollPayload.UnitRoll(
                unitModel.getId(),
                unitModel.getAsyncId(),
                unitModel.getBaseType(),
                unitModel.getName(),
                getDisplayedUnitName(unitModel),
                unitModel.getUnitEmoji().toString(),
                unitCount,
                dicePerUnit,
                extraRolls,
                toHit,
                modifierToHit,
                toHit - modifierToHit,
                payloadRollType,
                toDieRolls(dice, source),
                hits));
    }

    public CombatRollPayload build(int displayedTotalHits, int misses, int maximumHits) {
        return new CombatRollPayload(
                null,
                notes,
                modifiers,
                unitRolls,
                new CombatRollPayload.RollTotal(diceRolled, displayedTotalHits, misses, maximumHits));
    }

    private static Integer getCombatRound(
            Player player, Game game, Tile tile, UnitHolder combatOnHolder, CombatRollType rollType) {
        if (rollType != CombatRollType.combatround) return null;
        String key = "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
        String storedRound = game.getStoredValue(key);
        return storedRound.isBlank() ? null : Integer.parseInt(storedRound);
    }

    private void addNote(CombatRollPayload.CombatRollNote note) {
        if (note != null) notes.add(note);
    }

    private List<CombatRollPayload.DieRoll> toDieRolls(List<DiceHelper.Die> dice, DieRollSource source) {
        if (dice.isEmpty()) return List.of();
        return dice.stream()
                .map(die -> new CombatRollPayload.DieRoll(die.getResult(), die.getThreshold(), die.isSuccess(), source))
                .toList();
    }

    private String getDisplayedUnitName(UnitModel unitModel) {
        if (unitModel.getUpgradesFromUnitId().isPresent()
                || unitModel.getFaction().isPresent()) {
            return unitModel.getName();
        }
        return "";
    }

    private String resolveScopeDisplay(CombatModifierModel modifier, Map<UnitModel, Integer> units) {
        String unitScope = modifier.getScope();
        if (isBlank(unitScope)) return "all";
        return units.keySet().stream()
                .filter(unit -> unit.getAsyncId().equals(unitScope))
                .findFirst()
                .map(unit -> unit.getUnitEmoji().toString())
                .orElse(unitScope);
    }
}
