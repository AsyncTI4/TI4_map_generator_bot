package ti4.service.combat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatV2DiceData.RollPlan;

/** Defines the immutable requests, contexts, and results passed between combat roll stages. */
public final class CombatV2RollData {
    private CombatV2RollData() {}

    public record Request(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName) {

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

        public UnitHolder unitHolder() {
            return tile.getUnitHolders().get(unitHolderName);
        }

        public UnitHolder spaceHolder() {
            return tile.getSpaceUnitHolder();
        }

        public List<Planet> planetHolders() {
            return tile.getPlanetUnitHolders();
        }

        public TileModel tileModel() {
            return tile.getTileModel();
        }

        public String storedValue(String key) {
            return game.getStoredValue(key);
        }

        public boolean storedValueIsEmpty(String key) {
            return storedValue(key).isEmpty();
        }

        public void setStoredValue(String key, String value) {
            game.setStoredValue(key, value);
        }

        public void removeStoredValue(String key) {
            game.removeStoredValue(key);
        }

        public UnitHolder unitHolderFromPlanet(String planet) {
            return game.getUnitHolderFromPlanet(planet);
        }

        public List<Player> realPlayers() {
            return game.getRealPlayers();
        }

        public List<Player> playersAndNeutral() {
            return game.getRealPlayersNNeutral();
        }

        public List<Player> otherPlayers() {
            return game.getRealPlayersExcludingThis(player);
        }

        public boolean anyRealPlayer(Predicate<Player> predicate) {
            return realPlayers().stream().anyMatch(predicate);
        }

        public List<String> playerPlanets() {
            return player.getPlanetsAllianceMode();
        }

        public boolean playerControlsPlanet(String planet) {
            return playerPlanets().contains(planet);
        }

        public Player playerOwningPlanet(String planet) {
            return playersAndNeutral().stream()
                    .filter(candidate -> candidate.getPlanets().contains(planet))
                    .findFirst()
                    .orElse(null);
        }

        public boolean playerHasUnit(String unit) {
            return player.hasUnit(unit);
        }

        public boolean playerHasTech(String tech) {
            return player.hasTech(tech);
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

        public boolean playerHasActiveBreakthrough(String breakthrough) {
            return player.hasActiveBreakthrough(breakthrough);
        }

        public boolean playerHasPromissoryNote(String note) {
            return player.getPromissoryNotes().containsKey(note);
        }

        public boolean playerHasLeaderUnlockedOrAlliance(String leader) {
            return game.playerHasLeaderUnlockedOrAlliance(player, leader);
        }

        public MessageChannel playerChannel() {
            return player.getCorrectChannel();
        }

        public MessageChannel playerPrivateChannel() {
            return player.getPrivateChannel();
        }

        public String factionButtonChecker() {
            return player.factionButtonChecker();
        }
    }

    public enum ModifierKind {
        HIT_MODIFIER,
        EXTRA_DIE,
        TEMPORARY
    }

    public record RuleReference(String type, String alias) {}

    /** The complete modifier contribution for one unit roll. */
    public record UnitModifiers(int toHit, int extraDice) {}

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

    /** One resolved rule with its role made explicit for later roll stages and diagnostics. */
    public record AppliedModifier(
            ModifierKind kind,
            String ruleId,
            String condition,
            String scope,
            List<RuleReference> sources,
            String displayName,
            CombatModifierModel rule) {
        public AppliedModifier {
            sources = List.copyOf(sources);
        }

        public static AppliedModifier from(ModifierKind kind, NamedCombatModifierModel modifier) {
            var rule = modifier.getModifier();
            return new AppliedModifier(
                    kind,
                    rule.getAlias(),
                    rule.getCondition(),
                    rule.getScope(),
                    rule.getRelated().stream()
                            .map(related -> new RuleReference(related.getType(), related.getAlias()))
                            .toList(),
                    modifier.getName(),
                    rule);
        }
    }

    public record Modifiers(
            List<AppliedModifier> applied, BombardmentModifiers bombardment, Map<String, Long> sharedScalingValues) {
        public Modifiers {
            applied = List.copyOf(applied);
            sharedScalingValues = Map.copyOf(sharedScalingValues);
        }

        public static Modifiers of(
                List<NamedCombatModifierModel> hitModifiers,
                List<NamedCombatModifierModel> extraRolls,
                List<NamedCombatModifierModel> temporaryModifiers,
                BombardmentModifiers bombardment,
                Map<String, Long> sharedScalingValues) {
            java.util.ArrayList<AppliedModifier> applied = new java.util.ArrayList<>();
            hitModifiers.forEach(modifier -> applied.add(AppliedModifier.from(ModifierKind.HIT_MODIFIER, modifier)));
            extraRolls.forEach(modifier -> applied.add(AppliedModifier.from(ModifierKind.EXTRA_DIE, modifier)));
            temporaryModifiers.forEach(modifier -> applied.add(AppliedModifier.from(ModifierKind.TEMPORARY, modifier)));
            return new Modifiers(applied, bombardment, sharedScalingValues);
        }
    }

