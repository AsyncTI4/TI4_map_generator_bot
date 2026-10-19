package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TransitDiodesRules {

    public static final Set<String> TRANSIT_DIODES = Set.of("td", "absol_td");
    static final String MECHANIZE_THE_MILITARY = "mtm";
    static final String OCCUPY_THE_FRINGE = "otf";
    static final int MECHANIZED_PLANETS = 4;
    static final int FRINGE_GROUND_FORCES = 9;
    static final int MOVED_UNITS = 4;
    private static final String KEY = "transitDiodes|";
    private static final String EXHAUST_PREFIX = "exhaustTech_";
    private static final String PLANET_PREFIX = "transitDiodes_";
    private static final String MOVE_PREFIX = "mercerMove_";
    private static final String DONE_HANDLER = "deleteButtons";
    private static final String MECH = "mech";
    private static final String INFANTRY = "infantry";
    private static final String CHOSEN = "chosen";
    private static final String OPEN = "open";
    private static final String CLOSING = "closing";
    private static final String ITEM = ";";
    private static final String FIELD = "~";
    private static final Set<String> UNPLACEABLE = Set.of("custodia", "ghoti", "ocean", "bannerhall");

    record Transfer(String position, String holder, String unit, String planet) {
        String encode() {
            return String.join(FIELD, position, holder, unit, planet);
        }

        static Transfer decode(String text) {
            String[] parts = StringUtils.splitPreserveAllTokens(text, FIELD);
            return new Transfer(parts[0], parts[1], parts[2], parts[3]);
        }

        String handler() {
            return MOVE_PREFIX + planet + "_" + position + "_" + holder + "_" + unit;
        }
    }

    public static Optional<AiDecision> start(AiTurnContext context, List<AiPrompt> thisTurn) {
        Player seat = context.seat();
        String key = KEY + context.turnKey();
        if (context.memory().has(key)) return Optional.empty();
        Optional<String> diodes = readyDiodes(seat);
        if (diodes.isEmpty()) return Optional.empty();
        List<Transfer> transfers = plan(context.game(), seat);
        if (transfers.isEmpty()) return Optional.empty();
        Optional<Prompts.Match> exhaust =
                Prompts.owned(thisTurn, context.faction(), (EXHAUST_PREFIX + diodes.get())::equals);
        if (exhaust.isEmpty()) return Optional.empty();
        context.memory().put(key, state(OPEN, transfers));
        return Optional.of(
                exhaust.get().press("exhaust Transit Diodes to move ground forces for " + secretName(transfers)));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        String key = KEY + context.turnKey();
        String[] state = StringUtils.split(context.memory().get(key).orElse(""), "|", 2);
        if (state == null || state.length == 0 || CLOSING.equals(state[0])) return close(context, key, state);
        if (!OPEN.equals(state[0]) && !CHOSEN.equals(state[0])) return Optional.empty();
        List<Transfer> transfers = decode(state.length > 1 ? state[1] : "");
        if (transfers.isEmpty()) {
            context.memory().put(key, CLOSING);
            return close(context, key, new String[] {CLOSING});
        }
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts());
        Transfer transfer = transfers.getFirst();
        if (OPEN.equals(state[0])) {
            Optional<Prompts.Match> planet =
                    Prompts.first(visible, button -> button.handlerId().equals(PLANET_PREFIX + transfer.planet()));
            planet.ifPresent(match -> context.memory().put(key, state(CHOSEN, transfers)));
            return planet.map(match -> match.press("move ground forces to " + transfer.planet()));
        }
        Optional<Prompts.Match> move =
                Prompts.first(visible, button -> button.handlerId().equals(transfer.handler()));
        if (move.isEmpty()) {
            context.memory().put(key, CLOSING);
            return close(context, key, new String[] {CLOSING});
        }
        List<Transfer> rest = transfers.subList(1, transfers.size());
        boolean samePlanet = !rest.isEmpty() && rest.getFirst().planet().equals(transfer.planet());
        context.memory().put(key, rest.isEmpty() ? CLOSING : state(samePlanet ? CHOSEN : OPEN, rest));
        return Optional.of(move.get().press("move a " + transfer.unit() + " to " + transfer.planet()));
    }

    private static Optional<AiDecision> close(AiTurnContext context, String key, String[] state) {
        if (state == null || state.length == 0 || !CLOSING.equals(state[0])) return Optional.empty();
        Optional<Prompts.Match> done = Prompts.newestFirst(context.prompts()).stream()
                .filter(TransitDiodesRules::diodesPrompt)
                .flatMap(prompt -> Prompts.in(prompt, button -> DONE_HANDLER.equals(button.handlerId()))
                        .filter(button -> !context.alreadyPressed(prompt, button))
                        .map(button -> new Prompts.Match(prompt, button))
                        .stream())
                .findFirst();
        if (done.isEmpty()) {
            context.memory().put(key, "done");
            return Optional.empty();
        }
        return Optional.of(done.get().press("finish Transit Diodes"));
    }

    private static boolean diodesPrompt(AiPrompt prompt) {
        return prompt.buttons().stream()
                .anyMatch(button -> button.handlerId().startsWith(PLANET_PREFIX)
                        || button.handlerId().startsWith(MOVE_PREFIX));
    }

    static Optional<String> readyDiodes(Player seat) {
        return TRANSIT_DIODES.stream().filter(seat::hasTechReady).findFirst();
    }

    static List<Transfer> plan(Game game, Player seat) {
        List<Transfer> mechanize = mechanize(game, seat);
        List<Transfer> occupy = occupy(game, seat);
        if (completesMechanize(game, seat, mechanize)) return mechanize;
        if (completesOccupy(game, seat, occupy)) return occupy;
        return mechanize.isEmpty() ? occupy : mechanize;
    }

    private static List<Transfer> mechanize(Game game, Player seat) {
        if (!seat.getSecretsUnscored().containsKey(MECHANIZE_THE_MILITARY)) return List.of();
        List<Planet> bare = placeable(game, seat).stream()
                .filter(planet -> BoardView.count(planet, seat, UnitType.Mech) == 0)
                .toList();
        List<Transfer> transfers = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                int spare = BoardView.count(holder, seat, UnitType.Mech) - (holder instanceof Planet ? 1 : 0);
                for (int i = 0; i < spare && transfers.size() < Math.min(MOVED_UNITS, bare.size()); i++) {
                    Planet target = bare.get(transfers.size());
                    transfers.add(new Transfer(tile.getPosition(), holder.getName(), MECH, target.getName()));
                }
            }
        }
        return transfers;
    }

    private static List<Transfer> occupy(Game game, Player seat) {
        if (!seat.getSecretsUnscored().containsKey(OCCUPY_THE_FRINGE)) return List.of();
        Optional<Planet> fringe = placeable(game, seat).stream()
                .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .max(Comparator.comparingInt(planet -> BoardView.groundForces(planet, seat)));
        if (fringe.isEmpty()) return List.of();
        Planet target = fringe.get();
        int wanted = Math.min(MOVED_UNITS, FRINGE_GROUND_FORCES - BoardView.groundForces(target, seat));
        List<Transfer> transfers = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (holder.getName().equals(target.getName())) continue;
                boolean planet = holder instanceof Planet;
                int infantry = BoardView.count(holder, seat, UnitType.Infantry);
                int spare = planet ? Math.min(infantry, BoardView.groundForces(holder, seat) - 1) : infantry;
                for (int i = 0; i < spare && transfers.size() < wanted; i++) {
                    transfers.add(new Transfer(tile.getPosition(), holder.getName(), INFANTRY, target.getName()));
                }
            }
        }
        return transfers;
    }

    private static boolean completesMechanize(Game game, Player seat, List<Transfer> transfers) {
        return !transfers.isEmpty() && mechPlanets(game, seat) + transfers.size() >= MECHANIZED_PLANETS;
    }

    private static boolean completesOccupy(Game game, Player seat, List<Transfer> transfers) {
        if (transfers.isEmpty()) return false;
        UnitHolder target = game.getUnitHolderFromPlanet(transfers.getFirst().planet());
        return BoardView.groundForces(target, seat) + transfers.size() >= FRINGE_GROUND_FORCES;
    }

    static int mechPlanets(Game game, Player seat) {
        return (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> BoardView.count(planet, seat, UnitType.Mech) > 0)
                .count();
    }

    private static List<Planet> placeable(Game game, Player seat) {
        List<Planet> planets = new ArrayList<>();
        for (String name : seat.getPlanetsAllianceMode()) {
            if (UNPLACEABLE.stream().anyMatch(name.toLowerCase()::contains)) continue;
            if (game.getUnitHolderFromPlanet(name) instanceof Planet planet && !planet.isSpaceStation(game)) {
                planets.add(planet);
            }
        }
        return planets;
    }

    private static String secretName(List<Transfer> transfers) {
        return MECH.equals(transfers.getFirst().unit()) ? "Mechanize the Military" : "Occupy the Fringe";
    }

    private static String state(String stage, List<Transfer> transfers) {
        return stage + "|"
                + String.join(ITEM, transfers.stream().map(Transfer::encode).toList());
    }

    private static List<Transfer> decode(String text) {
        if (text.isBlank()) return List.of();
        List<Transfer> transfers = new ArrayList<>();
        for (String item : StringUtils.split(text, ITEM)) transfers.add(Transfer.decode(item));
        return transfers;
    }
}
