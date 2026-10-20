package ti4.ai.promissory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Units.UnitType;

@UtilityClass
class CombatNotes {

    private static final String ROLL_PREFIX = "combatRoll_";
    private static final String ROUND_TRACKER = "combatRoundTracker";
    private static final int ROUND_ROLL_PARTS = 3;
    private static final String ANTIVIRUS = "antivirus";
    private static final String TEKKLAR_LEGION = "tekklar";
    private static final String GREYFIRE_MUTAGEN = "greyfire";
    private static final String GREYFIRE_KEY = "greyfirePlayed";
    private static final String COMBAT_SCOPE = "combat|";
    private static final String FIELD = "~";
    private static final int GREYFIRE_FIELDS = 2;
    private static final int GREYFIRE_MIN_GROUND_FORCES = 2;
    private static final int OVERWHELMING_RATIO = 3;
    private static final long TARGET_WAIT_MILLIS = 60_000L;
    private static final long TARGET_POLL_MILLIS = 3_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    record Combat(Tile tile, UnitHolder holder, Player opponent, int mine, int theirs) {

        boolean ground() {
            return !BoardView.SPACE.equals(holder.getName());
        }

        boolean notStarted() {
            return mine == 0 && theirs == 0;
        }

        String scope() {
            return COMBAT_SCOPE + tile.getPosition() + "|" + holder.getName();
        }
    }

    static Optional<AiDecision> play(AiTurnContext context) {
        for (Combat combat : combats(context)) {
            if (!combat.notStarted()) continue;
            Optional<AiDecision> decision = antivirus(context, combat)
                    .or(() -> greyfire(context, combat))
                    .or(() -> tekklar(context, combat));
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    static Optional<AiDecision> chooseGreyfireTarget(AiTurnContext context) {
        String[] played = StringUtils.splitPreserveAllTokens(
                context.memory().get(GREYFIRE_KEY).orElse(""), FIELD);
        if (played == null || played.length != GREYFIRE_FIELDS || !StringUtils.isNumeric(played[0])) {
            return Optional.empty();
        }
        if (NoteHand.holdsForeign(context.seat(), GREYFIRE_MUTAGEN)) {
            context.memory().remove(GREYFIRE_KEY);
            return Optional.empty();
        }
        long since = Long.parseLong(played[0]);
        String planet = played[1];
        Optional<Match> target = greyfireTarget(context, planet, since - CLOCK_SKEW_MILLIS);
        if (target.isPresent()) {
            context.memory().remove(GREYFIRE_KEY);
            return Optional.of(target.get().press("replace an enemy infantry on " + planet + " with Greyfire Mutagen"));
        }
        long until = since + TARGET_WAIT_MILLIS;
        if (context.now() >= until) {
            context.memory().remove(GREYFIRE_KEY);
            return Optional.empty();
        }
        return Optional.of(new AiDecision.Wait(
                Math.min(until, context.now() + TARGET_POLL_MILLIS), "its Greyfire Mutagen target"));
    }

    private static Optional<AiDecision> antivirus(AiTurnContext context, Combat combat) {
        Game game = context.game();
        Player seat = context.seat();
        Player nekro = game.getPNOwner(ANTIVIRUS);
        if (nekro != combat.opponent() || !NoteHand.holdsForeign(seat, ANTIVIRUS)) return Optional.empty();
        if (ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(nekro, seat, new ArrayList<>(), game)
                .isEmpty()) {
            return Optional.empty();
        }
        return NoteHand.play(context, ANTIVIRUS, combat.scope(), "play Antivirus so Nekro cannot copy its technology");
    }

    private static Optional<AiDecision> tekklar(AiTurnContext context, Combat combat) {
        Player seat = context.seat();
        if (!combat.ground() || !NoteHand.holdsForeign(seat, TEKKLAR_LEGION)) return Optional.empty();
        boolean againstOwner = context.game().getPNOwner(TEKKLAR_LEGION) == combat.opponent();
        int ours = BoardView.groundForces(combat.holder(), seat);
        int theirs = BoardView.groundForces(combat.holder(), combat.opponent());
        if (!againstOwner && ours >= OVERWHELMING_RATIO * theirs) return Optional.empty();
        return NoteHand.play(
                context, TEKKLAR_LEGION, combat.scope(), "play Tekklar Legion for +1 on its ground combat rolls");
    }

    private static Optional<AiDecision> greyfire(AiTurnContext context, Combat combat) {
        Player opponent = combat.opponent();
        UnitHolder planet = combat.holder();
        if (!combat.ground() || !NoteHand.holdsForeign(context.seat(), GREYFIRE_MUTAGEN)) return Optional.empty();
        if (opponent == context.game().getPNOwner(GREYFIRE_MUTAGEN)
                || BoardView.groundForces(planet, opponent) < GREYFIRE_MIN_GROUND_FORCES
                || BoardView.count(planet, opponent, UnitType.Infantry) == 0) {
            return Optional.empty();
        }
        Optional<Match> hand = NoteHand.button(context, GREYFIRE_MUTAGEN);
        if (hand.isEmpty()) return NoteHand.requestHand(context, combat.scope());
        context.memory().put(GREYFIRE_KEY, String.join(FIELD, String.valueOf(context.now()), planet.getName()));
        return Optional.of(hand.get().press("play Greyfire Mutagen on " + planet.getName()));
    }

    private static Optional<Match> greyfireTarget(AiTurnContext context, String planet, long since) {
        String handlerId = PromissoryRules.GREYFIRE_TARGET + planet;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isUnowned()
                    && handlerId.equals(candidate.handlerId())
                    && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }

    private static List<Combat> combats(AiTurnContext context) {
        List<Combat> combats = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiPrompt prompt : context.prompts()) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                combatOf(context.game(), context.seat(), button)
                        .filter(combat -> seen.add(combat.scope()))
                        .ifPresent(combats::add);
            }
        }
        return combats;
    }

    private static Optional<Combat> combatOf(Game game, Player seat, PromptButton roll) {
        if (!roll.isUnowned() || !roll.handlerId().startsWith(ROLL_PREFIX)) return Optional.empty();
        String[] parts = roll.handlerId().split("_");
        if (parts.length != ROUND_ROLL_PARTS || !parts[1].equals(game.getActiveSystem())) return Optional.empty();
        Tile tile = game.getTileByPosition(parts[1]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[2]);
        if (holder == null || !present(seat, tile, holder)) return Optional.empty();
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && present(other, tile, holder))
                .findFirst()
                .map(opponent -> new Combat(
                        tile,
                        holder,
                        opponent,
                        rounds(game, seat, parts[1], parts[2]),
                        rounds(game, opponent, parts[1], parts[2])));
    }

    private static boolean present(Player player, Tile tile, UnitHolder holder) {
        if (BoardView.SPACE.equals(holder.getName())) return BoardView.hasOwnShips(player, tile);
        return BoardView.groundForces(holder, player) > 0;
    }

    private static int rounds(Game game, Player player, String position, String holder) {
        String value = game.getStoredValue(ROUND_TRACKER + player.getFaction() + position + holder);
        return StringUtils.isNumeric(value) ? Integer.parseInt(value) : 0;
    }
}
