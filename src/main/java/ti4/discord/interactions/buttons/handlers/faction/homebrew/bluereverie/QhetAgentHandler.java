package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class QhetAgentHandler {
    private static final String AGENT = "qhetagent";
    private static final String USE_AGENT = "useQhetAgent";
    private static final String SELECT_TECH = "selectQhetAgentTech_";
    private static final String STATE = "qhetAgentTech_";

    @ButtonHandler(USE_AGENT)
    public static void useQhetAgent(ButtonInteractionEvent event, Game game, Player agentOwner) {
        Player activePlayer = game.getActivePlayer();
        if (!agentOwner.hasUnexhaustedLeader(AGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Zhroth Khuvad must be ready to use this ability.");
            return;
        }

        List<TechnologyModel> upgrades = getEligibleUnitUpgrades(game, activePlayer);
        if (upgrades.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "The active player has no eligible non-faction unit upgrades.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agentOwner.unsafeGetLeader(AGENT));
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(
                agentOwner.getCorrectChannel(),
                agentOwner.getRepresentation() + " exhausted Zhroth Khuvad, the Qhet agent, for "
                        + activePlayer.getRepresentationNoPing() + ".");
        sendUnitUpgradeButtons(activePlayer.getCorrectChannel(), game, agentOwner, activePlayer, upgrades);
    }

    @ButtonHandler(SELECT_TECH)
    public static void selectQhetAgentTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TECH.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player agentOwner = game.getPlayerFromColorOrFaction(payload[0]);
        String techId = payload[1];
        List<TechnologyModel> upgrades = getEligibleUnitUpgrades(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a non-faction unit upgrade to gain until this tactical action ends.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getUnitUpgradeButtons(player, agentOwner, upgrades),
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

    private static List<TechnologyModel> getEligibleUnitUpgrades(Game game, Player player) {
        return ListTechService.getAllNonFactionUnitUpgradeTech(game).stream()
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .toList();
    }

    private static void sendUnitUpgradeButtons(
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel,
            Game game,
            Player agentOwner,
            Player activePlayer,
            List<TechnologyModel> upgrades) {
        String message = activePlayer.getRepresentation()
                + ", choose a non-faction unit upgrade to gain until this tactical action ends.";
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                message,
                NewStuffHelper.buttonPagination(
                        getUnitUpgradeButtons(activePlayer, agentOwner, upgrades),
                        activePlayer.factionButtonChecker() + SELECT_TECH + agentOwner.getFaction() + "|",
                        0));
    }

    private static List<Button> getUnitUpgradeButtons(
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
