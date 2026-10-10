package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.secrets.ActionSecretRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.combat.BombardmentService;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class BombardmentRules {

    static final String BOMBARD_KEY = "bombarded|";
    private static final String ROLL = "combatRoll_%s_space_bombardment";
    private static final String CONFIRM = "bombardConfirm_" + ROLL;
    private static final String DONE_ASSIGNING = ROLL + "_deleteTheseButtons";
    private static final String HIT_PROMPT = "getDamageButtons_%s_bombardment";
    private static final String CONFIRMING = "confirming";
    private static final String ROLLED = "rolled";
    private static final long CONFIRM_WAIT_MILLIS = 30_000L;
    private static final long AI_HITS_WAIT_MILLIS = 20_000L;
    private static final long HITS_WAIT_MILLIS = 120_000L;
    private static final String PLASMA_SCORING = "ps";

    public record Target(String planet, Player defender) {}

    public static Optional<Target> target(Game game, Player seat, Tile tile, Map<UnitType, Integer> ships) {
        if (dice(seat, ships) == 0) return Optional.empty();
        for (String name : BombardmentService.getBombardablePlanets(seat, game, tile)) {
            if (!(tile.getUnitHolders().get(name) instanceof Planet planet)) continue;
            Optional<Player> defender = game.getRealPlayers().stream()
                    .filter(other -> other != seat && BoardView.groundForces(planet, other) > 0)
                    .findFirst();
            if (defender.isEmpty()) continue;
            if (shielded(game, seat, planet, ships)) return Optional.empty();
            return Optional.of(new Target(name, defender.get()));
        }
        return Optional.empty();
    }

    public static int expectedHits(Player seat, Map<UnitType, Integer> ships) {
        double expected = 0;
        for (double chance : dieChances(seat, ships)) expected += chance;
        return (int) Math.floor(expected) * hitMultiplier(seat);
    }

    private static int hitMultiplier(Player seat) {
        return seat.hasTech(CombatForces.X89_DOUBLING) ? 2 : 1;
    }

    public static double chanceToDestroyAll(Player seat, Map<UnitType, Integer> ships, Planet planet, Player defender) {
        int needed = Math.ceilDiv(
                BoardView.groundForces(planet, defender) + BoardView.undamaged(planet, defender, UnitType.Mech),
                hitMultiplier(seat));
        double[] hits = {1.0};
        for (double chance : dieChances(seat, ships)) {
            double[] next = new double[hits.length + 1];
            for (int count = 0; count < hits.length; count++) {
                next[count] += hits[count] * (1 - chance);
                next[count + 1] += hits[count] * chance;
            }
            hits = next;
        }
        double enough = 0;
        for (int count = needed; count < hits.length; count++) enough += hits[count];
        return enough;
    }

    public static Optional<AiDecision> beforeLanding(AiTurnContext context, List<AiPrompt> turn, Tile tile) {
        String key = BOMBARD_KEY + TacticalRules.actionKey(context) + "|" + tile.getPosition();
        Optional<String> state = context.memory().get(key);
        if (state.isPresent()) return afterPress(context, tile, key, state.get());
        Player seat = context.seat();
        Optional<Target> target = target(context.game(), seat, tile, BoardView.ships(BoardView.space(tile), seat));
        if (target.isEmpty()) return Optional.empty();
        String position = tile.getPosition();
        Optional<Match> roll = Prompts.first(
                turn,
                button -> button.isUnowned()
                        && (button.handlerId().equals(ROLL.formatted(position))
                                || button.handlerId().equals(CONFIRM.formatted(position))));
        if (roll.isEmpty()) return Optional.empty();
        boolean confirming = roll.get().button().handlerId().startsWith("bombardConfirm_");
        Planet planet = (Planet) tile.getUnitHolders().get(target.get().planet());
        context.memory()
                .put(
                        key,
                        String.join(
                                "|",
                                confirming ? CONFIRMING : ROLLED,
                                String.valueOf(context.now()),
                                target.get().planet(),
                                target.get().defender().getFaction(),
                                String.valueOf(health(planet, target.get().defender()))));
        ActionSecretRules.watchBombardment(
                context, position, target.get().planet(), target.get().defender());
        return Optional.of(roll.get().press("bombard " + target.get().planet()));
    }

    private static Optional<AiDecision> afterPress(AiTurnContext context, Tile tile, String key, String state) {
        String[] fields = StringUtils.split(state, "|");
        if (fields.length != 5) return Optional.empty();
        long since = Long.parseLong(fields[1]);
        if (CONFIRMING.equals(fields[0])) return confirm(context, tile, key, fields, since);
        Player defender = context.game().getPlayerFromColorOrFaction(fields[3]);
        if (!(tile.getUnitHolders().get(fields[2]) instanceof Planet planet) || defender == null) {
            return Optional.empty();
        }
        boolean hitsPending = visible(context).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since)
                .anyMatch(prompt -> prompt.enabledHandler(HIT_PROMPT.formatted(tile.getPosition()))
                        .isPresent());
        long waitUntil = since + (AiSeats.isAiSeat(defender) ? AI_HITS_WAIT_MILLIS : HITS_WAIT_MILLIS);
        boolean unassigned = health(planet, defender) == Integer.parseInt(fields[4]);
        if (hitsPending && unassigned && context.now() < waitUntil) {
            return Optional.of(new AiDecision.Wait(waitUntil, "the bombardment hits to be assigned"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> confirm(
            AiTurnContext context, Tile tile, String key, String[] fields, long since) {
        Optional<Match> done = Prompts.first(
                visible(context),
                button ->
                        button.isUnowned() && button.handlerId().equals(DONE_ASSIGNING.formatted(tile.getPosition())));
        if (done.isPresent()) {
            fields[0] = ROLLED;
            fields[1] = String.valueOf(context.now());
            context.memory().put(key, String.join("|", fields));
            return Optional.of(done.get().press("roll bombardment against " + fields[2]));
        }
        if (context.now() < since + CONFIRM_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(since + CONFIRM_WAIT_MILLIS, "the bombardment assignment"));
        }
        fields[0] = ROLLED;
        context.memory().put(key, String.join("|", fields));
        return Optional.empty();
    }

    private static List<AiPrompt> visible(AiTurnContext context) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
    }

    private static int health(Planet planet, Player defender) {
        return BoardView.groundForces(planet, defender) + BoardView.undamaged(planet, defender, UnitType.Mech);
    }

    private static boolean shielded(Game game, Player seat, Planet planet, Map<UnitType, Integer> ships) {
        boolean shieldsDown = ships.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> seat.getUnitByType(entry.getKey()))
                .anyMatch(model -> model != null && model.getDisablesPlanetaryShield());
        if (shieldsDown) return false;
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (UnitKey key : planet.getUnitKeysForPlayer(other)) {
                UnitModel model = other.getUnitFromUnitKey(key);
                if (model != null && model.getPlanetaryShield()) return true;
            }
        }
        return false;
    }

    private static int dice(Player seat, Map<UnitType, Integer> ships) {
        return dieChances(seat, ships).size();
    }

    private static List<Double> dieChances(Player seat, Map<UnitType, Integer> ships) {
        List<Double> chances = new ArrayList<>();
        double best = 0;
        for (Map.Entry<UnitType, Integer> entry : ships.entrySet()) {
            UnitModel model = seat.getUnitByType(entry.getKey());
            if (model == null || entry.getValue() <= 0) continue;
            int dice = model.getCombatDieCountForAbility(CombatRollType.bombardment, seat);
            if (dice <= 0) continue;
            double chance = hitChance(model.getCombatDieHitsOnForAbility(CombatRollType.bombardment, seat));
            best = Math.max(best, chance);
            for (int die = 0; die < dice * entry.getValue(); die++) chances.add(chance);
        }
        if (!chances.isEmpty() && seat.hasTech(PLASMA_SCORING)) chances.add(best);
        return chances;
    }

    private static double hitChance(int hitsOn) {
        return Math.clamp((11 - hitsOn) / 10.0, 0, 1);
    }
}