    public record Context(
            Request request,
            CombatRollType rollType,
            boolean automated,
            UnitHolder combatHolder,
            Player opponent,
            Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits,
            Map<UnitModel, Integer> rollingUnitsFlat,
            Map<UnitModel, Integer> opponentUnits,
            Modifiers modifiers,
            List<String> notices) {
        public Context {
            rollingUnits = Map.copyOf(rollingUnits);
            rollingUnitsFlat = Map.copyOf(rollingUnitsFlat);
            opponentUnits = Map.copyOf(opponentUnits);
            notices = List.copyOf(notices);
        }

        public Player player() {
            return request.player();
        }

        public Game game() {
            return request.game();
        }

        public GenericInteractionCreateEvent event() {
            return request.event();
        }

        public MessageChannel messageChannel() {
            return request.messageChannel();
        }

        public Tile tile() {
            return request.tile();
        }

        public String unitHolderName() {
            return request.unitHolderName();
        }

        public String getFaction() {
            return request.getFaction();
        }

        public String getColor() {
            return request.getColor();
        }

        public String getFactionEmoji() {
            return request.getFactionEmoji();
        }

        public String getColorId() {
            return request.getColorId();
        }

        public String getTilePosition() {
            return request.getTilePosition();
        }

        public String getTileId() {
            return request.getTileId();
        }

        public boolean isFowMode() {
            return request.isFowMode();
        }

        public String storedValue(String key) {
            return request.storedValue(key);
        }

        public void setStoredValue(String key, String value) {
            request.setStoredValue(key, value);
        }

        public void removeStoredValue(String key) {
            request.removeStoredValue(key);
        }

        public UnitHolder unitHolderFromPlanet(String planet) {
            return request.unitHolderFromPlanet(planet);
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
            return request.playerHasTech(tech);
        }

        public boolean playerHasUnit(String unit) {
            return request.playerHasUnit(unit);
        }

        public boolean playerHasAbility(String ability) {
            return request.playerHasAbility(ability);
        }

        public boolean playerHasBreakthrough(String breakthrough) {
            return request.playerHasBreakthrough(breakthrough);
        }

        public boolean playerHasUnlockedBreakthrough(String breakthrough) {
            return request.playerHasUnlockedBreakthrough(breakthrough);
        }

        public boolean playerHasPromissoryNote(String note) {
            return request.playerHasPromissoryNote(note);
        }

        public MessageChannel playerChannel() {
            return request.playerChannel();
        }

        public MessageChannel playerPrivateChannel() {
            return request.playerPrivateChannel();
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
            return request.realPlayers();
        }

        public List<Player> playersAndNeutral() {
            return request.playersAndNeutral();
        }

        public String factionButtonChecker() {
            return request.factionButtonChecker();
        }

        public Set<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>> rollingUnitEntries() {
            return rollingUnits.entrySet();
        }

        public Set<UnitModel> rollingUnitModels() {
            return rollingUnitsFlat.keySet();
        }

        public List<AppliedModifier> appliedModifiers() {
            return modifiers.applied();
        }

        public boolean hasAppliedCondition(String condition) {
            return appliedModifiers().stream().anyMatch(applied -> condition.equals(applied.condition()));
        }

        public boolean playerHasLeaderUnlockedOrAlliance(String leader) {
            return request.playerHasLeaderUnlockedOrAlliance(leader);
        }
    }

    public sealed interface ContextResult permits PreparedRoll, Rejected {}

    public record PreparedRoll(Context context, RollPlan plan) implements ContextResult {}

    public record Rejected(String message) implements ContextResult {}

    public record Round(int rollingSideRound, int opponentRound, String displayName) {}

    /** A named, auditable rule effect applied after the dice are evaluated. */
    public record Effect(String source, int hitDelta, String message) {}

    public record Resolution(
            Context context,
            Round round,
            String message,
            int rolledHits,
            int hits,
            boolean whiff,
            boolean slam,
            ti4.contest.replay.core.CombatRollPayload payload,
            List<Effect> effects) {
        public Resolution {
            effects = List.copyOf(effects);
        }
    }
}
