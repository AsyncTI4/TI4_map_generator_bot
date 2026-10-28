package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class OblivionAgent extends AgentModule<Player> {

    static final String ID = "oblivionagent";
    private static final String LEGACY_USE = "useOblivionAgent";
    private static final String CHOOSE_TARGET = "useOblivionAgent_other";
    private static final String LEGACY_TARGET = "oblivionAgentTarget_";
    private static final String ADD_TOKEN = "addOblivionFrontierToken_";
    private static final String DISCARD = "discardPeekedFrontier_";

    public OblivionAgent() {
        super(ID, "Avaris the Seer, the Oblivion");
    }

    public static Button startTurnButton(Player holder) {
        return Buttons.gray(ownedButtonId(holder, ID), useLabel(holder, ID, "Avaris the Seer"), FactionEmojis.oblivion);
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + CHOOSE_TARGET,
                useLabel(holder, ID, "Avaris the Seer", " on someone else"),
                FactionEmojis.oblivion);
    }

    @ButtonHandler(CHOOSE_TARGET)
    static void chooseTarget(ButtonInteractionEvent event, Game game, Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                "Please choose the target for Avaris the Seer, the Oblivion agent.",
                targetButtons(game, holder));
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
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, "");
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_TARGET)
    static void legacyTarget(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_TARGET.length()));
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (frontierTokenButtons(game, target).isEmpty()) {
            return Optional.of("There are no eligible systems for a frontier token");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        AgentOutcome outcome = AgentOutcome.of(Message.addressedTo(
                target,
                ", please choose the system in which to place a frontier token.",
                NewStuffHelper.buttonPagination(
                        frontierTokenButtons(use.game(), target), target.factionButtonChecker() + ADD_TOKEN, 0)));
        return target == use.user() ? outcome : outcome.deletingPressedMessage();
    }

    @ButtonHandler(ADD_TOKEN)
    static void placeToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = player.getRepresentation() + ", please choose the system in which to place a frontier token.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                frontierTokenButtons(game, player),
                message,
                player.factionButtonChecker() + ADD_TOKEN,
                buttonID)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        Tile tile = game.getTileByPosition(buttonID.replace(ADD_TOKEN, ""));
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to locate that tile.");
            return;
        }
        tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Frontier token has been placed in " + tile.getRepresentation()
                        + ". The top card of the frontier deck has been sent to your `#cards-info` thread.");
        peekAtFrontierDeck(player, game);
    }

    private static void peekAtFrontierDeck(Player player, Game game) {
        List<String> frontierDeck = game.getExploreDeck(Constants.FRONTIER);
        if (frontierDeck.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "The frontier deck is empty.");
            return;
        }
        String cardId = frontierDeck.getFirst();
        ExploreModel card = Mapper.getExplore(cardId);
        List<Button> buttons = List.of(
                Buttons.red(player.factionButtonChecker() + DISCARD + cardId, "Discard"),
                Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you looked at the top card of the " + ExploreEmojis.Frontier
                        + " frontier deck and saw _" + card.getName() + "_.",
                List.of(card.getRepresentationEmbed()),
                buttons);
    }

    @ButtonHandler(DISCARD)
    static void discardFrontier(ButtonInteractionEvent event, Game game, String buttonID) {
        String cardId = buttonID.replace(DISCARD, "");
        List<String> frontierDeck = game.getExploreDeck(Constants.FRONTIER);
        if (!frontierDeck.isEmpty() && cardId.equals(frontierDeck.getFirst())) {
            game.discardExplore(cardId);
        }
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> frontierTokenButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String frontierToken = Mapper.getTokenID(Constants.FRONTIER);
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().isEmpty()
                    && Mapper.getFrontierTileIds().contains(tile.getTileID())
                    && !tile.getSpaceUnitHolder().getTokenList().contains(frontierToken)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + ADD_TOKEN + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }
}
