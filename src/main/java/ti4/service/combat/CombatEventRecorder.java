package ti4.service.combat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

@UtilityClass
public class CombatEventRecorder {

    private static final Set<UnitType> GROUND_FORCES = EnumSet.of(UnitType.Infantry, UnitType.Mech);
    private static final Set<UnitType> SHIPS = EnumSet.of(
            UnitType.Fighter,
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    public static List<GameSubEvent> withOutcomes(Game game, Player actor, List<GameSubEvent> subEvents) {
        List<GameSubEvent> withOutcomes = new ArrayList<>(subEvents);
        for (GameSubEvent subEvent : subEvents) {
            if (subEvent instanceof GameSubEvent.Combat combat) {
                outcomeOf(game, actor, combat).ifPresent(withOutcomes::add);
            }
        }
        return withOutcomes;
    }

    public static void recordLanding(Game game, Player player, Tile tile, String planet, UnitType type, int amount) {
        Map<String, Map<String, Integer>> groundForces = Map.of(planet, Map.of(type.value, amount));
        GameSubEvent.InvasionCommitted landing =
                new GameSubEvent.InvasionCommitted(player.getFaction(), tile.getPosition(), groundForces);
        if (GameEventDraft.stage(game, landing)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("tile", landing.tile());
        payload.put("groundForces", groundForces);
        GameEventService.commit(game, GameEventType.INVASION_COMMITTED, player, payload);
    }

    private static Set<UnitType> fightingUnitTypes(GameSubEvent.Combat combat) {
        return "ground".equals(combat.kind()) ? GROUND_FORCES : SHIPS;
    }

    private static Optional<GameSubEvent.CombatOutcome> outcomeOf(Game game, Player actor, GameSubEvent.Combat combat) {
        Tile tile = game.getTileByPosition(combat.tile());
        if (tile == null) return Optional.empty();
        UnitHolder holder =
                combat.planet() == null ? tile.getSpaceUnitHolder() : tile.getUnitHolderFromPlanet(combat.planet());
        if (holder == null) return Optional.empty();

        Map<String, Map<String, Integer>> survivors = new LinkedHashMap<>();
        for (Player participant : participantsOf(game, actor, combat)) {
            Map<String, Integer> remaining = new LinkedHashMap<>();
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(participant)) {
                if (fightingUnitTypes(combat).contains(unitKey.unitType())) {
                    remaining.merge(unitKey.asyncID(), holder.getUnitCount(unitKey), Integer::sum);
                }
            }
            if (!remaining.isEmpty()) {
                survivors.put(participant.getFaction(), remaining);
            }
        }
        String winner = survivors.size() == 1 ? survivors.keySet().iterator().next() : null;
        return Optional.of(
                new GameSubEvent.CombatOutcome(combat.kind(), combat.tile(), combat.planet(), winner, survivors));
    }

    private static List<Player> participantsOf(Game game, Player actor, GameSubEvent.Combat combat) {
        List<Player> participants = new ArrayList<>();
        participants.add(actor);
        Player opponent = game.getPlayerFromColorOrFaction(combat.vsFaction());
        if (opponent != null && opponent != actor) {
            participants.add(opponent);
        }
        return participants;
    }

    static void recordRoll(
            Game game,
            Player player,
            Player opponent,
            CombatRollType rollType,
            Tile tile,
            UnitHolder unitHolder,
            Map<UnitModel, Integer> units,
            int hits,
            int expectedHitsTimes10) {
        Map<String, Integer> unitCounts = new LinkedHashMap<>();
        units.forEach((unit, count) -> unitCounts.merge(unit.getAlias(), count, Integer::sum));
        String tilePosition = tile == null ? null : tile.getPosition();
        String planet = planetOf(unitHolder);
        String opponentFaction = opponent == null || opponent == player ? null : opponent.getFaction();

        GameSubEvent.CombatRoll roll = new GameSubEvent.CombatRoll(
                rollType.name(),
                tilePosition,
                planet,
                player.getFaction(),
                opponentFaction,
                hits,
                expectedHitsTimes10,
                unitCounts);
        if (GameEventDraft.stage(game, roll)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", roll.kind());
        payload.put("tile", tilePosition);
        payload.put("planet", planet);
        payload.put("vsFaction", opponentFaction);
        payload.put("hits", hits);
        payload.put("expectedHitsTimes10", expectedHitsTimes10);
        payload.put("units", unitCounts);
        GameEventService.commit(game, GameEventType.COMBAT_ROLL, player, payload);
    }

    private static String planetOf(UnitHolder unitHolder) {
        if (unitHolder == null || Constants.SPACE.equals(unitHolder.getName())) {
            return null;
        }
        return unitHolder.getName();
    }
}
