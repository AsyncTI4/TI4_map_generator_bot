package ti4.service.combat.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

/** Defines the immutable requests, contexts, and results passed between combat roll stages. */
public final class CombatV2RollData {
    private CombatV2RollData() {}

    /** Raw inputs from the Discord interaction. Normalization happens once during combat setup. */
    public record Request(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName) {}

    /** Bombardment assignment facts shared by filtering and per-unit modifier calculation. */
    public record BombardmentModifiers(
            boolean assignmentsPresent, Set<String> sourceIds, Map<String, Integer> galvanizedByUnit) {
        public BombardmentModifiers {
            sourceIds = Set.copyOf(sourceIds);
            galvanizedByUnit = Map.copyOf(galvanizedByUnit);
        }

        public static BombardmentModifiers empty() {
            return new BombardmentModifiers(false, Set.of(), Map.of());
        }

        public boolean hasSource(String sourceId) {
            return sourceIds.contains(sourceId);
        }

        public int galvanizedCount(String unitId) {
            return galvanizedByUnit.getOrDefault(unitId, 0);
        }
    }

    /** A registry rule that passed roll-level eligibility and is ready to be applied to units. */
    public record ResolvedModifier(
            CombatV2Modifiers.Rule rule, String displayName, CombatModifierActivation activation) {
        public String ruleId() {
            return rule.id();
        }
    }

    public record Context(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated,
            UnitHolder combatHolder,
            Player opponent,
            Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits,
            Map<UnitModel, Integer> opponentUnits,
            List<ResolvedModifier> resolvedModifiers,
            BombardmentModifiers bombardmentModifiers,
            List<String> notices) {
        public Context {
            rollingUnits = Map.copyOf(rollingUnits);
            opponentUnits = Map.copyOf(opponentUnits);
            resolvedModifiers = List.copyOf(resolvedModifiers);
            notices = List.copyOf(notices);
        }

        public MessageChannel messageChannel() {
            return event.getMessageChannel();
        }

        public String getFaction() {
            return player.getFaction();
        }

        public String getColor() {
            return player.getColor();
        }

        public String getFactionEmoji() {
            return player.getFactionEmoji();
        }

        public String getColorId() {
            return player.getColorID();
        }

        public String getTilePosition() {
            return tile.getPosition();
        }

        public String getTileId() {
            return tile.getTileID();
        }

        public boolean isFowMode() {
            return game.isFowMode();
        }

        public String storedValue(String key) {
            return game.getStoredValue(key);
        }

        public UnitHolder unitHolderFromPlanet(String planet) {
            return game.getUnitHolderFromPlanet(planet);
        }

        public String holderName() {
            return combatHolder.getName();
        }

        public String tileRepresentation() {
            return tile().getRepresentation();
        }

        public String rollTypeName() {
            return rollType().getValue();
        }

        public String opponentFaction() {
            return opponent.getFaction();
        }

        public String opponentColor() {
            return opponent.getColor();
        }

        public boolean opponentHasTech(String tech) {
            return opponent.hasTech(tech);
        }

        public boolean opponentIsDummyOrNpc() {
            return opponent.isDummy() || opponent.isNpc();
        }

        public boolean playerHasTech(String tech) {
            return player.hasTech(tech);
        }

        public boolean playerHasUnit(String unit) {
            return player.hasUnit(unit);
        }

        public boolean playerHasAbility(String ability) {
            return player.hasAbility(ability);
        }

        public boolean playerHasBreakthrough(String breakthrough) {
            return player.hasBreakthrough(breakthrough);
        }

        public boolean playerHasUnlockedBreakthrough(String breakthrough) {
            return player.hasUnlockedBreakthrough(breakthrough);
        }

        public boolean playerHasPromissoryNote(String note) {
            return player.getPromissoryNotes().containsKey(note);
        }

        public MessageChannel playerChannel() {
            return player.getCorrectChannel();
        }

        public MessageChannel playerPrivateChannel() {
            return player.getPrivateChannel();
        }

        public MessageChannel opponentChannel() {
            return opponent.getCorrectChannel();
        }

        public boolean isPrivateFowRoll() {
            return isFowMode() && messageChannel().equals(playerPrivateChannel());
        }

        public String gameName() {
            return game().getName();
        }

        public List<Player> realPlayers() {
            return game.getRealPlayers();
        }

        public List<Player> playersAndNeutral() {
            return game.getRealPlayersNNeutral();
        }

        public String factionButtonChecker() {
            return player.factionButtonChecker();
        }

        public Set<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>> rollingUnitEntries() {
            return rollingUnits.entrySet();
        }

        public Set<UnitModel> rollingUnitModels() {
            return rollingUnitsFlat().keySet();
        }

        public Map<UnitModel, Integer> rollingUnitsFlat() {
            Map<UnitModel, Integer> flat = new LinkedHashMap<>();
            rollingUnits.forEach((unit, count) -> flat.merge(unit.getLeft(), count, Integer::sum));
            return Map.copyOf(flat);
        }

        public boolean playerHasLeaderUnlockedOrAlliance(String leader) {
            return game.playerHasLeaderUnlockedOrAlliance(player, leader);
        }
    }

    public record Round(int rollingSideRound, int opponentRound, String displayName) {}

    public record Resolution(
            Context context,
            Round round,
            String message,
            ti4.service.combat.v2.CombatV2DiceData.RollResult roll,
            ti4.contest.replay.core.CombatRollPayload payload) {
        public int hits() {
            return roll.totalHits();
        }
    }
}
