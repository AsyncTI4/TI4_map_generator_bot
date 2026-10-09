package ti4.ai.secrets;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.promissory.PlayAreaNotes;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class ActionSecretRules {

    private static final String WATCH_KEY = "combatWatch|";
    private static final String OPEN_KEY = "combatWatchOpen|";
    private static final String RESOLVED_KEY = "combatWatchResolved|";
    private static final String ROUND_TRACKER = "combatRoundTracker";
    private static final String ENTRY = ";";
    private static final String LEDGER_KEY = "actionSecretScored|";
    private static final String PENDING_KEY = "actionSecretPending|";
    private static final String HOME_PLANETS_KEY = "homePlanetsHeld|";
    private static final String MARTYR = "bam";
    private static final String DUST = "ttfd";
    private static final String CANNON_KEY = "cannonWatch|";
    private static final String FIELD = "~";
    private static final int DEMONSTRATION_SHIPS = 3;
    private static final List<String> COMBAT_SECRETS = List.of("dtgs", "uf", "dts", "btv", "sar", "baf", "fwp", "dyp");

    record Watch(
            String key,
            String position,
            String holder,
            String opponent,
            boolean opponentLeads,
            boolean flagship,
            int enemyHeavy,
            int enemyHeavyOnBoard,
            int enemyFighters,
            boolean anomaly,
            boolean otherHome,
            boolean activeSystem,
            boolean betrayable,
            boolean fightersCleared) {

        boolean space() {
            return BoardView.SPACE.equals(holder);
        }

        String encode() {
            return String.join(
                    FIELD,
                    key,
                    position,
                    holder,
                    opponent,
                    flag(opponentLeads),
                    flag(flagship),
                    String.valueOf(enemyHeavy),
                    String.valueOf(enemyHeavyOnBoard),
                    String.valueOf(enemyFighters),
                    flag(anomaly),
                    flag(otherHome),
                    flag(activeSystem),
                    flag(betrayable),
                    flag(fightersCleared));
        }

        Watch withFightersCleared() {
            return new Watch(
                    key,
                    position,
                    holder,
                    opponent,
                    opponentLeads,
                    flagship,
                    enemyHeavy,
                    enemyHeavyOnBoard,
                    enemyFighters,
                    anomaly,
                    otherHome,
                    activeSystem,
                    betrayable,
                    true);
        }

        static Optional<Watch> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 14) return Optional.empty();
            try {
                return Optional.of(new Watch(
                        fields[0],
                        fields[1],
                        fields[2],
                        fields[3],
                        "1".equals(fields[4]),
                        "1".equals(fields[5]),
                        Integer.parseInt(fields[6]),
                        Integer.parseInt(fields[7]),
                        Integer.parseInt(fields[8]),
                        "1".equals(fields[9]),
                        "1".equals(fields[10]),
                        "1".equals(fields[11]),
                        "1".equals(fields[12]),
                        "1".equals(fields[13])));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        private static String flag(boolean value) {
            return value ? "1" : "0";
        }
    }

    public static void watchSpaceCannon(AiTurnContext context, String position) {
        Game game = context.game();
        Player target = game.getActivePlayer();
        Tile tile = game.getTileByPosition(position);
        if (target == null
                || tile == null
                || !context.seat().getSecretsUnscored().containsKey(DUST)) return;
        int ships = BoardView.nonFighterShips(BoardView.space(tile), target);
        boolean othersFire = ButtonHelper.getPlayersWithPds2Cover(target, game, position).stream()
                .anyMatch(shooter -> shooter != context.seat() && shooter != target);
        if (ships > 0 && !othersFire) {
            context.memory()
                    .put(CANNON_KEY + game.getRound(), context.turnKey() + "|" + position + "|" + target.getFaction());
        }
    }

    private static void watchCannonResult(AiTurnContext context, Set<String> held) {
        if (!held.contains(DUST)) return;
        Game game = context.game();
        String cannonKey = CANNON_KEY + game.getRound();
        Optional<String> watch = context.memory().get(cannonKey);
        if (watch.isEmpty()) return;
        String[] parts = watch.get().split("\\|");
        if (parts.length < 3) {
            context.memory().remove(cannonKey);
            return;
        }
        String position = parts[parts.length - 2];
        Player target = game.getPlayerFromColorOrFaction(parts[parts.length - 1]);
        Tile tile = game.getTileByPosition(position);
        if (target == null || tile == null || !watch.get().startsWith(context.turnKey())) {
            context.memory().remove(cannonKey);
            return;
        }
        if (BoardView.nonFighterShips(BoardView.space(tile), target) > 0) return;
        context.memory().remove(cannonKey);
        if (rounds(game, target, position, BoardView.SPACE) > 0) return;
        queue(context, List.of(DUST), "cannon|" + context.turnKey() + "|" + position);
    }

    public static Optional<AiDecision> observe(AiTurnContext context) {
        if (!"action".equalsIgnoreCase(context.game().getPhaseOfGame())) return Optional.empty();
        Set<String> held = heldActionSecrets(context.seat());
        if (held.isEmpty()) return Optional.empty();
        watchCombats(context, held);
        watchHomePlanets(context, held);
        watchCannonResult(context, held);
        return Optional.empty();
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!"action".equalsIgnoreCase(context.game().getPhaseOfGame())) return Optional.empty();
        if (heldActionSecrets(context.seat()).isEmpty()) return Optional.empty();
        observe(context);
        return scorePending(context);
    }

    static Set<String> heldActionSecrets(Player seat) {
        Set<String> held = new LinkedHashSet<>();
        for (String secret : seat.getSecretsUnscored().keySet()) {
            if (SecretPhase.of(secret) == SecretPhase.ACTION) held.add(secret);
        }
        return held;
    }

    private static List<String> pendingEntries(AiTurnContext context) {
        String pending =
                context.memory().get(PENDING_KEY + context.game().getRound()).orElse("");
        return new ArrayList<>(List.of(StringUtils.split(pending, ENTRY)));
    }

    private static void savePending(AiTurnContext context, List<String> entries) {
        String key = PENDING_KEY + context.game().getRound();
        if (entries.isEmpty()) {
            context.memory().remove(key);
        } else {
            context.memory().put(key, String.join(ENTRY, entries));
        }
    }

    private static void queue(AiTurnContext context, List<String> met, String combat) {
        if (met.isEmpty() || context.memory().has(LEDGER_KEY + combat)) return;
        List<String> entries = pendingEntries(context);
        if (entries.stream()
                .anyMatch(entry -> StringUtils.substringAfter(entry, "|").equals(combat))) return;
        Set<String> queued = new LinkedHashSet<>();
        entries.forEach(entry -> queued.add(StringUtils.substringBefore(entry, "|")));
        Optional<String> secret =
                met.stream().filter(candidate -> !queued.contains(candidate)).findFirst();
        if (secret.isEmpty()) return;
        entries.add(secret.get() + "|" + combat);
        savePending(context, entries);
    }

    private static Optional<AiDecision> scorePending(AiTurnContext context) {
        List<String> entries = pendingEntries(context);
        if (entries.isEmpty()) return Optional.empty();
        List<String> remaining = new ArrayList<>();
        Optional<AiDecision> decision = Optional.empty();
        for (String entry : entries) {
            String secret = StringUtils.substringBefore(entry, "|");
            String combat = StringUtils.substringAfter(entry, "|");
            if (!context.seat().getSecretsUnscored().containsKey(secret)
                    || context.memory().has(LEDGER_KEY + combat)) {
                continue;
            }
            if (decision.isEmpty()) {
                decision = SecretScoring.score(context, secret, "score " + secret + " after a combat");
                if (decision.filter(ActionSecretRules::isScorePress).isPresent()) {
                    context.memory().put(LEDGER_KEY + combat, secret);
                    continue;
                }
            }
            remaining.add(entry);
        }
        savePending(context, remaining);
        return decision;
    }

    private static boolean isScorePress(AiDecision decision) {
        return decision instanceof AiDecision.Press press
                && press.button().handlerId().startsWith(Constants.SO_SCORE_FROM_HAND);
    }

    private static void watchCombats(AiTurnContext context, Set<String> held) {
        Game game = context.game();
        Player seat = context.seat();
        String openKey = OPEN_KEY + game.getRound();
        Set<String> open = new LinkedHashSet<>(
                List.of(StringUtils.split(context.memory().get(openKey).orElse(""), ",")));
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                Player opponent = opponentIn(game, seat, tile, holder);
                if (opponent == null) opponent = foughtOpponent(game, seat, tile, holder);
                if (opponent == null) continue;
                String key = context.turnKey() + "|" + tile.getPosition() + "|" + holder.getName();
                if (open.contains(key) || context.memory().has(RESOLVED_KEY + key)) continue;
                context.memory()
                        .put(
                                WATCH_KEY + key,
                                start(context, key, tile, holder, opponent).encode());
                open.add(key);
            }
        }
        List<String> stillOpen = new ArrayList<>();
        for (String key : open) {
            if (key.isBlank()) continue;
            Optional<Watch> watch = context.memory().get(WATCH_KEY + key).flatMap(Watch::decode);
            if (watch.isEmpty()) continue;
            Tile tile = game.getTileByPosition(watch.get().position());
            UnitHolder holder =
                    tile == null ? null : tile.getUnitHolders().get(watch.get().holder());
            Player opponent = game.getPlayerFromColorOrFaction(watch.get().opponent());
            if (holder == null || opponent == null) {
                context.memory().remove(WATCH_KEY + key);
                continue;
            }
            if (present(seat, tile, holder) && present(opponent, tile, holder)) {
                observe(context, watch.get(), tile, holder, opponent);
                stillOpen.add(key);
                continue;
            }
            context.memory().remove(WATCH_KEY + key);
            context.memory().put(RESOLVED_KEY + key, "1");
            resolve(context, held, watch.get(), tile, holder, opponent);
        }
        context.memory().put(openKey, String.join(",", stillOpen));
    }

    private static Watch start(AiTurnContext context, String key, Tile tile, UnitHolder holder, Player opponent) {
        Game game = context.game();
        Player seat = context.seat();
        boolean space = BoardView.SPACE.equals(holder.getName());
        boolean betrayable = seat == game.getActivePlayer()
                && (seat.getPromissoryNotesInPlayArea().stream().anyMatch(note -> game.getPNOwner(note) == opponent)
                        || PlayAreaNotes.heldAtActionStart(context, opponent));
        return new Watch(
                key,
                tile.getPosition(),
                holder.getName(),
                opponent.getFaction(),
                opponent.getTotalVictoryPoints() == game.getHighestScore(),
                space && BoardView.count(holder, seat, UnitType.Flagship) > 0,
                space ? heavyShips(holder, opponent) : 0,
                heavyShipsOnBoard(game, opponent),
                space ? BoardView.count(holder, opponent, UnitType.Fighter) : 0,
                tile.isAnomaly(game, seat),
                tile.isHomeSystem(game) && tile != seat.getHomeSystemTile(),
                tile.getPosition().equals(game.getActiveSystem()),
                betrayable,
                false);
    }

    private static void observe(AiTurnContext context, Watch watch, Tile tile, UnitHolder holder, Player opponent) {
        if (!watch.space() || watch.fightersCleared() || watch.enemyFighters() == 0) return;
        if (BoardView.count(holder, opponent, UnitType.Fighter) > 0) return;
        Game game = context.game();
        String seatRounds =
                game.getStoredValue("combatRoundTracker" + context.faction() + tile.getPosition() + holder.getName());
        String opponentRounds = game.getStoredValue(
                "combatRoundTracker" + opponent.getFaction() + tile.getPosition() + holder.getName());
        if (seatRounds.isBlank() && opponentRounds.isBlank()) {
            context.memory()
                    .put(WATCH_KEY + watch.key(), watch.withFightersCleared().encode());
        }
    }

    private static void resolve(
            AiTurnContext context, Set<String> held, Watch watch, Tile tile, UnitHolder holder, Player opponent) {
        Game game = context.game();
        Player seat = context.seat();
        boolean fought = rounds(game, seat, watch.position(), watch.holder()) > 0
                && rounds(game, opponent, watch.position(), watch.holder()) > 0;
        boolean won = fought && present(seat, tile, holder) && !present(opponent, tile, holder);
        List<String> met = new ArrayList<>();
        for (String secret : COMBAT_SECRETS) {
            if (held.contains(secret) && conditionMet(game, secret, watch, fought, won, seat, holder, opponent)) {
                met.add(secret);
            }
        }
        queue(context, met, watch.key());
    }

    private static boolean conditionMet(
            Game game,
            String secret,
            Watch watch,
            boolean fought,
            boolean won,
            Player seat,
            UnitHolder holder,
            Player opponent) {
        return switch (secret) {
            case "sar" -> won && watch.opponentLeads();
            case "btv" -> won && watch.anomaly();
            case "dts" -> won && watch.otherHome();
            case "baf" -> won && watch.betrayable();
            case "uf" ->
                watch.space() && won && watch.flagship() && BoardView.count(holder, seat, UnitType.Flagship) > 0;
            case "dyp" ->
                watch.space()
                        && fought
                        && watch.activeSystem()
                        && BoardView.nonFighterShips(holder, seat) >= DEMONSTRATION_SHIPS;
            case "dtgs" ->
                watch.space()
                        && watch.enemyHeavy() > 0
                        && heavyShipsOnBoard(game, opponent) < watch.enemyHeavyOnBoard();
            case "fwp" -> watch.space() && watch.activeSystem() && watch.fightersCleared();
            default -> false;
        };
    }

    private static int rounds(Game game, Player player, String position, String holder) {
        String rounds = game.getStoredValue(ROUND_TRACKER + player.getFaction() + position + holder);
        return StringUtils.isNumeric(rounds) ? Integer.parseInt(rounds) : 0;
    }

    @Nullable
    private static Player foughtOpponent(Game game, Player seat, Tile tile, UnitHolder holder) {
        if (rounds(game, seat, tile.getPosition(), holder.getName()) == 0) return null;
        for (Player other : game.getRealPlayers()) {
            if (other != seat && rounds(game, other, tile.getPosition(), holder.getName()) > 0) return other;
        }
        return null;
    }

    private static int heavyShipsOnBoard(Game game, Player player) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fs")
                + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "ws");
    }

    private static void watchHomePlanets(AiTurnContext context, Set<String> held) {
        if (!held.contains(MARTYR)) return;
        Game game = context.game();
        Player seat = context.seat();
        Set<String> now = new LinkedHashSet<>();
        for (String planet : seat.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null && tile.isHomeSystem(game)) now.add(planet);
        }
        String key = HOME_PLANETS_KEY + context.faction();
        Optional<String> before = context.memory().get(key);
        context.memory().put(key, String.join(",", now));
        if (before.isEmpty()) return;
        for (String planet : StringUtils.split(before.get(), ",")) {
            if (now.contains(planet)) continue;
            queue(context, List.of(MARTYR), "martyr|" + game.getRound() + "|" + planet);
            return;
        }
    }

    @Nullable
    private static Player opponentIn(Game game, Player seat, Tile tile, UnitHolder holder) {
        if (!present(seat, tile, holder)) return null;
        for (Player other : game.getRealPlayers()) {
            if (other != seat && present(other, tile, holder)) return other;
        }
        return null;
    }

    private static boolean present(Player player, Tile tile, UnitHolder holder) {
        if (BoardView.SPACE.equals(holder.getName())) return FoWHelper.playerHasActualShipsInSystem(player, tile);
        return holder instanceof Planet && ButtonHelperModifyUnits.doesPlayerHaveGfOnPlanet(holder, player);
    }

    private static int heavyShips(UnitHolder holder, Player player) {
        return BoardView.count(holder, player, UnitType.Flagship) + BoardView.count(holder, player, UnitType.Warsun);
    }
}
