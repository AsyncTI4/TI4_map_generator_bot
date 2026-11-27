package ti4.service.leader.agent.modules.homebrew.goose;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir.VaettirAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class VaettirAgent extends AgentModule<Player> {

    private static final String ID = "vaettiragent";
    private static final String SELECT_OTHER_TARGET = "selectOtherVaettirAgentTarget";
    private static final String USE_OTHER = "useOtherVaettirAgent_";
    private static final String SELECT_PLANET = "selectVaettirAgentPlanet_";
    private static final String SELECT_ROOT = "selectVaettirAgentRoot_";

    public VaettirAgent() {
        super(ID, "The Vigorous Sapling");
    }

    public static void offerAtEndOfTacticalAction(Game game, Player activePlayer) {
        Player agentOwner = game.getPlayerFromLeader(ID);
        if (activePlayer == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(ID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer == agentOwner ? agentOwner.getCorrectChannel() : agentOwner.getCardsInfoThread(),
                agentOwner.getRepresentationNoPing()
                        + ", you may use _The Vigorous Sapling, the Vaettir agent_ for the active player.",
                List.of(
                        Buttons.green(
                                ownedButtonId(agentOwner, ID, activePlayer.getFaction()),
                                useLabel(agentOwner, ID, "Vaettir Agent"),
                                FactionEmojis.vaettir),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    public static void addCardsInfoButton(List<Button> buttons, Player player) {
        if (player.hasUnexhaustedLeader(ID)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_OTHER_TARGET,
                    useLabel(player, ID, "Vaettir Agent"),
                    FactionEmojis.vaettir));
        }
    }

    @ButtonHandler(SELECT_OTHER_TARGET)
    static void selectOtherTarget(ButtonInteractionEvent event, Game game, Player agentOwner) {
        List<Button> buttons = game.getRealPlayersExcludingThis(agentOwner).stream()
                .map(target -> Buttons.gray(
                        agentOwner.factionButtonChecker() + USE_OTHER + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                agentOwner.getRepresentationNoPing()
                        + ", please choose the player on whom you wish to use _The Vigorous Sapling, the Vaettir agent_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_OTHER)
    static void useOnOtherPlayer(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_OTHER.length()));
        if (target == null || target == agentOwner) {
            return;
        }
        AgentLifecycle.use(game, agentOwner, ID, target.getFaction(), event);
    }

    @ButtonHandler(SELECT_PLANET)
    static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_PLANET.length()).split("\\|", 2);
        Tile activeSystem = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet =
                activeSystem == null || payload.length != 2 ? null : activeSystem.getUnitHolderFromPlanet(payload[1]);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = VaettirAbilityHandler.getRootTypes().stream()
                .filter(rootType -> VaettirAbilityHandler.isRootTokenAvailable(game, rootType))
                .filter(rootType -> isEligibleRootToken(planet, rootType))
                .map(rootType -> Buttons.green(
                        player.factionButtonChecker() + SELECT_ROOT + activeSystem.getPosition() + "|"
                                + planet.getName() + "|" + rootType,
                        "Add " + rootType + " Root Token",
                        FactionEmojis.vaettir))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the Root token to add to "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_ROOT)
    static void placeRootAndProduce(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_ROOT.length()).split("\\|", 3);
        Tile activeSystem = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Planet planet =
                activeSystem == null || payload.length != 3 ? null : activeSystem.getUnitHolderFromPlanet(payload[1]);
        if (planet == null
                || !VaettirAbilityHandler.getRootTypes().contains(payload[2])
                || !isEligibleRootToken(planet, payload[2])
                || !VaettirAbilityHandler.isRootTokenAvailable(game, payload[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!planet.addToken("attachment_root_" + payload[2] + ".png")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        VaettirAbilityHandler.syncOldGrowthRootCards(game);
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, activeSystem, "tacticalAction", "place");
        int productionLimit = VaettirAbilityHandler.getPlanetTraitCount(planet);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " added the " + payload[2] + " Root token to "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " with _The Vigorous Sapling, the Vaettir agent_ and may produce up to " + productionLimit
                        + " units in "
                        + activeSystem.getRepresentationForButtons(game, player) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Tile activeSystem = use.game().getTileByPosition(use.game().getActiveSystem());
        if (activeSystem == null) {
            return AgentOutcome.none().deletingPressedMessage();
        }
        Player target = use.payload();
        List<Button> buttons = activeSystem.getPlanetUnitHolders().stream()
                .map(Planet::getName)
                .map(planet -> Buttons.green(
                        target.factionButtonChecker() + SELECT_PLANET + activeSystem.getPosition() + "|" + planet,
                        Helper.getPlanetRepresentation(planet, use.game())))
                .toList();
        if (buttons.isEmpty()) {
            return AgentOutcome.none().deletingPressedMessage();
        }
        return AgentOutcome.of(Message.addressedTo(
                                target,
                                ", please choose a planet in the active system for _The Vigorous Sapling, the Vaettir agent_.",
                                buttons)
                        .inCorrectChannel())
                .deletingPressedMessage();
    }

    private static boolean isEligibleRootToken(Planet planet, String rootType) {
        return !planet.getPlanetTypes().contains(rootType)
                && !planet.getTokenList().contains("attachment_root_" + rootType + ".png");
    }
}
