package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class RevenantArdentiaAgent extends AgentModule<RevenantArdentiaAgent.TokenMove> {

    static final String ID = "revenantardentiaagent";
    private static final String LEGACY_USE = "useRevenantArdentiaAgent_";
    private static final String MOVE_TOKEN = "moveRevenantArdentiaToken_";
    private static final String SEPARATOR = "|";
    private static final int MAX_BUTTONS = 25;

    public record TokenMove(Player tokenOwner, Tile source) {}

    public RevenantArdentiaAgent() {
        super(ID, "Kruth Torrious, the Revenant");
    }

    public static void offerAfterCommandTokenPlaced(Game game, Player tokenOwner, Tile sourceTile) {
        if (game == null || tokenOwner == null || sourceTile == null) {
            return;
        }
        for (Player agentOwner : game.getRealPlayers()) {
            if (agentOwner == tokenOwner || !agentOwner.hasUnexhaustedLeader(ID)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentationNoPing() + ", " + tokenOwner.getRepresentationNoPing()
                            + " placed a command token in " + sourceTile.getRepresentationForButtons(game, agentOwner)
                            + ". You may exhaust **Kruth Torrious, the Revenant agent** to move it.",
                    offerButtons(agentOwner, tokenOwner, sourceTile));
        }
    }

    static List<Button> offerButtons(Player agentOwner, Player tokenOwner, Tile sourceTile) {
        return List.of(
                Buttons.green(
                        ownedButtonId(agentOwner, ID, tokenOwner.getFaction() + SEPARATOR + sourceTile.getPosition()),
                        "Move " + tokenOwner.getColor() + " Command Token",
                        FactionEmojis.revenant),
                Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "No Thanks"));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE.length()));
    }

    @ButtonHandler(MOVE_TOKEN)
    static void moveToken(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] values = buttonID.substring(MOVE_TOKEN.length()).split("\\|", 2);
        Player tokenOwner = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (tokenOwner == null || !agentOwner.hasLeader(ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = destinationButtons(game, agentOwner, tokenOwner);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                declineButtons(agentOwner),
                movePrompt(agentOwner.getRepresentation(), tokenOwner),
                movePrefix(agentOwner, tokenOwner),
                buttonID)) {
            return;
        }
        Tile destinationTile = game.getTileByPosition(values[1]);
        if (destinationTile == null
                || buttons.stream()
                        .noneMatch(
                                button -> button.getCustomId().endsWith(SEPARATOR + destinationTile.getPosition()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        destinationTile.addCC(Mapper.getCCID(tokenOwner.getColor()));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                agentOwner.getRepresentationNoPing() + " moved " + tokenOwner.getRepresentationNoPing()
                        + "'s command token to " + destinationTile.getRepresentationForButtons(game, agentOwner)
                        + " with **Kruth Torrious**.");
        ButtonHelper.deleteMessage(event);
    }

    private static String movePrompt(String agentOwnerName, Player tokenOwner) {
        return agentOwnerName + ", choose where to move " + tokenOwner.getRepresentationNoPing()
                + "'s command token with **Kruth Torrious**.";
    }

    private static String movePrefix(Player agentOwner, Player tokenOwner) {
        return agentOwner.factionButtonChecker() + MOVE_TOKEN + tokenOwner.getFaction() + SEPARATOR;
    }

    private static List<Button> declineButtons(Player agentOwner) {
        return List.of(Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "Decline"));
    }

    static List<Button> destinationButtons(Game game, Player agentOwner, Player tokenOwner) {
        String token = Mapper.getCCID(tokenOwner.getColor());
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> FoWHelper.playerHasUnitsInSystem(agentOwner, tile))
                .filter(tile -> tile.getPlanetUnitHolders().stream().noneMatch(Planet::isLegendary))
                .filter(tile -> !tile.hasCC(token))
                .map(tile -> Buttons.green(
                        movePrefix(agentOwner, tokenOwner) + tile.getPosition(),
                        "Move to " + tile.getRepresentationForButtons(game, agentOwner)))
                .toList();
    }

    @Override
    public Optional<TokenMove> decode(Game game, Player user, String payload) {
        Player tokenOwner = game.getPlayerFromColorOrFaction(StringUtils.substringBeforeLast(payload, SEPARATOR));
        Tile source = game.getTileByPosition(StringUtils.substringAfterLast(payload, SEPARATOR));
        if (tokenOwner == null || source == null) {
            return Optional.empty();
        }
        return Optional.of(new TokenMove(tokenOwner, source));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, TokenMove move) {
        if (move.tokenOwner() == user) {
            return Optional.of("Kruth Torrious only moves other players' command tokens");
        }
        if (!move.source().hasCC(Mapper.getCCID(move.tokenOwner().getColor()))) {
            return Optional.of("That command token is no longer in that system");
        }
        if (destinationButtons(game, user, move.tokenOwner()).isEmpty()) {
            return Optional.of("There are no eligible systems for that command token");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<TokenMove> use) {
        Game game = use.game();
        Player owner = use.user();
        Player tokenOwner = use.payload().tokenOwner();
        RemoveCommandCounterService.fromTile(
                tokenOwner.getColor(), use.payload().source(), game);
        List<Button> buttons = destinationButtons(game, owner, tokenOwner);
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(declineButtons(owner));
        if (displayedButtons.size() > MAX_BUTTONS) {
            displayedButtons = NewStuffHelper.buttonPagination(
                    buttons, declineButtons(owner), movePrefix(owner, tokenOwner), MAX_BUTTONS, 0, false);
        }
        return AgentOutcome.of(Message.addressedTo(owner, movePrompt("", tokenOwner), displayedButtons)
                        .forEveryone())
                .deletingPressedMessage();
    }
}
