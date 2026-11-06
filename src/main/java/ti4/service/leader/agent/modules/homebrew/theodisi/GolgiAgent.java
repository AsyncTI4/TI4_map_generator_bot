package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;
import ti4.service.actioncard.ForceGiveActionCardService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class GolgiAgent extends AgentModule<Player> {

    static final String ID = "morphaagent";
    private static final String CHOOSE_TARGET = "useMorphaAgent";
    private static final String LEGACY_SELECT_TARGET = "morphaAgentTarget_";
    private static final String TAKE_DISCARD = "morphaAgentTakeDiscard_";
    private static final String SEPARATOR = "|";

    public GolgiAgent() {
        super(ID, "Golgi, the Morpha");
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + CHOOSE_TARGET,
                useLabel(player, ID, "Golgi", ", the Morpha Agent"),
                FactionEmojis.morpha);
    }

    @ButtonHandler(CHOOSE_TARGET)
    static void chooseTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)) {
            return;
        }
        List<Button> buttons = targetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no other players to select.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "Choose a player for Golgi, the Morpha agent.", buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    static List<Button> targetButtons(Game game, Player holder) {
        return game.getRealPlayersExcludingThis(holder).stream()
                .map(target -> Buttons.green(
                        ownedButtonId(holder, ID, target.getFaction()),
                        target.getFactionNameOrColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_SELECT_TARGET)
    static void legacySelectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_SELECT_TARGET.length()));
    }

    @ButtonHandler(TAKE_DISCARD)
    static void takeDiscardedActionCard(ButtonInteractionEvent event, String buttonID, Game game, Player target) {
        String[] payload = buttonID.substring(TAKE_DISCARD.length()).split("\\|", 2);
        Player agentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (agentOwner == null || !target.getFaction().equals(game.getStoredValue(targetKey(agentOwner)))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                discardPrompt(target),
                () -> discardButtons(game, target, agentOwner),
                List.of(),
                discardPrefix(target, agentOwner))) {
            return;
        }
        Integer actionCardNumber = parseNumber(payload[1]);
        if (actionCardNumber == null || !game.pickActionCard(target.getUserID(), actionCardNumber)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ForceGiveActionCardService.sendGiveACButtons(
                agentOwner,
                target,
                game,
                target.getRepresentationNoPing() + ", choose 1 Action Card to give to "
                        + agentOwner.getRepresentationNoPing()
                        + " for Golgi, the Morpha agent.");
        game.removeStoredValue(targetKey(agentOwner));
        ButtonHelper.deleteMessage(event);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (!game.getRealPlayersExcludingThis(user).contains(target)) {
            return Optional.of("Golgi must target another player");
        }
        if (discardButtons(game, target, user).isEmpty()) {
            return Optional.of("There are no non-purged Action Cards in the discard pile");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player owner = use.user();
        Player target = use.payload();
        game.setStoredValue(targetKey(owner), target.getFaction());
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                discardPrompt(target),
                discardButtons(game, target, owner),
                List.of(),
                discardPrefix(target, owner));
        return AgentOutcome.none().deletingPressedMessage();
    }

    private static String discardPrompt(Player target) {
        return target.getRepresentationNoPing()
                + ", choose the discarded Action Card to take for Golgi, the Morpha agent.";
    }

    private static String discardPrefix(Player target, Player agentOwner) {
        return target.factionButtonChecker() + TAKE_DISCARD + agentOwner.getFaction() + SEPARATOR;
    }

    static List<Button> discardButtons(Game game, Player target, Player agentOwner) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : game.getDiscardActionCards().entrySet()) {
            if (game.getDiscardACStatus().get(entry.getKey()) == ACStatus.purged) {
                continue;
            }
            ActionCardModel model = Mapper.getActionCard(entry.getKey());
            String name = model == null ? entry.getKey() : model.getName();
            buttons.add(Buttons.green(discardPrefix(target, agentOwner) + entry.getValue(), "Take " + name));
        }
        return buttons;
    }

    private static Integer parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String targetKey(Player agentOwner) {
        return "morphaAgentTarget_" + agentOwner.getFaction();
    }
}
