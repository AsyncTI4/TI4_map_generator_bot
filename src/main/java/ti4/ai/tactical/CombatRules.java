package ti4.ai.tactical;

import java.util.List;
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
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.secrets.ActionSecretRules;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class CombatRules {

    static final long ROLL_GRACE_MILLIS = 120_000L;
    static final long AI_ROLL_GRACE_MILLIS = 10_000L;
    private static final String ROLL_PREFIX = "combatRoll_";
    private static final String ASSIGN_PREFIX = "autoAssign";
    private static final String AFB_SUFFIX = "_afb";
    private static final String SPACE_CANNON_OFFENCE_SUFFIX = "_spacecannonoffence";
    private static final String SPACE_CANNON_DEFENCE_SUFFIX = "_spacecannondefence";
    private static final List<String> ROUND_HIT_PREFIXES =
            List.of("autoAssignSpaceHits_", "autoAssignSpaceCannonOffenceHits_", AutoAssignGroundHitsButtonIds.PREFIX);

    public static Optional<AiDecision> next(AiTurnContext context) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        Optional<AiDecision> spaceCannonDefence = spaceCannonDefence(context, prompts);
        if (spaceCannonDefence.isPresent()) return spaceCannonDefence;
        Optional<AiDecision> assignment = assignHits(context, prompts);
        if (assignment.isPresent()) return assignment;
        Optional<Match> structures =
                Prompts.owned(prompts, context.faction(), id -> id.startsWith("removeAllStructures_"));
        if (structures.isPresent()) return Optional.of(structures.get().press("remove its lost structures"));
        Optional<Match> automate = Prompts.owned(
                prompts, context.faction(), id -> id.startsWith("automateGroundCombat_") && id.endsWith("_confirmed"));
        if (automate.isPresent()) return Optional.of(automate.get().press("automate ground combat"));
        Optional<AiDecision> spaceCannon = spaceCannonOffence(context, prompts);
        if (spaceCannon.isPresent()) return spaceCannon;
        Optional<AiDecision> afb = antiFighterBarrage(context, prompts);
        if (afb.isPresent()) return afb;
        return combatRound(context, prompts);
    }

    public static boolean awaitsSeat(Game game, Player seat, List<AiPrompt> prompts, AiPrompt prompt) {
        if (prompt.source() != PromptSource.COMBAT_THREAD) return false;
        return prompt.enabledButtons().stream()
                .anyMatch(button -> button.isOwnedBy(seat.getFaction())
                        || (isRoundRoll(button) && rollAllowed(game, seat, prompts, button) != RollTiming.NOT_NOW));
    }

    public static boolean isRollFor(Game game, Player seat, List<AiPrompt> prompts, PromptButton button) {
        if (!button.isUnowned() || !button.handlerId().startsWith(ROLL_PREFIX)) return false;
        return isRoundRoll(button) && rollAllowed(game, seat, prompts, button) != RollTiming.NOT_NOW;
    }

    private static Optional<AiDecision> assignHits(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> assign = prompt.firstEnabled(button ->
                    button.isOwnedBy(context.faction()) && button.handlerId().startsWith(ASSIGN_PREFIX));
            if (assign.isEmpty()) continue;
            if (isRoundHit(assign.get())) {
                Optional<Match> ownRoll = Prompts.first(
                        prompts,
                        button -> isRoundRoll(button)
                                && sameCombat(assign.get(), button)
                                && behind(context.game(), context.seat(), button));
                if (ownRoll.isPresent()) return Optional.of(ownRoll.get().press("roll its dice before taking hits"));
            }
            return Optional.of(AiDecision.press(prompt, assign.get(), "assign hits"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> spaceCannonDefence(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        if (game.getActivePlayer() == context.seat()) return Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : rolls(prompt, SPACE_CANNON_DEFENCE_SUFFIX)) {
                String[] parts = roll.handlerId().split("_");
                if (parts.length != 4) continue;
                Tile tile = game.getTileByPosition(parts[1]);
                UnitHolder planet = tile == null ? null : tile.getUnitHolders().get(parts[2]);
                String key = "scd|" + prompt.messageId() + "|" + parts[2];
                if (planet == null || context.memory().has(key)) continue;
                if (BoardView.count(planet, context.seat(), UnitType.Pds) == 0) continue;
                context.memory().put(key, "pressed");
                return Optional.of(AiDecision.press(prompt, roll, "fire space cannon defense"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> spaceCannonOffence(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player active = game.getActivePlayer();
        if (active == null || active == context.seat()) return Optional.empty();
        long turnStart = Prompts.turnStart(context);
        for (AiPrompt prompt : prompts) {
            if (prompt.createdAtMillis() < turnStart) continue;
            for (PromptButton roll : rolls(prompt, SPACE_CANNON_OFFENCE_SUFFIX)) {
                String position = positionOf(roll);
                String key = "spaceCannon|" + prompt.messageId() + "|" + position;
                if (context.memory().has(key)) continue;
                if (!ButtonHelper.getPlayersWithPds2Cover(active, game, position)
                        .contains(context.seat())) continue;
                context.memory().put(key, "pressed");
                ActionSecretRules.watchSpaceCannon(context, position);
                return Optional.of(AiDecision.press(prompt, roll, "fire space cannon"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> antiFighterBarrage(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : rolls(prompt, AFB_SUFFIX)) {
                String position = positionOf(roll);
                Tile tile = context.game().getTileByPosition(position);
                String key = "afb|" + prompt.messageId() + "|" + position;
                if (tile == null
                        || context.memory().has(key)
                        || tracker(context, context.faction(), position, "space") > 0) {
                    continue;
                }
                if (!hasBarrage(context.seat(), tile) || !enemyFighters(context.game(), context.seat(), tile)) continue;
                context.memory().put(key, "pressed");
                return Optional.of(AiDecision.press(prompt, roll, "fire anti-fighter barrage"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> combatRound(AiTurnContext context, List<AiPrompt> prompts) {
        Optional<AiDecision> waiting = Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : prompt.enabledButtons()) {
                if (!isRoundRoll(roll)) continue;
                RollTiming timing = rollAllowed(context, roll);
                if (timing == RollTiming.NOW) {
                    return Optional.of(AiDecision.press(prompt, roll, "roll a combat round"));
                }
                if (timing != RollTiming.AFTER_GRACE) continue;
                long grace = onlyAiOpponents(context.game(), context.seat(), roll)
                        ? AI_ROLL_GRACE_MILLIS
                        : ROLL_GRACE_MILLIS;
                long readyAt = newestInChannel(prompts, prompt.channelId()) + grace;
                if (context.now() >= readyAt) {
                    return Optional.of(AiDecision.press(prompt, roll, "roll a combat round"));
                }
                if (waiting.isEmpty())
                    waiting = Optional.of(new AiDecision.Wait(readyAt, "giving the opponent a moment"));
            }
        }
        return waiting;
    }

    private static List<PromptButton> rolls(AiPrompt prompt, String suffix) {
        return prompt.enabledButtons().stream()
                .filter(button -> button.isUnowned()
                        && button.handlerId().startsWith(ROLL_PREFIX)
                        && button.handlerId().endsWith(suffix))
                .toList();
    }

    enum RollTiming {
        NOW,
        AFTER_GRACE,
        NOT_NOW
    }

    static RollTiming rollAllowed(AiTurnContext context, PromptButton roll) {
        return rollAllowed(context.game(), context.seat(), context.prompts(), roll);
    }

    private static RollTiming rollAllowed(Game game, Player seat, List<AiPrompt> prompts, PromptButton roll) {
        Optional<Combat> combat = combatOf(game, seat, roll);
        if (combat.isEmpty()) return RollTiming.NOT_NOW;
        int mine = combat.get().mine();
        int theirs = combat.get().theirs();
        if (mine < theirs) return RollTiming.NOW;
        if (mine > theirs || hitsPending(prompts, roll)) return RollTiming.NOT_NOW;
        List<Player> opponents = combat.get().opponents();
        if (opponents.stream().allMatch(AiSeats::isAiSeat)
                && mine == 0
                && game.getActivePlayer() != seat
                && opponents.contains(game.getActivePlayer())) {
            return RollTiming.NOT_NOW;
        }
        return RollTiming.AFTER_GRACE;
    }

    private record Combat(List<Player> opponents, int mine, int theirs) {}

    private static Optional<Combat> combatOf(Game game, Player seat, PromptButton roll) {
        String[] parts = roll.handlerId().split("_");
        if (parts.length != 3) return Optional.empty();
        String position = parts[1];
        String holderName = parts[2];
        Tile tile = game.getTileByPosition(position);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(holderName);
        if (holder == null) return Optional.empty();
        List<Player> opponents = opponentsOn(game, seat, tile, holder);
        if (opponents.isEmpty() || !participates(seat, tile, holder)) return Optional.empty();
        int mine = tracker(game, seat.getFaction(), position, holderName);
        int theirs = opponents.stream()
                .mapToInt(opponent -> tracker(game, opponent.getFaction(), position, holderName))
                .max()
                .orElse(0);
        return Optional.of(new Combat(opponents, mine, theirs));
    }

    private static boolean behind(Game game, Player seat, PromptButton roll) {
        return combatOf(game, seat, roll)
                .map(combat -> combat.mine() < combat.theirs())
                .orElse(false);
    }

    private static boolean onlyAiOpponents(Game game, Player seat, PromptButton roll) {
        return combatOf(game, seat, roll)
                .map(combat -> combat.opponents().stream().allMatch(AiSeats::isAiSeat))
                .orElse(false);
    }

    private static boolean hitsPending(List<AiPrompt> prompts, PromptButton roll) {
        return prompts.stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .flatMap(prompt -> prompt.enabledButtons().stream())
                .anyMatch(button -> isRoundHit(button) && sameCombat(button, roll));
    }

    private static boolean isRoundHit(PromptButton button) {
        return ROUND_HIT_PREFIXES.stream().anyMatch(button.handlerId()::startsWith);
    }

    private static boolean sameCombat(PromptButton assignment, PromptButton roll) {
        String[] parts = roll.handlerId().split("_");
        if (parts.length != 3) return false;
        String id = assignment.handlerId();
        if (id.startsWith(AutoAssignGroundHitsButtonIds.PREFIX)) {
            return id.startsWith(AutoAssignGroundHitsButtonIds.PREFIX + parts[2] + "_");
        }
        return BoardView.SPACE.equals(parts[2]) && id.contains("_" + parts[1] + "_");
    }

    private static boolean isRoundRoll(PromptButton button) {
        return button.isUnowned()
                && button.handlerId().startsWith(ROLL_PREFIX)
                && button.handlerId().split("_").length == 3;
    }

    private static boolean participates(Player seat, Tile tile, UnitHolder holder) {
        if (BoardView.SPACE.equals(holder.getName())) return BoardView.hasOwnShips(seat, tile);
        return BoardView.groundForces(holder, seat) > 0;
    }

    private static List<Player> opponentsOn(Game game, Player seat, Tile tile, UnitHolder holder) {
        boolean space = BoardView.SPACE.equals(holder.getName());
        return game.getPlayers().values().stream()
                .filter(other -> other != seat && other.getColor() != null)
                .filter(other -> space ? BoardView.hasOwnShips(other, tile) : BoardView.groundForces(holder, other) > 0)
                .toList();
    }

    static int tracker(AiTurnContext context, String faction, String position, String holder) {
        return tracker(context.game(), faction, position, holder);
    }

    private static int tracker(Game game, String faction, String position, String holder) {
        String value = game.getStoredValue("combatRoundTracker" + faction + position + holder);
        return StringUtils.isNumeric(value) ? Integer.parseInt(value) : 0;
    }

    private static boolean hasBarrage(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        return BoardView.MOVING_SHIPS.stream()
                .filter(type -> BoardView.count(space, seat, type) > 0)
                .map(seat::getUnitByType)
                .anyMatch(model -> model != null && model.getAfbDieCount(seat) > 0);
    }

    private static boolean enemyFighters(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        return game.getPlayers().values().stream()
                .filter(other -> other != seat && other.getColor() != null)
                .anyMatch(other -> BoardView.count(space, other, UnitType.Fighter) > 0);
    }

    private static long newestInChannel(List<AiPrompt> prompts, String channelId) {
        return prompts.stream()
                .filter(prompt -> prompt.channelId().equals(channelId))
                .mapToLong(AiPrompt::createdAtMillis)
                .max()
                .orElse(0L);
    }

    static String positionOf(PromptButton roll) {
        String[] parts = roll.handlerId().split("_");
        return parts.length > 1 ? parts[1] : "";
    }
}
