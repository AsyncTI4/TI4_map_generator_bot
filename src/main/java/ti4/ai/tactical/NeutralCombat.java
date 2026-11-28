package ti4.ai.tactical;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class NeutralCombat {

    static final String NEUTRAL = "neutral";
    private static final String ROLL_PREFIX = "combatRoll_";
    private static final String AFB_SUFFIX = "_afb";
    private static final String AFB_HITS = "autoAssignAFBHits_";
    private static final int ROUND_ROLL_PARTS = 3;
    private static final int AFB_PARTS = 4;
    private static final Pattern SYSTEM_POSITION = Pattern.compile("-system-([^-]+)-turn-");
    private static final List<String> ASSIGN_PREFIXES = List.of(
            "autoAssignSpaceHits_",
            AFB_HITS,
            "autoAssignSpaceCannonOffenceHits_",
            AutoAssignGroundHitsButtonIds.PREFIX);

    public static boolean watches(Game game, Player seat, String threadName) {
        Player active = game.getActivePlayer();
        if (game.isFowMode() || !threadName.contains(NEUTRAL) || !AiSeats.isActiveAiSeat(active)) return false;
        if (active == seat || !threadName.contains(active.getFaction())) return false;
        return roller(game, active).filter(seat::equals).isPresent() && fights(game, threadName);
    }

    static Optional<Player> roller(Game game, Player active) {
        List<Player> seats = game.getRealPlayers();
        int at = seats.indexOf(active);
        for (int step = 1; step < seats.size(); step++) {
            Player next = seats.get((at + step) % seats.size());
            if (AiSeats.isActiveAiSeat(next)) return Optional.of(next);
        }
        return Optional.empty();
    }

    static boolean barrages(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        Player neutral = game.getPlayerFromColorOrFaction(NEUTRAL);
        if (tile == null || neutral == null) return false;
        UnitHolder space = BoardView.space(tile);
        boolean fighters = BoardView.count(space, seat, UnitType.Fighter) > 0;
        return fighters
                && BoardView.ships(space, neutral).keySet().stream().anyMatch(type -> hasBarrage(neutral, type));
    }

    private static boolean hasBarrage(Player neutral, UnitType type) {
        var model = neutral.getUnitByType(type);
        return model != null && model.getAfbDieCount(neutral) > 0;
    }

    private static boolean fights(Game game, String threadName) {
        Matcher matcher = SYSTEM_POSITION.matcher(threadName);
        Player neutral = game.getPlayerFromColorOrFaction(NEUTRAL);
        Tile tile = matcher.find() ? game.getTileByPosition(matcher.group(1)) : null;
        if (tile == null || neutral == null || !threadName.contains("-round-" + game.getRound() + "-")) return false;
        return tile.getUnitHolders().values().stream().anyMatch(holder -> hasUnits(holder, neutral));
    }

    private static boolean hasUnits(UnitHolder holder, Player player) {
        return !holder.getUnitKeysForPlayer(player).isEmpty();
    }

    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player seat = context.seat();
        Player active = game.getActivePlayer();
        if (!AiSeats.isActiveAiSeat(active)) return Optional.empty();
        Optional<Player> roller = roller(game, active);
        boolean standIn = seat == active;
        if (!standIn && roller.filter(seat::equals).isEmpty()) return Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<AiDecision> decision = pressFor(context, active, prompt, standIn);
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> pressFor(
            AiTurnContext context, Player active, AiPrompt prompt, boolean standIn) {
        for (PromptButton button : prompt.enabledButtons()) {
            if (!button.isOwnedBy(NEUTRAL) || context.alreadyPressed(prompt, button)) continue;
            Optional<String> reason = reasonToPress(context, active, prompt, button);
            if (reason.isEmpty()) continue;
            if (!standIn || standInMayPress(context, prompt)) {
                return Optional.of(AiDecision.press(prompt, button, reason.get()));
            }
        }
        return Optional.empty();
    }

    private static boolean standInMayPress(AiTurnContext context, AiPrompt prompt) {
        Player active = context.game().getActivePlayer();
        long grace = roller(context.game(), active).isEmpty()
                ? CombatRules.AI_ROLL_GRACE_MILLIS
                : CombatRules.ROLL_GRACE_MILLIS;
        return context.now() >= prompt.createdAtMillis() + grace;
    }

    private static Optional<String> reasonToPress(
            AiTurnContext context, Player active, AiPrompt prompt, PromptButton button) {
        String id = button.handlerId();
        if (ASSIGN_PREFIXES.stream().anyMatch(id::startsWith)) return assignment(context, active, prompt, id);
        if (!id.startsWith(ROLL_PREFIX)) return Optional.empty();
        String[] parts = id.split("_");
        if (parts.length == AFB_PARTS && id.endsWith(AFB_SUFFIX)) return barrage(context, active, prompt, parts[1]);
        if (parts.length == ROUND_ROLL_PARTS) return roll(context, active, parts[1], parts[2]);
        return Optional.empty();
    }

    private static Optional<String> assignment(AiTurnContext context, Player active, AiPrompt prompt, String id) {
        Game game = context.game();
        String[] combat = combatOfAssignment(game, id);
        boolean waitsForRoll = combat != null
                && !id.startsWith(AFB_HITS)
                && CombatRules.tracker(game, NEUTRAL, combat[0], combat[1])
                        < CombatRules.tracker(game, active.getFaction(), combat[0], combat[1])
                && neutralHasUnits(game, combat[0], combat[1])
                && context.now() < prompt.createdAtMillis() + CombatRules.ROLL_GRACE_MILLIS;
        return waitsForRoll ? Optional.empty() : Optional.of("assign the neutral units' hits");
    }

    @Nullable
    private static String[] combatOfAssignment(Game game, String id) {
        if (id.startsWith(AutoAssignGroundHitsButtonIds.PREFIX)) {
            String planet = AutoAssignGroundHitsButtonIds.parse(id).planetName();
            Tile tile = game.getTileFromPlanet(planet);
            return tile == null ? null : new String[] {tile.getPosition(), planet};
        }
        String[] parts = id.split("_");
        return parts.length < 2 ? null : new String[] {parts[1], BoardView.SPACE};
    }

    private static Optional<String> barrage(AiTurnContext context, Player active, AiPrompt prompt, String position) {
        String key = "neutralBarrage|" + prompt.messageId() + "|" + position;
        if (context.memory().has(key) || !barrages(context.game(), active, position)) return Optional.empty();
        if (CombatRules.tracker(context.game(), active.getFaction(), position, BoardView.SPACE) > 0) {
            return Optional.empty();
        }
        context.memory().put(key, "pressed");
        return Optional.of("fire the neutral units' anti-fighter barrage");
    }

    private static Optional<String> roll(AiTurnContext context, Player active, String position, String holder) {
        Game game = context.game();
        if (!neutralHasUnits(game, position, holder)) return Optional.empty();
        int theirs = CombatRules.tracker(game, NEUTRAL, position, holder);
        int ours = CombatRules.tracker(game, active.getFaction(), position, holder);
        return theirs < ours ? Optional.of("roll the neutral units' dice") : Optional.empty();
    }

    private static boolean neutralHasUnits(Game game, String position, String holderName) {
        Tile tile = game.getTileByPosition(position);
        Player neutral = game.getPlayerFromColorOrFaction(NEUTRAL);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(holderName);
        return holder != null && neutral != null && hasUnits(holder, neutral);
    }
}
