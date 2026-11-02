package ti4.service.leader.agent.modules.homebrew.bluereverie;

import java.util.Comparator;
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
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.tactical.movement.BelkoseaAgentService;

public final class BelkoseaAgent extends AgentModule<Tile> {

    static final String ID = "belkoseaagent";
    private static final String CHOOSE_SYSTEM = "useBelkoseaAgent";
    private static final String LEGACY_SELECT_SYSTEM = "selectBelkoseaAgentSystem_";

    public BelkoseaAgent() {
        super(ID, "Razalka Gris, the Belkosea");
    }

    public static Button button(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + CHOOSE_SYSTEM,
                useLabel(holder, ID, "Belkosea Agent"),
                FactionEmojis.belkosea);
    }

    @ButtonHandler(CHOOSE_SYSTEM)
    static void offerSystems(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Razalka Gris must be ready to use this ability.");
            return;
        }

        List<Button> buttons = systemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the system whose other players' ships will not affect the active player's movement.";
        String prefix = player.factionButtonChecker() + CHOOSE_SYSTEM + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, event.getComponentId())) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(buttons, null, prefix, 24, 0, true));
        ButtonHelper.deleteTheOneButton(event);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_SELECT_SYSTEM)
    static void legacySelectSystem(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_SELECT_SYSTEM.length()));
    }

    @Override
    public Optional<Tile> decode(Game game, Player user, String payload) {
        return Optional.ofNullable(game.getTileByPosition(payload));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Tile tile) {
        if (game.getActivePlayer() == null || tile.getTileModel().isHyperlane()) {
            return Optional.of("That system is no longer eligible");
        }
        return Optional.empty();
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Tile> use) {
        Player activePlayer = use.game().getActivePlayer();
        return " has exhausted " + use.agentName() + " for " + activePlayer.getRepresentationNoPing()
                + ". Other players' ships in "
                + use.payload().getRepresentationForButtons(use.game(), use.user())
                + " do not affect that player's movement until the end of movement.";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Tile> use) {
        BelkoseaAgentService.ignoreOtherShips(use.game(), use.game().getActivePlayer(), use.payload());
        return AgentOutcome.none().deletingPressedMessage();
    }

    private static List<Button> systemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile ->
                        !tile.getTileModel().isHyperlane() && FoWHelper.knowsTile(game, player, tile.getPosition()))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.gray(
                        ownedButtonId(player, ID, tile.getPosition()), tile.getRepresentationForButtons(game, player)))
                .toList();
    }
}
