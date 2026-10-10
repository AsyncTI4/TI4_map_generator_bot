package ti4.ai.trade;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.trade.BuilderPrompts.Kind;
import ti4.ai.trade.BuilderPrompts.Pick;
import ti4.ai.trade.BuilderPrompts.Step;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class OfferBuilder {

    static final long STEP_WAIT_MILLIS = Duration.ofSeconds(20).toMillis();
    static final long DRAFT_MAX_AGE_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long HOUR_MILLIS = Duration.ofHours(1).toMillis();
    private static final String HOUR_KEY = "tradeHour";
    private static final String CLEANUP_KEY = "tradeCleanup|";
    private static final String ABORTED = "aborted";
    private static final String TIMES = ",";
    private static final String SEPARATOR = "_";
    private static final Set<ItemType> REQUESTABLE =
            EnumSet.of(ItemType.TRADE_GOODS, ItemType.COMMODITIES, ItemType.SEND_DEBT, ItemType.CLEAR_DEBT);
    private static final Set<Purpose> TURN_BOUND =
            EnumSet.of(Purpose.WASH, Purpose.SELL, Purpose.DESPERATION, Purpose.DEBT_PAYMENT, Purpose.DEBT_COLLECTION);
    private static final List<Order> ORDER = List.of(
            new Order(true, ItemType.COMMODITIES),
            new Order(true, ItemType.TRADE_GOODS),
            new Order(true, ItemType.SEND_DEBT),
            new Order(true, ItemType.CLEAR_DEBT),
            new Order(false, ItemType.CLEAR_DEBT),
            new Order(false, ItemType.SEND_DEBT),
            new Order(false, ItemType.COMMODITIES),
            new Order(false, ItemType.TRADE_GOODS),
            new Order(false, ItemType.PROMISSORY));

    private record Order(boolean fromPartner, ItemType type) {}

    private record Missing(Player sender, Player receiver, ItemType type, int remaining, String detail) {

        String row() {
            return type.token() + SEPARATOR + sender.getColor() + SEPARATOR + receiver.getColor();
        }
    }

    private record Build(AiTurnContext context, Draft draft, Player partner, Deal current, Optional<Step> step) {

        Game game() {
            return context.game();
        }

        Player seat() {
            return context.seat();
        }

        long now() {
            return context.now();
        }
    }

    static boolean active(AiTurnContext context) {
        return Draft.read(context.memory()).isPresent();
    }

    static Optional<String> partner(AiTurnContext context) {
        return Draft.read(context.memory()).map(Draft::partner);
    }

    static void abandon(AiTurnContext context) {
        Draft.clear(context.memory());
    }

    static int startsInLastHour(AiTurnContext context) {
        return recentStarts(context).size();
    }

    static Optional<AiDecision> start(AiTurnContext context, Player partner, Deal target, Purpose purpose) {
        if (active(context) || PendingOffers.with(context, partner) || !buildable(context.seat(), partner, target)) {
            return Optional.empty();
        }
        Optional<AiDecision> entry = TradeEntry.open(context);
        if (entry.isEmpty()) return Optional.empty();
        boolean opened = entry.get() instanceof AiDecision.Press;
        Draft.fresh(
                        partner.getFaction(),
                        purpose,
                        context.now(),
                        opened,
                        scopeKey(context.game(), purpose, target),
                        target)
                .write(context.memory());
        PendingOffers.markNoneOpen(context, partner);
        logStart(context);
        TradeLegality.useWindow(context, partner, purpose);
        return entry;
    }

    static Optional<AiDecision> startCounter(
            AiTurnContext context, IncomingOffer offer, Player offerer, Deal target, Purpose purpose) {
        if (active(context) || target.isEmpty() || !buildable(context.seat(), offerer, target)) {
            return Optional.empty();
        }
        Draft.fresh(
                        offerer.getFaction(),
                        purpose,
                        context.now(),
                        true,
                        scopeKey(context.game(), purpose, target),
                        target)
                .write(context.memory());
        PendingOffers.markNoneOpen(context, offerer);
        logStart(context);
        return Optional.of(
                AiDecision.press(offer.prompt(), offer.counter(), "trade: counter offer from " + offerer.getFaction()));
    }

    static void queueCleanup(AiTurnContext context, Player partner) {
        if (active(context) || !claimCleanup(context, partner)) return;
        writeCleanup(context, partner);
    }

    static Optional<AiDecision> startCleanup(AiTurnContext context, Player partner) {
        if (active(context)) return Optional.empty();
        claimCleanup(context, partner);
        writeCleanup(context, partner);
        return continueDraft(context);
    }

    static Optional<AiDecision> continueDraft(AiTurnContext context) {
        Optional<Draft> stored = Draft.read(context.memory());
        if (stored.isEmpty()) return Optional.empty();
        Draft draft = stored.get();
        Player partner = context.game().getPlayerFromColorOrFaction(draft.partner());
        if (partner == null || !DealItem.tradable(partner.getFaction())) {
            Draft.clear(context.memory());
            return Optional.empty();
        }
        long since = draft.lastPressAt() - BuilderPrompts.SAME_STEP_TOLERANCE_MILLIS;
        Build build = new Build(
                context,
                draft,
                partner,
                Deal.between(context.seat(), partner),
                BuilderPrompts.newest(context, partner, since));
        if (draft.aborting()) return finishAbort(build);
        if (mustAbort(build)) return abort(build);
        if (draft.purpose() == Purpose.CLEANUP) return cleanUp(build);
        if (!build.current().fitsWithin(draft.target())) return reset(build);
        if (build.current().sameAs(draft.target())) return send(build);
        return nextMissing(build).map(missing -> step(build, missing)).orElseGet(() -> idle(build));
    }

    private static boolean mustAbort(Build build) {
        Draft draft = build.draft();
        AiTurnContext context = build.context();
        if (build.now() - draft.startedAt() > DRAFT_MAX_AGE_MILLIS
                || !draft.window().equals(scopeKey(build.game(), draft.purpose(), draft.target()))) {
            return true;
        }
        if (draft.purpose() == Purpose.CLEANUP) return false;
        return PendingOffers.with(context, build.partner())
                || IncomingOffer.live(context).stream()
                        .anyMatch(offer -> offer.isFrom(build.game(), build.partner())
                                && offer.isCurrent(build.game(), build.seat()));
    }

    private static Optional<AiDecision> step(Build build, Missing missing) {
        if (build.step().isEmpty()) return idle(build);
        Step step = build.step().get();
        return switch (step.kind()) {
            case PICKER -> pick(build, step, missing);
            case BUILDER -> addOrSwitch(build, step, missing);
            case PLAYER_PICKER -> choosePartner(build, step);
        };
    }

    private static Optional<AiDecision> pick(Build build, Step picker, Missing missing) {
        if (!picker.isFor(
                missing.type().token(),
                missing.sender().getColor(),
                missing.receiver().getColor())) {
            return restart(build);
        }
        List<Pick> picks = BuilderPrompts.picks(picker);
        Optional<Pick> choice = missing.type() == ItemType.PROMISSORY
                ? picks.stream()
                        .filter(pick -> pick.detail().equals(missing.detail()))
                        .findFirst()
                : picks.stream()
                        .filter(pick -> amount(pick).isPresent() && amount(pick).getAsInt() <= missing.remaining())
                        .max(Comparator.comparingInt(pick -> amount(pick).getAsInt()));
        if (choice.isEmpty()) return abort(build);
        return press(
                build, picker.prompt(), choice.get().button(), "trade: put " + describe(missing) + " in the offer");
    }

    private static OptionalInt amount(Pick pick) {
        return TradeMemory.parseInt(pick.detail());
    }

    private static Optional<AiDecision> addOrSwitch(Build build, Step builder, Missing missing) {
        boolean rightMode = builder.sender().equals(missing.sender().getColor());
        if (rightMode) {
            Optional<PromptButton> add = builder.button(BuilderPrompts.NEW_ITEM + missing.row());
            if (add.isEmpty()) return abort(build);
            return press(build, builder.prompt(), add.get(), "trade: choose " + describe(missing));
        }
        String toggle = BuilderPrompts.MODE
                + missing.sender().getColor()
                + SEPARATOR
                + missing.receiver().getColor();
        Optional<PromptButton> switchMode = builder.button(toggle);
        if (switchMode.isEmpty()) return abort(build);
        String mode = Standings.same(missing.sender(), build.seat()) ? "offering" : "asking";
        return press(build, builder.prompt(), switchMode.get(), "trade: switch the builder to " + mode);
    }

    private static Optional<AiDecision> choosePartner(Build build, Step playerPicker) {
        Optional<PromptButton> partner = BuilderPrompts.partnerButton(playerPicker, build.seat(), build.partner());
        if (partner.isEmpty()) return idle(build);
        return press(
                build,
                playerPicker.prompt(),
                partner.get(),
                "trade: start a transaction with " + build.partner().getFaction());
    }

    private static Optional<AiDecision> send(Build build) {
        Optional<Step> builder = build.step().filter(step -> step.kind() == Kind.BUILDER);
        if (builder.isEmpty()) return idle(build);
        if (!sendable(build)) return abort(build);
        Optional<PromptButton> send =
                builder.get().button(BuilderPrompts.SEND + build.partner().getColor());
        if (send.isEmpty()) return idle(build);
        Draft draft = build.draft();
        PendingOffers.record(build.context(), build.partner(), draft.purpose(), draft.target());
        TradeLegality.useWindow(build.context(), build.partner(), draft.purpose());
        Draft.clear(build.context().memory());
        return Optional.of(AiDecision.press(
                builder.get().prompt(),
                send.get(),
                "trade: send the " + draft.purpose().label() + " to "
                        + build.partner().getFaction()));
    }

    private static boolean sendable(Build build) {
        Game game = build.game();
        Player seat = build.seat();
        Player partner = build.partner();
        Deal deal = build.draft().target();
        Purpose purpose = build.draft().purpose();
        double trust = Trust.of(build.context(), partner);
        if (purpose.proposes()) {
            return TradeValue.coverable(game, seat, partner, deal)
                    && TradeValue.proposable(game, seat, partner, deal, trust);
        }
        if (purpose == Purpose.COUNTER) return TradeValue.acceptable(game, seat, partner, deal, trust);
        return TradeValue.commitmentOk(game, seat, partner, deal);
    }

    private static Optional<AiDecision> reset(Build build) {
        if (build.draft().restartsUsedUp()) return abort(build);
        Optional<Step> builder = build.step().filter(step -> step.kind() == Kind.BUILDER);
        if (builder.isEmpty()) return restart(build);
        Optional<PromptButton> reset =
                builder.get().button(BuilderPrompts.RESET + build.partner().getColor());
        if (reset.isEmpty()) return restart(build);
        build.draft().restartedAt(build.now()).write(build.context().memory());
        return Optional.of(AiDecision.press(
                builder.get().prompt(),
                reset.get(),
                "trade: start the offer to " + build.partner().getFaction() + " over"));
    }

    private static Optional<AiDecision> idle(Build build) {
        Draft draft = build.draft();
        AiTurnContext context = build.context();
        if (!draft.opened()) {
            Optional<AiDecision> entry = TradeEntry.open(context);
            if (entry.isEmpty()) return abort(build);
            if (entry.get() instanceof AiDecision.Press)
                draft.openedAt(build.now()).write(context.memory());
            return entry;
        }
        Optional<AiDecision> refreshed = TradeEntry.openRefreshed(context);
        if (refreshed.isPresent()) {
            draft.pressedAt(build.now()).write(context.memory());
            return refreshed;
        }
        if (build.now() - draft.lastPressAt() < STEP_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(
                    draft.lastPressAt() + STEP_WAIT_MILLIS, "trade: waiting for the transaction buttons"));
        }
        return restart(build);
    }

    private static Optional<AiDecision> restart(Build build) {
        if (build.draft().restartsUsedUp()) return abort(build);
        Optional<AiDecision> entry = TradeEntry.open(build.context());
        if (entry.isEmpty()) return abort(build);
        build.draft().restartedAt(build.now()).write(build.context().memory());
        return entry;
    }

    private static Optional<AiDecision> abort(Build build) {
        AiTurnContext context = build.context();
        Draft draft = build.draft();
        if (draft.purpose().settles()) {
            context.memory().put(TradeCardRules.settledKey(build.game(), build.partner()), ABORTED);
        }
        Draft.clear(context.memory());
        if (build.current().isEmpty()) return Optional.empty();
        Optional<Step> builder = BuilderPrompts.newestBuilder(context, build.partner(), Long.MIN_VALUE);
        Optional<PromptButton> reset = builder.flatMap(
                step -> step.button(BuilderPrompts.RESET + build.partner().getColor()));
        if (reset.isPresent()) {
            draft.abortingAt(build.now()).write(context.memory());
            return Optional.of(AiDecision.press(
                    builder.get().prompt(),
                    reset.get(),
                    "trade: abandon the offer to " + build.partner().getFaction()));
        }
        if (draft.purpose() == Purpose.CLEANUP || !claimCleanup(context, build.partner())) return Optional.empty();
        writeCleanup(context, build.partner());
        return continueDraft(context);
    }

    private static Optional<AiDecision> finishAbort(Build build) {
        AiTurnContext context = build.context();
        long since = build.draft().lastPressAt() - BuilderPrompts.SAME_STEP_TOLERANCE_MILLIS;
        Optional<Step> builder = BuilderPrompts.newestBuilder(context, build.partner(), since);
        Optional<PromptButton> delete = builder.flatMap(step -> step.button(BuilderPrompts.DELETE));
        if (delete.isPresent()) {
            Draft.clear(context.memory());
            return Optional.of(
                    AiDecision.press(builder.get().prompt(), delete.get(), "trade: delete the abandoned transaction"));
        }
        if (build.now() - build.draft().lastPressAt() < STEP_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(
                    build.draft().lastPressAt() + STEP_WAIT_MILLIS, "trade: waiting to delete the transaction"));
        }
        Draft.clear(context.memory());
        return Optional.empty();
    }

    private static Optional<AiDecision> cleanUp(Build build) {
        Draft draft = build.draft();
        boolean cleared = build.current().isEmpty();
        if (!draft.opened()) return cleared ? finished(build) : idle(build);
        if (build.step().isEmpty()) {
            boolean waited = build.now() - draft.lastPressAt() >= STEP_WAIT_MILLIS;
            return cleared && waited ? finished(build) : idle(build);
        }
        Step step = build.step().get();
        return switch (step.kind()) {
            case PLAYER_PICKER -> choosePartner(build, step);
            case PICKER -> restart(build);
            case BUILDER -> clearBuilder(build, step);
        };
    }

    private static Optional<AiDecision> finished(Build build) {
        Draft.clear(build.context().memory());
        return Optional.empty();
    }

    private static Optional<AiDecision> clearBuilder(Build build, Step builder) {
        if (!build.current().isEmpty()) {
            Optional<PromptButton> reset =
                    builder.button(BuilderPrompts.RESET + build.partner().getColor());
            if (reset.isEmpty()) return abort(build);
            return press(
                    build,
                    builder.prompt(),
                    reset.get(),
                    "trade: clear the transaction with " + build.partner().getFaction());
        }
        Draft.clear(build.context().memory());
        return builder.button(BuilderPrompts.DELETE)
                .map(delete -> AiDecision.press(
                        builder.prompt(),
                        delete,
                        "trade: delete the transaction with " + build.partner().getFaction()));
    }

    private static Optional<AiDecision> press(Build build, AiPrompt prompt, PromptButton button, String reason) {
        build.draft().pressedAt(build.now()).write(build.context().memory());
        return Optional.of(AiDecision.press(prompt, button, reason));
    }

    private static Optional<Missing> nextMissing(Build build) {
        Player seat = build.seat();
        Player partner = build.partner();
        Deal target = build.draft().target();
        Deal current = build.current();
        for (Order order : ORDER) {
            Player sender = order.fromPartner() ? partner : seat;
            Player receiver = order.fromPartner() ? seat : partner;
            String faction = sender.getFaction();
            if (order.type() == ItemType.PROMISSORY) {
                Optional<String> note = target.notes(faction).stream()
                        .filter(detail -> !current.notes(faction).contains(detail))
                        .findFirst();
                if (note.isPresent()) return Optional.of(new Missing(sender, receiver, order.type(), 1, note.get()));
                continue;
            }
            int remaining = target.total(faction, order.type()) - current.total(faction, order.type());
            if (remaining > 0) return Optional.of(new Missing(sender, receiver, order.type(), remaining, ""));
        }
        return Optional.empty();
    }

    private static boolean buildable(Player seat, Player partner, Deal target) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        if (!DealItem.tradable(theirs) || target.notes(ours).size() > 1) return false;
        return target.items().stream().allMatch(item -> {
            if (item.isFrom(theirs) && item.isTo(ours)) return REQUESTABLE.contains(item.type());
            if (!item.isFrom(ours) || !item.isTo(theirs)) return false;
            if (item.type() == ItemType.PROMISSORY) return StringUtils.isNumeric(item.detail());
            return REQUESTABLE.contains(item.type());
        });
    }

    static String scopeKey(Game game, Purpose purpose, Deal target) {
        return TURN_BOUND.contains(purpose) ? TradeLegality.window(game) : TradeLegality.openKey(game, purpose, target);
    }

    private static boolean claimCleanup(AiTurnContext context, Player partner) {
        String key = CLEANUP_KEY + context.game().getRound() + SEPARATOR + partner.getFaction();
        if (context.memory().has(key)) return false;
        context.memory().put(key, String.valueOf(context.now()));
        return true;
    }

    private static void writeCleanup(AiTurnContext context, Player partner) {
        Draft.fresh(
                        partner.getFaction(),
                        Purpose.CLEANUP,
                        context.now(),
                        false,
                        scopeKey(context.game(), Purpose.CLEANUP, Deal.EMPTY),
                        Deal.EMPTY)
                .write(context.memory());
    }

    private static void logStart(AiTurnContext context) {
        List<Long> starts = new ArrayList<>(recentStarts(context));
        starts.add(context.now());
        TradeMemory.rewrite(
                context.memory(), HOUR_KEY, starts.stream().map(String::valueOf).collect(Collectors.joining(TIMES)));
    }

    private static List<Long> recentStarts(AiTurnContext context) {
        return Arrays.stream(StringUtils.split(context.memory().get(HOUR_KEY).orElse(""), TIMES))
                .map(TradeMemory::parseLong)
                .filter(time -> time.isPresent() && context.now() - time.getAsLong() < HOUR_MILLIS)
                .map(OptionalLong::getAsLong)
                .toList();
    }

    private static String describe(Missing missing) {
        String what = missing.type() == ItemType.PROMISSORY
                ? "a promissory note"
                : missing.remaining() + " " + missing.type().token();
        return what + " from " + missing.sender().getFaction() + " to "
                + missing.receiver().getFaction();
    }
}
