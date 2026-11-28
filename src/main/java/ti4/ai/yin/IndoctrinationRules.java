package ti4.ai.yin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.ValueScale;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class IndoctrinationRules {

    static final String ABILITY = "indoctrination";
    static final String START_PREFIX = "initialIndoctrination_";
    static final String CHOICE_PREFIX = "indoctrinate_";
    static final String MECH = "yin_mech";
    static final int INFANTRY_INFLUENCE = 2;
    static final int MECH_INFLUENCE = 3;
    static final double MIN_NET = 0;
    private static final String KEY = "indoctrination|";
    private static final String PENDING_KEY = "indoctrinationChoice|";
    private static final String DECIDED = "decided";
    private static final String DONE_PAYING = "deleteButtons";
    private static final String DECLINE_LABEL = "Decline";
    private static final String FIELD = "~";
    private static final String INFANTRY = "infantry";
    private static final String MECH_UNIT = "mech";
    private static final long CHOICE_WAIT_MILLIS = 30_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    record Option(String unit, double net) {}

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.seat().hasAbility(ABILITY)) return Optional.empty();
        List<AiPrompt> combat = combatPrompts(context);
        return choose(context, combat).or(() -> start(context, combat));
    }

    private static Optional<AiDecision> start(AiTurnContext context, List<AiPrompt> combat) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : combat) {
            for (var button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(START_PREFIX)) continue;
                String planet = StringUtils.removeStart(button.handlerId(), START_PREFIX);
                String key = KEY + prompt.messageId() + "|" + planet;
                if (context.memory().has(key)) continue;
                context.memory().put(key, DECIDED);
                Tile tile = game.getTileFromPlanet(planet);
                if (tile == null || CombatRules.tracker(game, seat.getFaction(), tile.getPosition(), planet) > 0) {
                    continue;
                }
                Optional<Option> best = best(game, seat, tile.getPosition(), planet);
                if (best.isEmpty()) continue;
                context.memory()
                        .put(
                                PENDING_KEY + planet,
                                String.join(FIELD, best.get().unit(), String.valueOf(context.now())));
                return Optional.of(AiDecision.press(
                        prompt,
                        button,
                        "indoctrinate an enemy infantry on " + planet + " into its "
                                + best.get().unit()));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> choose(AiTurnContext context, List<AiPrompt> combat) {
        for (String key : context.memory().keysStartingWith(PENDING_KEY)) {
            String planet = StringUtils.removeStart(key, PENDING_KEY);
            String[] fields =
                    StringUtils.splitPreserveAllTokens(context.memory().get(key).orElse(""), FIELD);
            if (fields == null || fields.length != 2 || !StringUtils.isNumeric(fields[1])) {
                context.memory().remove(key);
                continue;
            }
            long since = Long.parseLong(fields[1]);
            List<AiPrompt> fresh = combat.stream()
                    .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                    .toList();
            Optional<Match> wanted = choiceButton(fresh, planet, fields[0]);
            Optional<Match> infantry = choiceButton(fresh, planet, INFANTRY);
            if (wanted.isEmpty() && infantry.isEmpty()) {
                if (context.now() < since + CHOICE_WAIT_MILLIS) {
                    return Optional.of(new AiDecision.Wait(since + CHOICE_WAIT_MILLIS, "the Indoctrination choice"));
                }
                context.memory().remove(key);
                continue;
            }
            context.memory().remove(key);
            Match choice = wanted.or(() -> infantry).orElseThrow();
            return resolve(context, choice, planet, wanted.isPresent() ? fields[0] : INFANTRY);
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> resolve(AiTurnContext context, Match choice, String planet, String unit) {
        int influence = MECH_UNIT.equals(unit) ? MECH_INFLUENCE : INFANTRY_INFLUENCE;
        Optional<Wallet.Payment> payment = payment(context.game(), context.seat(), influence);
        if (payment.isEmpty()) {
            return choice.prompt()
                    .firstEnabled(button -> button.isUnowned()
                            && DONE_PAYING.equals(button.handlerId())
                            && DECLINE_LABEL.equals(button.label()))
                    .map(button ->
                            AiDecision.press(choice.prompt(), button, "decline Indoctrination it cannot pay for"));
        }
        PaymentRules.expect(context, "Indoctrination", payment.get(), DONE_PAYING);
        return Optional.of(choice.press("replace an enemy infantry on " + planet + " with its " + unit));
    }

    private static Optional<Match> choiceButton(List<AiPrompt> prompts, String planet, String unit) {
        String handler = CHOICE_PREFIX + planet + "_" + unit;
        return Prompts.unowned(prompts, handler::equals);
    }

    static Optional<Option> best(Game game, Player seat, String position, String planet) {
        double unlock = BrotherOmar.unlockBonus(seat);
        List<Option> options = new ArrayList<>();
        option(game, seat, position, planet, UnitType.Infantry, INFANTRY, INFANTRY_INFLUENCE, unlock)
                .ifPresent(options::add);
        if (seat.hasUnit(MECH)) {
            option(game, seat, position, planet, UnitType.Mech, MECH_UNIT, MECH_INFLUENCE, unlock)
                    .ifPresent(options::add);
        }
        return options.stream().filter(found -> found.net() >= MIN_NET).max(Comparator.comparingDouble(Option::net));
    }

    private static Optional<Option> option(
            Game game,
            Player seat,
            String position,
            String planet,
            UnitType type,
            String unit,
            int influence,
            double unlock) {
        if (payment(game, seat, influence).isEmpty()) return Optional.empty();
        double gain = CombatForecast.conversionGain(game, seat, position, planet, type)
                .gain();
        if (gain <= 0) return Optional.empty();
        return Optional.of(new Option(unit, gain + unlock - influence * ValueScale.INFLUENCE));
    }

    private static Optional<Wallet.Payment> payment(Game game, Player seat, int influence) {
        return ScoringReserve.planAfterReserve(game, seat, SpendCost.influence(influence));
    }

    private static List<AiPrompt> combatPrompts(AiTurnContext context) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .toList();
    }
}
