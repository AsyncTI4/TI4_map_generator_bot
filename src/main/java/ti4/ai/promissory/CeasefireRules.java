package ti4.ai.promissory;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class CeasefireRules {

    private static final String CEASEFIRE = "_cf";
    private static final String GIVEN_AWAY_KEY = "ceasefireGivenAway|";
    private static final String GIVEN_AWAY = "yes";
    private static final String GRACE_KEY = "ceasefireGrace|";
    private static final String HAND_SCOPE = "ceasefire";
    private static final String ACTION_SUMMARY = "currentActionSummary";
    private static final String MOVED = " Moved ships there.";
    private static final String DID_NOT_MOVE = " Did not move units.";
    private static final String ACTION_PHASE = "action";
    private static final long HOLDER_GRACE_MILLIS = 120_000L;

    public static boolean blocksMovement(AiTurnContext context) {
        Player seat = context.seat();
        if (!context.isActivePlayer() || !holdsOwn(seat) || !context.memory().has(GIVEN_AWAY_KEY + context.turnKey())) {
            return false;
        }
        Tile tile = activeTile(context.game());
        return tile != null && !othersWithUnits(context.game(), seat, tile).isEmpty();
    }

    public static double blockChance(Game game, Player seat, Tile tile) {
        if (!givenAway(seat)) return 0;
        long others =
                game.getRealPlayers().stream().filter(player -> player != seat).count();
        if (others == 0) return 0;
        return (double) othersWithUnits(game, seat, tile).size() / others;
    }

    static void observe(AiTurnContext context) {
        if (context.isActivePlayer() && givenAway(context.seat())) {
            context.memory().put(GIVEN_AWAY_KEY + context.turnKey(), GIVEN_AWAY);
        }
    }

    static Optional<AiDecision> playAsHolder(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        Player active = game.getActivePlayer();
        if (active == null || active == seat || !ACTION_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            return Optional.empty();
        }
        String note = active.getColor() + CEASEFIRE;
        if (!NoteHand.holdsForeign(seat, note) || !awaitingMovement(game, active)) return Optional.empty();
        Tile tile = activeTile(game);
        if (tile == null || !FoWHelper.playerHasUnitsInSystem(seat, tile) || !canMoveInto(game, active, tile)) {
            return Optional.empty();
        }
        return NoteHand.play(
                context,
                note,
                HAND_SCOPE,
                "play Ceasefire: " + active.getFaction() + " cannot move into " + tile.getPosition());
    }

    static Optional<AiDecision> holdForHolder(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!context.isActivePlayer()
                || !givenAway(seat)
                || !awaitingMovement(game, seat)
                || !game.getTacticalActionDisplacement().isEmpty()
                || !plansToMove(context)) {
            return Optional.empty();
        }
        Tile tile = activeTile(game);
        if (tile == null || othersWithUnits(game, seat, tile).stream().allMatch(AiSeats::isAiSeat)) {
            return Optional.empty();
        }
        String key = GRACE_KEY + context.turnKey();
        long since = context.memory()
                .get(key)
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong)
                .orElse(context.now());
        context.memory().put(key, String.valueOf(since));
        long until = since + HOLDER_GRACE_MILLIS;
        if (context.now() >= until) return Optional.empty();
        return Optional.of(new AiDecision.Wait(until, "a Ceasefire window"));
    }

    private static boolean givenAway(Player seat) {
        String note = seat.getColor() + CEASEFIRE;
        return seat.ownsPromissoryNote(note) && !seat.getPromissoryNotes().containsKey(note);
    }

    private static boolean holdsOwn(Player seat) {
        return seat.getPromissoryNotes().containsKey(seat.getColor() + CEASEFIRE);
    }

    private static List<Player> othersWithUnits(Game game, Player seat, Tile tile) {
        return game.getRealPlayers().stream()
                .filter(player -> player != seat && FoWHelper.playerHasUnitsInSystem(player, tile))
                .toList();
    }

    private static boolean plansToMove(AiTurnContext context) {
        return TacticalRules.rememberedPlan(context)
                .map(plan -> !plan.moves().isEmpty())
                .orElse(true);
    }

    private static boolean awaitingMovement(Game game, Player active) {
        String summary = game.getStoredValue(ACTION_SUMMARY + active.getFaction());
        return TacticalRules.inProgress(game, active) && !summary.contains(MOVED) && !summary.contains(DID_NOT_MOVE);
    }

    @Nullable
    private static Tile activeTile(Game game) {
        String position = game.getCurrentActiveSystem();
        return StringUtils.isBlank(position) ? null : game.getTileByPosition(position);
    }

    private static boolean canMoveInto(Game game, Player mover, Tile target) {
        for (Tile origin : game.getTileMap().values()) {
            if (origin == target || origin.hasPlayerCC(mover)) continue;
            int move = fastestShip(mover, origin);
            if (move > 0
                    && MovementGraph.reach(game, mover, origin.getPosition(), move)
                            .containsKey(target.getPosition())) {
                return true;
            }
        }
        return false;
    }

    private static int fastestShip(Player mover, Tile origin) {
        return BoardView.ships(BoardView.space(origin), mover).keySet().stream()
                .filter(type -> type != UnitType.Fighter)
                .mapToInt(type -> BoardView.moveValue(mover, type))
                .max()
                .orElse(0);
    }
}
