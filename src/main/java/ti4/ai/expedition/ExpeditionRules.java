package ti4.ai.expedition;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SecretValue;
import ti4.game.Player;

@UtilityClass
public class ExpeditionRules {

    public static final String TILE_PREFIX = ThundersEdgeRules.TILE_PREFIX;
    static final long STEP_TIMEOUT_MILLIS = 60_000L;
    static final long POLL_MILLIS = 3_000L;
    private static final String KEY = "expedition|";
    private static final String PAID_KEY = "expeditionPaid|";
    private static final String FIELD = "~";
    private static final String INFO = "expeditionInfoAndButtons";
    private static final String END_OF_TURN = "endOfTurnAbilities";
    private static final String PASSING = "passingAbilities";
    private static final String PASS = "passForRound";
    private static final String PAYMENT_DONE = "deleteButtons_spitItOut";
    private static final String DISCARD_SECRET = "discardSecret_";
    private static final String DISCARD_CARD = "ac_discard_from_hand_";
    private static final String OPEN_CARD_DISCARD = "getDiscardButtonsACs";
    private static final int CARDS_DISCARDED = KindPricing.ACTION_CARDS_COST;

    private enum Stage {
        CLAIM,
        PAY,
        SECRET,
        CARDS,
        DONE
    }

    private record Flow(ExpeditionKind kind, Stage stage, long since, int holding) {

        String encode() {
            return String.join(FIELD, kind.id(), stage.name(), String.valueOf(since), String.valueOf(holding));
        }

        static Optional<Flow> decode(String value) {
            String[] fields = StringUtils.split(value, FIELD);
            if (fields.length != 4) return Optional.empty();
            return ExpeditionKind.of(fields[0])
                    .map(kind -> new Flow(
                            kind, Stage.valueOf(fields[1]), Long.parseLong(fields[2]), Integer.parseInt(fields[3])));
        }

        Flow at(Stage next, long now) {
            return new Flow(kind, next, now, holding);
        }

        Flow finished() {
            return new Flow(kind, Stage.DONE, since, holding);
        }
    }

    public static Optional<AiDecision> endOfTurn(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (inThePassingMenu(context, thisTurn)) return beforePassing(context, thisTurn);
        return open(
                context,
                thisTurn,
                END_OF_TURN,
                "open its end-of-turn abilities to do an expedition",
                () -> ExpeditionOutlook.claimNow(context.game(), context.seat()));
    }

    private static boolean inThePassingMenu(AiTurnContext context, List<AiPrompt> thisTurn) {
        return Prompts.owned(thisTurn, context.faction(), INFO::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.prompt().enabledHandler(PASS).isPresent())
                .orElse(false);
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        return open(
                context,
                thisTurn,
                PASSING,
                "open its passing abilities to do an expedition",
                () -> ExpeditionOutlook.best(
                        context.game(),
                        context.seat(),
                        Holdings.of(context.game(), context.seat()).atPassing()));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        return FractureRules.roll(context)
                .or(() -> FractureRules.placeIngress(context))
                .or(() -> ThundersEdgeRules.next(context))
                .or(() -> continueFlow(context));
    }

    private static Optional<AiDecision> open(
            AiTurnContext context,
            List<AiPrompt> thisTurn,
            String opener,
            String reason,
            Supplier<Optional<ExpeditionOutlook.Choice>> claim) {
        if (context.memory().has(key(context))) return Optional.empty();
        Optional<Match> info = Prompts.owned(thisTurn, context.faction(), INFO::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        Optional<Match> menu = info.or(() -> Prompts.owned(thisTurn, context.faction(), opener::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button())));
        if (menu.isEmpty()) return Optional.empty();
        Optional<ExpeditionOutlook.Choice> choice = claim.get();
        if (choice.isEmpty()) return Optional.empty();
        if (info.isPresent()) {
            remember(context, new Flow(choice.get().kind(), Stage.CLAIM, context.now(), 0));
            return info.map(match -> match.press("do the " + choice.get().kind().id() + " expedition"));
        }
        return menu.map(match -> match.press(reason));
    }

    private static Optional<AiDecision> continueFlow(AiTurnContext context) {
        Optional<Flow> flow = context.memory().get(key(context)).flatMap(Flow::decode);
        if (flow.isEmpty()) return Optional.empty();
        return switch (flow.get().stage()) {
            case CLAIM -> claim(context, flow.get());
            case PAY -> awaitPayment(context, flow.get());
            case SECRET -> discardSecret(context, flow.get());
            case CARDS -> discardCards(context, flow.get());
            case DONE -> Optional.empty();
        };
    }

