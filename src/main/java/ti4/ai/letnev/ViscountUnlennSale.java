package ti4.ai.letnev;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.SpaceOutlook;
import ti4.ai.tactical.CombatRules;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ViscountUnlennSale {

    public static final String BUY_PREFIX = "unlennBuy_";
    public static final String DECLINE_PREFIX = "unlennNo_";
    public static final int PRICE = 1;
    static final double FAIR_FIGHT_MIN = 0.4;
    static final double FAIR_FIGHT_MAX = 0.6;
    static final long PATIENCE_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final String KEY = "unlennSale|";
    private static final String ROLL_PREFIX = "combatRoll_";
    private static final String SEPARATOR = "_";
    private static final int ROLL_PARTS = 3;
    private static final int OFFER_PARTS = 4;

    public static Optional<AiDecision> offer(AiTurnContext context) {
        if (!ViscountUnlennRules.ownsReadyAgent(context.seat())) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton roll : prompt.enabledButtons()) {
                Optional<AiDecision> post = offerAt(context, prompt, roll);
                if (post.isPresent()) return post;
            }
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> answer(AiTurnContext context) {
        List<AiPrompt> combat = combatPrompts(context);
        Optional<Match> buy = Prompts.owned(combat, context.faction(), id -> id.startsWith(BUY_PREFIX));
        Optional<Match> decline = Prompts.owned(combat, context.faction(), id -> id.startsWith(DECLINE_PREFIX));
        if (buy.isPresent()
                && context.alreadyPressed(buy.get().prompt(), buy.get().button())) return Optional.empty();
        if (buy.isPresent() && wantsToBuy(context, buy.get().button())) {
            return Optional.of(buy.get().press("buy Viscount Unlenn for 1 trade good in a close fight"));
        }
        return decline.filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("decline Viscount Unlenn"));
    }

    public static Optional<AiDecision> holdRoll(AiTurnContext context) {
        Game game = context.game();
        for (AiPrompt prompt : combatPrompts(context)) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.handlerId().startsWith(BUY_PREFIX)) continue;
                String[] parts = button.handlerId().split(SEPARATOR);
                Tile tile = parts.length == OFFER_PARTS ? game.getTileByPosition(parts[1]) : null;
                if (tile == null
                        || !BoardView.hasOwnShips(context.seat(), tile)
                        || !isRound(game, context.faction(), parts)) {
                    continue;
                }
                long until = prompt.createdAtMillis() + PATIENCE_MILLIS;
                if (context.now() < until) {
                    return Optional.of(new AiDecision.Wait(until, "a Viscount Unlenn offer is open"));
                }
            }
        }
        return Optional.empty();
    }

    public static boolean fairFight(double winA, double winB) {
        return winA >= FAIR_FIGHT_MIN && winA <= FAIR_FIGHT_MAX && winB >= FAIR_FIGHT_MIN && winB <= FAIR_FIGHT_MAX;
    }

    static String buyId(String position, int round, Player buyer) {
        return ComponentIdEnvelope.ownedBy(buyer.getFaction()) + BUY_PREFIX + suffix(position, round, buyer);
    }

    static String declineId(String position, int round, Player buyer) {
        return ComponentIdEnvelope.ownedBy(buyer.getFaction()) + DECLINE_PREFIX + suffix(position, round, buyer);
    }

    static String suffix(String position, int round, Player buyer) {
        return position + SEPARATOR + round + SEPARATOR + buyer.getFaction();
    }

    private static Optional<AiDecision> offerAt(AiTurnContext context, AiPrompt prompt, PromptButton roll) {
        Game game = context.game();
        String[] parts = roll.handlerId().split(SEPARATOR);
        if (!roll.isUnowned()
                || !roll.handlerId().startsWith(ROLL_PREFIX)
                || parts.length != ROLL_PARTS
                || !BoardView.SPACE.equals(parts[2])
                || !parts[1].equals(game.getActiveSystem())) {
            return Optional.empty();
        }
        String position = parts[1];
        String key = KEY + position + "|" + game.getRound();
        Tile tile = game.getTileByPosition(position);
        if (tile == null || context.memory().has(key)) return Optional.empty();
        List<Player> fighters = fighters(game, tile);
        if (fighters.size() != 2 || fighters.contains(context.seat())) return Optional.empty();
        int round = CombatRules.tracker(game, fighters.get(0).getFaction(), position, BoardView.SPACE);
        if (round != CombatRules.tracker(game, fighters.get(1).getFaction(), position, BoardView.SPACE)) {
            return Optional.empty();
        }
        if (!closeFight(game, position, fighters)) return Optional.empty();
        List<Player> buyers = fighters.stream()
                .filter(fighter -> !fighter.isNeutral() && fighter.getTg() >= PRICE)
                .toList();
        if (buyers.isEmpty()) return Optional.empty();
        context.memory().put(key, String.valueOf(context.now()));
        return Optional.of(new AiDecision.Post(
                prompt.channelId(), offerText(context.seat(), buyers), offerButtons(position, round, buyers)));
    }

    private static boolean closeFight(Game game, String position, List<Player> fighters) {
        Optional<SpaceOutlook> first = CombatForecast.spaceOutlook(game, fighters.get(0), position);
        Optional<SpaceOutlook> second = CombatForecast.spaceOutlook(game, fighters.get(1), position);
        return first.isPresent()
                && second.isPresent()
                && fairFight(first.get().win(), second.get().win());
    }

    private static List<Player> fighters(Game game, Tile tile) {
        return game.getPlayers().values().stream()
                .filter(player -> player.getColor() != null && BoardView.hasOwnShips(player, tile))
                .toList();
    }

    private static String offerText(Player seller, List<Player> buyers) {
        String addressed = String.join(
                " ", buyers.stream().map(Player::getRepresentationUnfogged).toList());
        return addressed + " " + seller.getFactionEmojiOrColor()
                + " offers Viscount Unlenn, the Letnev agent, for " + PRICE
                + " trade good: one of your ships rolls an extra die this round. The first to buy gets it."
                + " If both decline, the combat goes on.";
    }

    private static List<Button> offerButtons(String position, int round, List<Player> buyers) {
        List<Button> buttons = new ArrayList<>();
        for (Player buyer : buyers) {
            buttons.add(Buttons.green(buyId(position, round, buyer), "Buy for 1 TG (" + buyer.getFaction() + ")"));
            buttons.add(Buttons.gray(declineId(position, round, buyer), "No thanks (" + buyer.getFaction() + ")"));
        }
        return buttons;
    }

    private static boolean wantsToBuy(AiTurnContext context, PromptButton buy) {
        String[] parts = buy.handlerId().split(SEPARATOR);
        return context.seat().getTg() >= PRICE
                && parts.length == OFFER_PARTS
                && isRound(context.game(), context.faction(), parts);
    }

    private static boolean isRound(Game game, String faction, String[] parts) {
        return StringUtils.isNumeric(parts[2])
                && CombatRules.tracker(game, faction, parts[1], BoardView.SPACE) == Integer.parseInt(parts[2]);
    }

    private static List<AiPrompt> combatPrompts(AiTurnContext context) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .toList();
    }
}
