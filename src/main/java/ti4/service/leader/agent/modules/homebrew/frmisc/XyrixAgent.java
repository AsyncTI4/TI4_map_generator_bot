package ti4.service.leader.agent.modules.homebrew.frmisc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class XyrixAgent extends AgentModule<XyrixAgent.Gift> {
    public static final String ID = "xyrixagent";
    private static final String SELECT_TARGET = "xyrixAgentSelectTarget";
    private static final String OFFER_ACTION_CARD = "xyrixAgentOfferActionCard_";
    private static final String GIVE_ACTION_CARD = "xyrixAgentGiveActionCard_";
    private static final String DECLINE = "xyrixAgentDecline_";
    private static final String SEPARATOR = "|";

    public record Gift(Player target, String actionCard) {}

    public XyrixAgent() {
        super(ID, "Caerax the Curator");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + SELECT_TARGET,
                useLabel(holder, ID, "Caerax the Curator"),
                FactionEmojis.xyrix);
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(ButtonInteractionEvent event, Game game, Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(holder).stream()
                .map(target -> FoWHelper.fogSafeTargetButton(
                        holder.factionButtonChecker() + OFFER_ACTION_CARD + target.getFaction(),
                        "gray",
                        target,
                        holder))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no other players to target.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentationNoPing() + ", choose a player for _Caerax the Curator_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(OFFER_ACTION_CARD)
    static void offerActionCard(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(OFFER_ACTION_CARD.length()));
        if (target == null || target == holder) {
            return;
        }
        List<Button> cards = actionCardButtons(holder, target);
        if (cards.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), targetName(game, target) + " has no action cards to give.");
            return;
        }
        NewStuffHelper.sendPaginated(
                target.getCardsInfoThread(),
                actionCardPrompt(game, holder, target),
                cards,
                List.of(Buttons.red(target.factionButtonChecker() + DECLINE + holder.getFaction(), "Decline")),
                actionCardPrefix(holder, target));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GIVE_ACTION_CARD)
    static void giveActionCard(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        if (!buttonID.contains(SEPARATOR)) {
            return;
        }
        String[] payload = buttonID.substring(GIVE_ACTION_CARD.length()).split("\\|", 2);
        Player holder = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (holder == null || !holder.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> cards = actionCardButtons(holder, target);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                target.getCardsInfoThread(),
                cards,
                List.of(Buttons.red(target.factionButtonChecker() + DECLINE + holder.getFaction(), "Decline")),
                actionCardPrompt(game, holder, target),
                actionCardPrefix(holder, target),
                buttonID)) {
            return;
        }
        Integer cardIndex = parseCardIndex(payload[1]);
        if (cardIndex == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AgentLifecycle.use(game, holder, ID, target.getFaction() + SEPARATOR + cardIndex, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE)
    static void decline(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        Player holder = game.getPlayerFromColorOrFaction(buttonID.substring(DECLINE.length()));
        if (holder != null && holder.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " declined " + agentName(game) + "'s offer.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @Override
    public Optional<Gift> decode(Game game, Player user, String payload) {
        String[] values = payload.split("\\|", 2);
        if (values.length != 2) {
            return Optional.empty();
        }
        Player target = AgentTargets.player(game, values[0]).orElse(null);
        Integer cardIndex = parseCardIndex(values[1]);
        if (target == null || cardIndex == null) {
            return Optional.empty();
        }
        String actionCard = target.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue().equals(cardIndex))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        return Optional.ofNullable(actionCard).map(card -> new Gift(target, card));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Gift gift) {
        if (gift.target() == user) {
            return Optional.of("Caerax must target another player");
        }
        if (!gift.target().getActionCards().containsKey(gift.actionCard())) {
            return Optional.of(gift.target().getFactionEmojiOrColor() + " no longer has that action card");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Gift> use) {
        Player holder = use.user();
        Player target = use.payload().target();
        ActionCardHelper.sendActionCard(
                use.event(), use.game(), target, holder, use.payload().actionCard());
        List<Button> buttons = TeHelperActionCards.getReadiedStrategyCardSecondaryButtons(use.game(), target);
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", gave an action card to " + targetName(use.game(), holder) + " with "
                        + use.agentNameFor(target)
                        + ". They may now resolve the secondary ability of a readied or unchosen strategy card without spending a command token.",
                buttons));
    }

    private static List<Button> actionCardButtons(Player holder, Player target) {
        return target.getActionCards().entrySet().stream()
                .map(entry -> Buttons.green(
                        actionCardPrefix(holder, target) + entry.getValue(),
                        Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
    }

    private static String actionCardPrompt(Game game, Player holder, Player target) {
        return target.getRepresentationNoPing() + ", you may give 1 action card to "
                + targetName(game, holder)
                + " with " + agentName(game)
                + ". If you do, you may resolve the secondary ability of a readied or unchosen strategy card without spending a command token.";
    }

    private static String targetName(Game game, Player player) {
        return game.isFowMode() ? player.getColor() : player.getRepresentationNoPing();
    }

    private static String agentName(Game game) {
        return game.isFowMode() ? "an agent" : "_Caerax the Curator_";
    }

    private static String actionCardPrefix(Player holder, Player target) {
        return target.factionButtonChecker() + GIVE_ACTION_CARD + holder.getFaction() + SEPARATOR;
    }

    private static Integer parseCardIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
