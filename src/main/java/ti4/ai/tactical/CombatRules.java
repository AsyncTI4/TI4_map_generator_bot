package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.actioncards.CombatCardRules;
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
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class CombatRules {

    static final long ROLL_GRACE_MILLIS = 120_000L;
    static final long AI_ROLL_GRACE_MILLIS = 10_000L;
    private static final String ROLL_PREFIX = "combatRoll_";
    private static final String AUTOMATE_PREFIX = "automateGroundCombat_";
    private static final String CONFIRMED_SUFFIX = "_confirmed";
    private static final String ASSIGN_PREFIX = "autoAssign";
    private static final String AFB_SUFFIX = "_afb";
    private static final String AFB_HIT_PREFIX = "autoAssignAFBHits_";
    private static final String SPACE_CANNON_OFFENCE_SUFFIX = "_spacecannonoffence";
    private static final String SPACE_CANNON_DEFENCE_SUFFIX = "_spacecannondefence";
    private static final String ASSAULT_CANNON = "asc";
    private static final String ASSAULT_CANNON_PREFIX = "assCannonNDihmohn_asc_";
    private static final int ASSAULT_CANNON_SHIPS = 3;
    private static final String NEKRO_FLAGSHIP = "nekro_flagship";
    private static final String LATEST_ASSIGN_HITS = "latestAssignHits";
    private static final String ASSAULT_CANNON_HITS = "assaultcannoncombat";
    private static final String ASSIGN_HITS = "assignHits";
    private static final String ASSIGN_DAMAGE = "assignDamage";
    private static final String CANCEL_THE_HIT = "Cancel The Hit";
    private static final String DONE_REMOVING = "deleteButtons";
    private static final double DAMAGED_DISCOUNT = 0.1;
    private static final String MAGEN_HIT_PREFIX = "magenHit_";
    private static final String GRAVITON = "gls";
    private static final String GRAVITON_EXHAUST = "exhaustTech_gls";
    private static final String GROUND_HIT_PREFIX = "hitOpponentGround_";
    private static final List<String> ROUND_HIT_PREFIXES =
            List.of("autoAssignSpaceHits_", "autoAssignSpaceCannonOffenceHits_", AutoAssignGroundHitsButtonIds.PREFIX);

    public static Optional<AiDecision> next(AiTurnContext context) {
        return next(context, CombatCardHook.NONE);
    }

    public static Optional<AiDecision> next(AiTurnContext context, CombatCardHook hook) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        Optional<AiDecision> spaceCannonDefence = spaceCannonDefence(context, prompts);
        if (spaceCannonDefence.isPresent()) return spaceCannonDefence;
        Optional<AiDecision> assaultCannonLoss = destroyShipForAssaultCannon(context, prompts);
        if (assaultCannonLoss.isPresent()) return assaultCannonLoss;
        Optional<AiDecision> singleHit = takeSingleHit(context, prompts);
        if (singleHit.isPresent()) return singleHit;
        Optional<AiDecision> assignment = assignHits(context, prompts);
        if (assignment.isPresent()) return assignment;
        Optional<Match> structures =
                Prompts.owned(prompts, context.faction(), id -> id.startsWith("removeAllStructures_"));
        if (structures.isPresent()) return Optional.of(structures.get().press("remove its lost structures"));
        Optional<AiDecision> groundHit = hitOpposingGroundForce(context, prompts);
        if (groundHit.isPresent()) return groundHit;
        Optional<AiDecision> magen = magenDefenseGrid(context, prompts);
        if (magen.isPresent()) return magen;
        Optional<AiDecision> automation = confirmAutomation(context, prompts, hook);
        if (automation.isPresent()) return automation;
        Optional<AiDecision> spaceCannon = spaceCannonOffence(context, prompts);
        if (spaceCannon.isPresent()) return spaceCannon;
        Optional<AiDecision> assaultCannon = assaultCannon(context, prompts);
        if (assaultCannon.isPresent()) return assaultCannon;
        Optional<AiDecision> combatTech = CombatTechRules.next(context, prompts);
        if (combatTech.isPresent()) return combatTech;
        Optional<AiDecision> afb = antiFighterBarrage(context, prompts);
        if (afb.isPresent()) return afb;
        Optional<AiDecision> retreat = RetreatRules.next(context, prompts);
        if (retreat.isPresent()) return retreat;
        Optional<AiDecision> neutral = NeutralCombat.next(context, prompts);
        if (neutral.isPresent()) return neutral;
        return combatRound(context, prompts, hook);
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
            Optional<PromptButton> assign = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && button.handlerId().startsWith(ASSIGN_PREFIX)
                    && !assignsNothing(button));
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
                if (!holdsSpaceCannon(context.seat(), planet)) continue;
                context.memory().put(key, "pressed");
                return Optional.of(AiDecision.press(prompt, roll, "fire space cannon defense"));
            }
        }
        return Optional.empty();
    }

    private static boolean holdsSpaceCannon(Player seat, UnitHolder planet) {
        boolean units = planet.getUnitKeysForPlayer(seat).stream()
                .map(seat::getUnitFromUnitKey)
                .anyMatch(model -> model != null && model.getSpaceCannonDieCount(seat) > 0);
        return units
                || (planet instanceof Planet held
                        && held.getSpaceCannonDieCount() > 0
                        && seat.getPlanets().contains(held.getName()));
    }

    private static Optional<AiDecision> spaceCannonOffence(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player active = game.getActivePlayer();
        if (active == null) return Optional.empty();
        boolean attacking = active == context.seat();
        long turnStart = Prompts.turnStart(context);
        for (AiPrompt prompt : prompts) {
            if (prompt.createdAtMillis() < turnStart) continue;
            for (PromptButton roll : rolls(prompt, SPACE_CANNON_OFFENCE_SUFFIX)) {
                String position = positionOf(roll);
                String key = spaceCannonWindow(game, active, turnStart, position);
                if (context.memory().has(key)) continue;
                if (attacking && !position.equals(game.getActiveSystem())) continue;
                if (!ButtonHelper.getPlayersWithPds2Cover(active, game, position)
                        .contains(context.seat())) continue;
                Optional<PromptButton> graviton = graviton(context, prompt, game.getTileByPosition(position));
                if (graviton.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, graviton.get(), "exhaust Graviton Laser System"));
                }
                context.memory().put(key, "pressed");
                ActionSecretRules.watchSpaceCannon(context, position);
                CombatCardRules.watchSpaceCannon(context, position);
                String reason = attacking ? "fire space cannon at the defenders" : "fire space cannon";
                return Optional.of(AiDecision.press(prompt, roll, reason));
            }
        }
        return Optional.empty();
    }

    private static String spaceCannonWindow(Game game, Player active, long turnStart, String position) {
        return "spaceCannon|" + game.getRound() + "|" + active.getFaction() + "|" + turnStart + "|" + position;
    }

    private static Optional<PromptButton> graviton(AiTurnContext context, AiPrompt prompt, Tile tile) {
        Player seat = context.seat();
        if (tile == null || !seat.hasTechReady(GRAVITON)) return Optional.empty();
        UnitHolder space = BoardView.space(tile);
        boolean fightersScreenShips = context.game().getPlayers().values().stream()
                .filter(other -> other != seat && other.getColor() != null)
                .anyMatch(other -> BoardView.count(space, other, UnitType.Fighter) > 0
                        && BoardView.nonFighterShips(space, other) > 0);
        if (!fightersScreenShips) return Optional.empty();
        return prompt.firstEnabled(button -> button.isUnowned() && GRAVITON_EXHAUST.equals(button.handlerId()));
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

    private static Optional<AiDecision> assaultCannon(AiTurnContext context, List<AiPrompt> prompts) {
        Player seat = context.seat();
        if (!seat.hasTech(ASSAULT_CANNON)) return Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<PromptButton> fire = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(ASSAULT_CANNON_PREFIX));
            if (fire.isEmpty()) continue;
            String position = StringUtils.removeStart(fire.get().handlerId(), ASSAULT_CANNON_PREFIX);
            Tile tile = context.game().getTileByPosition(position);
            String key = "assaultCannon|" + prompt.messageId() + "|" + position;
            if (tile == null
                    || context.memory().has(key)
                    || tracker(context, context.faction(), position, BoardView.SPACE) > 0) {
                continue;
            }
            if (!firesAssaultCannon(seat, tile) || !enemyNonFighterShips(context.game(), seat, tile)) continue;
            context.memory().put(key, "pressed");
            return Optional.of(AiDecision.press(prompt, fire.get(), "fire Assault Cannon"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> destroyShipForAssaultCannon(AiTurnContext context, List<AiPrompt> prompts) {
        String type = context.game().getStoredValue(context.faction() + LATEST_ASSIGN_HITS);
        if (!ASSAULT_CANNON_HITS.equals(type)) return Optional.empty();
        for (AiPrompt prompt : prompts) {
            List<PromptButton> losses = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()) && isSingleUnitLoss(button))
                    .toList();
            if (losses.isEmpty()) continue;
            String key = "assaultCannonLoss|" + prompt.messageId();
            if (context.memory().has(key)) {
                Optional<PromptButton> done =
                        prompt.firstEnabled(button -> button.isUnowned() && DONE_REMOVING.equals(button.handlerId()));
                if (done.isEmpty()) continue;
                return Optional.of(AiDecision.press(prompt, done.get(), "finish the Assault Cannon loss"));
            }
            PromptButton cheapest = losses.stream()
                    .min(Comparator.comparingDouble(button -> lossCost(context.seat(), button)))
                    .orElseThrow();
            context.memory().put(key, "destroyed");
            return Optional.of(AiDecision.press(prompt, cheapest, "destroy its cheapest ship for Assault Cannon"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> magenDefenseGrid(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<PromptButton> magen = prompt.firstEnabled(button ->
                    button.isOwnedBy(context.faction()) && button.handlerId().startsWith(MAGEN_HIT_PREFIX));
            if (magen.isEmpty()) continue;
            String key = "magenHit|" + prompt.messageId() + "|" + magen.get().handlerId();
            if (context.memory().has(key)) continue;
            context.memory().put(key, "pressed");
            return Optional.of(AiDecision.press(prompt, magen.get(), "hit with Magen Defense Grid"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> hitOpposingGroundForce(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> target = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(GROUND_HIT_PREFIX))
                    .max(Comparator.comparingInt(CombatRules::groundHitValue));
            if (target.isPresent()) return Optional.of(AiDecision.press(prompt, target.get(), "hit a ground force"));
        }
        return Optional.empty();
    }

    private static int groundHitValue(PromptButton button) {
        String[] parts = button.handlerId().split("_");
        String unit = parts.length > 2 ? parts[2] : "";
        boolean damaged = unit.contains("damaged");
        String type = unit.replace("damaged", "").replace("galvanized", "");
        if (UnitType.Mech.plainName().equals(type)) return damaged ? 3 : 1;
        return UnitType.Infantry.plainName().equals(type) ? 2 : 0;
    }

    private static Optional<AiDecision> takeSingleHit(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            List<PromptButton> losses = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && (isSingleUnitLoss(button) || isSingleUnitSustain(button)))
                    .toList();
            Optional<PromptButton> close =
                    prompt.firstEnabled(button -> button.isUnowned() && DONE_REMOVING.equals(button.handlerId()));
            if (losses.isEmpty() || close.isEmpty()) continue;
            String key = "singleHit|" + prompt.messageId();
            if (context.memory().has(key)) {
                return Optional.of(AiDecision.press(prompt, close.get(), "finish taking the hit"));
            }
            if (!CANCEL_THE_HIT.equals(close.get().label())) continue;
            PromptButton choice = losses.stream()
                    .filter(CombatRules::isSingleUnitSustain)
                    .findFirst()
                    .orElse(losses.getFirst());
            context.memory().put(key, "taken");
            return Optional.of(AiDecision.press(prompt, choice, "take the hit"));
        }
        return Optional.empty();
    }

    static boolean isSingleUnitLoss(PromptButton button) {
        return isSingleUnitPick(button, ASSIGN_HITS);
    }

    static boolean isSingleUnitSustain(PromptButton button) {
        return isSingleUnitPick(button, ASSIGN_DAMAGE);
    }

    private static boolean isSingleUnitPick(PromptButton button, String action) {
        String[] parts = button.handlerId().split("_");
        return parts.length >= 5 && action.equals(parts[0]) && StringUtils.isNumeric(parts[2]);
    }

    static double lossCost(Player seat, PromptButton button) {
        String[] parts = button.handlerId().split("_");
        UnitModel model = seat.getUnitFromAsyncID(parts[3]);
        double cost = model == null ? Double.MAX_VALUE : model.getCost();
        boolean damaged = parts.length > 5 && UnitState.dmg.name().equals(parts[4]);
        return damaged ? cost - DAMAGED_DISCOUNT : cost;
    }

    private static boolean firesAssaultCannon(Player seat, Tile tile) {
        return ButtonHelper.checkNumberNonFighterShips(seat, tile) >= ASSAULT_CANNON_SHIPS
                || ButtonHelper.doesPlayerHaveFSHere(NEKRO_FLAGSHIP, seat, tile);
    }

    private static boolean enemyNonFighterShips(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        return game.getPlayers().values().stream()
                .filter(other -> other != seat && other.getColor() != null)
                .anyMatch(other -> BoardView.nonFighterShips(space, other) > 0);
    }

    private static Optional<AiDecision> confirmAutomation(
            AiTurnContext context, List<AiPrompt> prompts, CombatCardHook hook) {
        Optional<Match> confirmation = Prompts.owned(
                prompts, context.faction(), id -> id.startsWith(AUTOMATE_PREFIX) && id.endsWith(CONFIRMED_SUFFIX));
        if (confirmation.isEmpty()) return Optional.empty();
        Optional<ReadyForDice.Round> round = automationRound(context, prompts, confirmation.get());
        if (round.isEmpty() || ReadyForDice.decided(context, round.get())) {
            return Optional.of(confirmation.get().press("automate ground combat"));
        }
        return ReadyForDice.decide(context, round.get(), hook);
    }

    private static Optional<ReadyForDice.Round> automationRound(
            AiTurnContext context, List<AiPrompt> prompts, Match confirmation) {
        String planet = confirmation.button().handlerId().split("_")[3];
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : prompt.enabledButtons()) {
                if (!isRoundRoll(roll) || !roll.handlerId().endsWith("_" + planet)) continue;
                Optional<Combat> combat = combatOf(context.game(), context.seat(), roll);
                if (combat.isEmpty() || !startsRound(context, prompts, roll, combat.get())) continue;
                CombatMoment moment = new CombatMoment(
                        positionOf(roll), planet, true, combat.get().mine());
                return Optional.of(ReadyForDice.forAutomation(
                        context, confirmation, moment, combat.get().opponents()));
            }
        }
        return Optional.empty();
    }

    private static boolean startsRound(
            AiTurnContext context, List<AiPrompt> prompts, PromptButton roll, Combat combat) {
        return combat.mine() == combat.theirs()
                && positionOf(roll).equals(context.game().getActiveSystem())
                && !hitsPending(prompts, roll);
    }

    private static Optional<ReadyForDice.Round> readyRound(
            AiTurnContext context, List<AiPrompt> prompts, AiPrompt prompt, PromptButton roll) {
        Optional<Combat> combat = combatOf(context.game(), context.seat(), roll);
        if (combat.isEmpty() || !startsRound(context, prompts, roll, combat.get())) return Optional.empty();
        String holder = roll.handlerId().split("_")[2];
        boolean ground = !BoardView.SPACE.equals(holder);
        if (ground && automationUnderWay(prompts, holder)) return Optional.empty();
        CombatMoment moment =
                new CombatMoment(positionOf(roll), holder, ground, combat.get().mine());
        return ReadyForDice.forRoll(
                context, prompts, prompt, moment, combat.get().opponents());
    }

    private static Optional<AiDecision> readyStep(
            AiTurnContext context,
            List<AiPrompt> prompts,
            ReadyForDice.Round round,
            AiPrompt prompt,
            PromptButton roll,
            RollTiming timing,
            CombatCardHook hook) {
        if (!ReadyForDice.decided(context, round)) return ReadyForDice.decide(context, round, hook);
        if (timing == RollTiming.NOT_NOW) return Optional.empty();
        return Optional.of(ReadyForDice.rollOrWait(context, prompts, round, prompt, roll));
    }

    private static Optional<AiDecision> combatRound(
            AiTurnContext context, List<AiPrompt> prompts, CombatCardHook hook) {
        Optional<AiDecision> waiting = Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : prompt.enabledButtons()) {
                if (!isRoundRoll(roll)) continue;
                RollTiming timing = rollAllowed(context, roll);
                if (timing == RollTiming.NOW) {
                    return Optional.of(AiDecision.press(prompt, roll, "roll a combat round"));
                }
                Optional<ReadyForDice.Round> round = readyRound(context, prompts, prompt, roll);
                if (round.isPresent()) {
                    Optional<AiDecision> step = readyStep(context, prompts, round.get(), prompt, roll, timing, hook);
                    if (step.isEmpty()) continue;
                    if (!(step.get() instanceof AiDecision.Wait)) return step;
                    if (waiting.isEmpty()) waiting = step;
                    continue;
                }
                if (timing != RollTiming.AFTER_GRACE) continue;
                long grace = onlyAiOpponents(context.game(), context.seat(), roll)
                        ? AI_ROLL_GRACE_MILLIS
                        : ROLL_GRACE_MILLIS;
                long readyAt = newestInChannel(prompts, prompt.channelId()) + grace;
                if (context.now() >= readyAt) {
                    Optional<AiDecision> card = roundStartCard(context, prompts, roll, hook);
                    if (card.isPresent()) return card;
                    return Optional.of(AiDecision.press(prompt, roll, "roll a combat round"));
                }
                if (waiting.isEmpty())
                    waiting = Optional.of(new AiDecision.Wait(readyAt, "giving the opponent a moment"));
            }
        }
        return waiting;
    }

    private static Optional<AiDecision> roundStartCard(
            AiTurnContext context, List<AiPrompt> prompts, PromptButton roll, CombatCardHook hook) {
        Optional<Combat> combat = combatOf(context.game(), context.seat(), roll);
        String[] parts = roll.handlerId().split("_");
        if (combat.isEmpty()
                || combat.get().mine() != combat.get().theirs()
                || !parts[1].equals(context.game().getActiveSystem())
                || hitsPending(prompts, roll)) {
            return Optional.empty();
        }
        boolean ground = !BoardView.SPACE.equals(parts[2]);
        if (ground && automationUnderWay(prompts, parts[2])) return Optional.empty();
        return hook.atRoundStart(
                context,
                new CombatMoment(parts[1], parts[2], ground, combat.get().mine()));
    }

    private static boolean automationUnderWay(List<AiPrompt> prompts, String planet) {
        return prompts.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream())
                .map(PromptButton::handlerId)
                .anyMatch(id -> id.startsWith(AUTOMATE_PREFIX)
                        && id.endsWith(CONFIRMED_SUFFIX)
                        && id.contains("_" + planet + "_"));
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
        if (opponents.stream().allMatch(Player::isNeutral)) return neutralTiming(game, seat, roll, mine);
        if (opponents.stream().allMatch(AiSeats::isAiSeat)
                && mine == 0
                && game.getActivePlayer() != seat
                && opponents.contains(game.getActivePlayer())) {
            return RollTiming.NOT_NOW;
        }
        return RollTiming.AFTER_GRACE;
    }

    private static RollTiming neutralTiming(Game game, Player seat, PromptButton roll, int mine) {
        boolean barraged = mine == 0 && NeutralCombat.barrages(game, seat, positionOf(roll));
        return barraged ? RollTiming.AFTER_GRACE : RollTiming.NOW;
    }

    record Combat(List<Player> opponents, int mine, int theirs) {}

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
                .map(combat -> combat.opponents().stream()
                        .allMatch(opponent -> AiSeats.isAiSeat(opponent) || opponent.isNeutral()))
                .orElse(false);
    }

    private static boolean hitsPending(List<AiPrompt> prompts, PromptButton roll) {
        return prompts.stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .flatMap(prompt -> prompt.enabledButtons().stream())
                .anyMatch(button -> (isRoundHit(button) || isBarrageHit(button)) && sameCombat(button, roll));
    }

    private static boolean isBarrageHit(PromptButton button) {
        return button.handlerId().startsWith(AFB_HIT_PREFIX);
    }

    static boolean assignsNothing(PromptButton button) {
        return button.handlerId().startsWith(ASSIGN_PREFIX)
                && "0".equals(StringUtils.substringAfterLast(button.handlerId(), "_"));
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

    public static int tracker(Game game, String faction, String position, String holder) {
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
