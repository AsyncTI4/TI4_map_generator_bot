package ti4.service.leader.agent.modules.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

public final class QhetAgent extends AgentModule<Player> {

    static final String ID = "qhetagent";
    private static final String SELECT_TECH = "selectQhetAgentTech_";
    private static final String STATE = "qhetAgentTech_";

    public QhetAgent() {
        super(ID, "Zhroth Khuvad, the Qhet");
    }

    public static Button button(Player holder) {
        return useButton(holder, ID, "Qhet Agent", FactionEmojis.qhet);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler("useQhetAgent")
    static void legacyUse(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, "");
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.ofNullable(game.getActivePlayer());
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player activePlayer) {
        if (eligibleUnitUpgrades(game, activePlayer).isEmpty()) {
            return Optional.of("The active player has no eligible non-faction unit upgrades");
        }
        return Optional.empty();
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has exhausted " + use.agentName() + " for " + use.payload().getRepresentationNoPing() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player activePlayer = use.payload();
        sendUnitUpgradeButtons(
                activePlayer.getCorrectChannel(), use.user(), activePlayer, eligibleUnitUpgrades(game, activePlayer));
        return AgentOutcome.none();
    }

    @ButtonHandler(SELECT_TECH)
    static void selectTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TECH.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player agentOwner = game.getPlayerFromColorOrFaction(payload[0]);
        String techId = payload[1];
        List<TechnologyModel> upgrades = eligibleUnitUpgrades(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a non-faction unit upgrade to gain until this tactical action ends.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                unitUpgradeButtons(player, agentOwner, upgrades),
                message,
                player.factionButtonChecker() + SELECT_TECH + payload[0] + "|",
                buttonID)) {
            return;
        }
        TechnologyModel tech = Mapper.getTech(techId);
        if (agentOwner == null || tech == null || !upgrades.contains(tech)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit upgrade is no longer eligible.");
            return;
        }

        PlayerTechService.addTech(event, game, player, techId);
        game.setStoredValue(stateKey(agentOwner), game.getActiveSystem() + "|" + player.getFaction() + "|" + techId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained " + tech.getNameRepresentation()
                        + " with Zhroth Khuvad. It returns to the technology deck at the end of this tactical action.");
    }

    public static void returnTemporaryUnitUpgrades(Game game) {
        for (Player agentOwner : game.getRealPlayers()) {
            String[] state = game.getStoredValue(stateKey(agentOwner)).split("\\|", 3);
            if (state.length != 3 || !state[0].equals(game.getActiveSystem())) {
                continue;
            }
            game.removeStoredValue(stateKey(agentOwner));
            Player player = game.getPlayerFromColorOrFaction(state[1]);
            if (player == null || !player.hasTech(state[2])) {
                continue;
            }
            player.removeTech(state[2]);
            TechnologyModel tech = Mapper.getTech(state[2]);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " returned "
                            + (tech == null ? state[2] : tech.getNameRepresentation())
                            + " to the technology deck after Zhroth Khuvad's effect.");
        }
    }

    private static List<TechnologyModel> eligibleUnitUpgrades(Game game, Player player) {
        return ListTechService.getAllNonFactionUnitUpgradeTech(game).stream()
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .toList();
    }

    private static void sendUnitUpgradeButtons(
            MessageChannel channel, Player agentOwner, Player activePlayer, List<TechnologyModel> upgrades) {
        String message = activePlayer.getRepresentation()
                + ", choose a non-faction unit upgrade to gain until this tactical action ends.";
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                message,
                NewStuffHelper.buttonPagination(
                        unitUpgradeButtons(activePlayer, agentOwner, upgrades),
                        activePlayer.factionButtonChecker() + SELECT_TECH + agentOwner.getFaction() + "|",
                        0));
    }

    private static List<Button> unitUpgradeButtons(
            Player activePlayer, Player agentOwner, List<TechnologyModel> upgrades) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : upgrades) {
            buttons.add(Buttons.gray(
                    activePlayer.factionButtonChecker() + SELECT_TECH + agentOwner.getFaction() + "|" + tech.getAlias(),
                    tech.getName(),
                    FactionEmojis.qhet));
        }
        return buttons;
    }

    private static String stateKey(Player agentOwner) {
        return STATE + agentOwner.getFaction();
    }
}