    private static Optional<AiDecision> claim(AiTurnContext context, Flow flow) {
        ExpeditionKind kind = flow.kind();
        Optional<ExpeditionOutlook.Choice> choice =
                ExpeditionOutlook.options(context.game(), context.seat(), Holdings.of(context.game(), context.seat()))
                        .stream()
                        .filter(option -> option.kind() == kind)
                        .findFirst();
        if (kind.isClaimed(context.game()) || choice.isEmpty()) return finish(context, flow);
        Optional<Match> button = Prompts.owned(
                        Prompts.newestFirst(context.prompts()), context.faction(), kind.handler()::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (button.isEmpty()) return waitFor(context, flow, "the expedition choices");
        Player seat = context.seat();
        Stage next = nextStage(kind);
        int holding = kind == ExpeditionKind.SECRET ? seat.getSecretsUnscored().size() : seat.getAcCount();
        remember(context, new Flow(kind, next, context.now(), holding));
        if (next == Stage.PAY) {
            PaymentRules.expect(context, "an expedition", choice.get().payment(), PAYMENT_DONE);
            rememberPaid(context, choice.get().payment());
        }
        return button.map(match -> match.press("claim the " + kind.id() + " expedition"));
    }

    public static Set<String> paidThisTurn(AiTurnContext context) {
        String paid = context.memory().get(PAID_KEY + context.turnKey()).orElse("");
        return Set.of(StringUtils.split(paid, FIELD));
    }

    private static void rememberPaid(AiTurnContext context, Wallet.Payment payment) {
        String planets = Stream.concat(payment.forResources().stream(), payment.forInfluence().stream())
                .distinct()
                .collect(Collectors.joining(FIELD));
        context.memory().put(PAID_KEY + context.turnKey(), planets);
    }

    private static Stage nextStage(ExpeditionKind kind) {
        return switch (kind) {
            case TECH_SKIP, FIVE_RES, FIVE_INF -> Stage.PAY;
            case SECRET -> Stage.SECRET;
            case ACTION_CARDS -> Stage.CARDS;
            case TRADE_GOODS -> Stage.DONE;
        };
    }

    private static Optional<AiDecision> awaitPayment(AiTurnContext context, Flow flow) {
        if (!PaymentRules.isPending(context)) return finish(context, flow);
        if (expired(context, flow)) {
            PaymentRules.forget(context);
            return finish(context, flow);
        }
        return Optional.of(wait(context, "paying for the expedition"));
    }

    private static Optional<AiDecision> discardSecret(AiTurnContext context, Flow flow) {
        Player seat = context.seat();
        if (seat.getSecretsUnscored().size() < flow.holding()) return finish(context, flow);
        Optional<String> worst = SecretValue.lowestValueSecret(context.game(), seat);
        if (worst.isEmpty()) return finish(context, flow);
        String handler = DISCARD_SECRET + seat.getSecretsUnscored().get(worst.get());
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            Optional<PromptButton> button =
                    prompt.enabledHandler(handler).filter(candidate -> !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) {
                return Optional.of(AiDecision.press(prompt, button.get(), "discard the least promising secret"));
            }
        }
        return waitOrGiveUp(context, flow, "the secret objective discard buttons");
    }

    private static Optional<AiDecision> discardCards(AiTurnContext context, Flow flow) {
        Player seat = context.seat();
        if (seat.getAcCount() <= flow.holding() - CARDS_DISCARDED) return finish(context, flow);
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts());
        for (AiPrompt prompt : visible) {
            Optional<PromptButton> discard = ActionCardValue.worstDiscard(
                    context.game(), seat, prompt, DISCARD_CARD, "", button -> !context.alreadyPressed(prompt, button));
            if (discard.isPresent()) {
                return Optional.of(
                        AiDecision.press(prompt, discard.get(), "discard an action card for the expedition"));
            }
        }
        Optional<Match> open = Prompts.first(
                        visible.stream().filter(AiPrompt::isHidden).toList(),
                        button -> OPEN_CARD_DISCARD.equals(button.handlerId()))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (open.isPresent()) return open.map(match -> match.press("ask for the action card discard buttons"));
        return waitOrGiveUp(context, flow, "the action card discard buttons");
    }

    private static Optional<AiDecision> waitOrGiveUp(AiTurnContext context, Flow flow, String awaited) {
        return expired(context, flow) ? finish(context, flow) : Optional.of(wait(context, awaited));
    }

    private static Optional<AiDecision> waitFor(AiTurnContext context, Flow flow, String awaited) {
        return waitOrGiveUp(context, flow, awaited);
    }

    private static AiDecision wait(AiTurnContext context, String awaited) {
        return new AiDecision.Wait(context.now() + POLL_MILLIS, awaited);
    }

    private static boolean expired(AiTurnContext context, Flow flow) {
        return context.now() - flow.since() > STEP_TIMEOUT_MILLIS;
    }

    private static Optional<AiDecision> finish(AiTurnContext context, Flow flow) {
        remember(context, flow.finished());
        return Optional.empty();
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(key(context), flow.encode());
    }

    private static String key(AiTurnContext context) {
        return KEY + context.turnKey();
    }
}
