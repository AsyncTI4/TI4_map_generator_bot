package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

@UtilityClass
public class PaymentRules {

    static final String PAYMENT_KEY = "pendingPayment|";
    private static final String PAID_KEY = "paidPayment|";
    private static final String PRESSED_KEY = "paymentPressed|";
    private static final String DONE_EXHAUSTING = "Done Exhausting Planets";
    private static final String SPEND_PREFIX = "spend_";
    private static final String ONE_TRADE_GOOD_PREFIX = "reduceTG_1_";
    private static final String FIELD = "~";
    private static final String ITEM = ";";
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final long PAYMENT_WINDOW_MILLIS = 10 * 60_000L;
    public static final String OBJECTIVE_DONE = "deleteButtons";
    public static final String TECHNOLOGY_DONE = "deleteButtons_technology";
    public static final String LEADERSHIP_DONE = "deleteButtons_leadership";

    record PendingPayment(
            String purpose,
            List<String> planets,
            int tradeGoodsAfter,
            long since,
            String doneHandler,
            boolean lastsThePhase) {

        String encode() {
            return String.join(
                    FIELD,
                    purpose,
                    String.join(ITEM, planets),
                    String.valueOf(tradeGoodsAfter),
                    String.valueOf(since),
                    doneHandler,
                    lastsThePhase ? "1" : "0");
        }

        static Optional<PendingPayment> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null
                    || fields.length != 6
                    || !StringUtils.isNumeric(fields[2])
                    || !StringUtils.isNumeric(fields[3])) {
                return Optional.empty();
            }
            List<String> planets = List.of(StringUtils.split(fields[1], ITEM));
            return Optional.of(new PendingPayment(
                    fields[0],
                    planets,
                    Integer.parseInt(fields[2]),
                    Long.parseLong(fields[3]),
                    fields[4],
                    "1".equals(fields[5])));
        }
    }

    public static void expect(AiTurnContext context, String purpose, Wallet.Payment payment, String doneHandler) {
        remember(context, purpose, payment, doneHandler, false);
    }

    public static void expectWhenScored(
            AiTurnContext context, String purpose, Wallet.Payment payment, String doneHandler) {
        remember(context, purpose, payment, doneHandler, true);
    }

    private static void remember(
            AiTurnContext context, String purpose, Wallet.Payment payment, String doneHandler, boolean lastsThePhase) {
        List<String> planets = new ArrayList<>(payment.forResources());
        planets.addAll(payment.forInfluence());
        int tradeGoodsAfter = Math.max(0, context.seat().getTg() - payment.tradeGoods());
        PendingPayment pending =
                new PendingPayment(purpose, planets, tradeGoodsAfter, context.now(), doneHandler, lastsThePhase);
        context.memory().put(key(context), pending.encode());
    }

    public static void expectNothing(AiTurnContext context, String purpose, String doneHandler) {
        expect(context, purpose, new Wallet.Payment(List.of(), List.of(), 0, 0), doneHandler);
    }

    public static boolean isPending(AiTurnContext context) {
        return pendingSince(context).isPresent();
    }

    public static Optional<Long> pendingSince(AiTurnContext context) {
        return current(context).map(PendingPayment::since);
    }

    public static void forget(AiTurnContext context) {
        context.memory().remove(key(context));
    }

    private static Optional<PendingPayment> current(AiTurnContext context) {
        Optional<PendingPayment> pending = context.memory().get(key(context)).flatMap(PendingPayment::decode);
        if (pending.isPresent()
                && !pending.get().lastsThePhase()
                && context.now() - pending.get().since() > PAYMENT_WINDOW_MILLIS) {
            forget(context);
            return Optional.empty();
        }
        return pending;
    }

    public static Optional<AiDecision> pay(AiTurnContext context) {
        Optional<PendingPayment> pending = current(context);
        if (pending.isEmpty()) return Optional.empty();
        Player seat = context.seat();
        long since = pending.get().since() - CLOCK_SKEW_MILLIS;
        String doneHandler = pending.get().doneHandler();
        Optional<AiPrompt> prompt = Prompts.newestFirst(context.prompts()).stream()
                .filter(candidate -> candidate.createdAtMillis() >= since)
                .filter(candidate -> isOwnPaymentPrompt(seat, candidate, doneHandler))
                .findFirst();
        if (prompt.isEmpty()) return Optional.empty();
        AiPrompt payment = prompt.get();
        String purpose = pending.get().purpose();
        for (String planet : pending.get().planets()) {
            if (seat.getExhaustedPlanets().contains(planet)) continue;
            Optional<PromptButton> spend = payment.firstEnabled(button -> planet.equals(planetOf(button)));
            if (spend.isPresent()) {
                rememberPressed(context, pending.get(), planet);
                return Optional.of(AiDecision.press(payment, spend.get(), "exhaust " + planet + " for " + purpose));
            }
        }
        if (seat.getTg() > pending.get().tradeGoodsAfter()) {
            Optional<PromptButton> tradeGood = payment.enabledHandlerPrefix(ONE_TRADE_GOOD_PREFIX);
            if (tradeGood.isPresent()) {
                return Optional.of(AiDecision.press(payment, tradeGood.get(), "spend a trade good for " + purpose));
            }
        }
        forget(context);
        if (isSettled(context, seat, pending.get()))
            context.memory().put(PAID_KEY + pending.get().since(), "paid");
        return payment.firstEnabled(button -> isDone(button, doneHandler))
                .map(button -> AiDecision.press(payment, button, "finish paying for " + purpose));
    }

    private static boolean isSettled(AiTurnContext context, Player seat, PendingPayment pending) {
        List<String> pressed = pressedPlanets(context, pending);
        return pressed.containsAll(pending.planets())
                && seat.getExhaustedPlanets().containsAll(pending.planets())
                && seat.getTg() <= pending.tradeGoodsAfter();
    }

    private static void rememberPressed(AiTurnContext context, PendingPayment pending, String planet) {
        List<String> pressed = new ArrayList<>(pressedPlanets(context, pending));
        if (pressed.contains(planet)) return;
        pressed.add(planet);
        context.memory().put(PRESSED_KEY + pending.since(), String.join(ITEM, pressed));
    }

    private static List<String> pressedPlanets(AiTurnContext context, PendingPayment pending) {
        String value = context.memory().get(PRESSED_KEY + pending.since()).orElse("");
        return List.of(StringUtils.split(value, ITEM));
    }

    public static boolean wasPaid(AiTurnContext context, long since) {
        return context.memory().has(PAID_KEY + since);
    }

    static boolean isOwnPaymentPrompt(Player seat, AiPrompt prompt, String doneHandler) {
        if (prompt.firstEnabled(button -> isDone(button, doneHandler)).isEmpty()) return false;
        if (prompt.isHidden()) return true;
        List<String> planets = prompt.buttons().stream()
                .map(PaymentRules::planetOf)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (!planets.isEmpty()) return seat.getPlanets().containsAll(planets);
        String content = prompt.content();
        return content.contains(seat.getRepresentationUnfogged()) || content.contains(seat.getRepresentationNoPing());
    }

    private static boolean isDone(PromptButton button, String doneHandler) {
        return button.isUnowned() && doneHandler.equals(button.handlerId()) && DONE_EXHAUSTING.equals(button.label());
    }

    private static String planetOf(PromptButton button) {
        if (!button.handlerId().startsWith(SPEND_PREFIX)) return "";
        String rest = StringUtils.removeStart(button.handlerId(), SPEND_PREFIX);
        return StringUtils.substringBefore(rest, "_");
    }

    private static String key(AiTurnContext context) {
        return PAYMENT_KEY + context.game().getRound() + "|" + context.game().getPhaseOfGame();
    }
}
