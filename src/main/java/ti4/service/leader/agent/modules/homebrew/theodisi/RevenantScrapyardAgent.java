package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class RevenantScrapyardAgent extends AgentModule<Player> {

    static final String ID = "revenantscrapyardagent";
    private static final String SELECT_TARGET = "selectRevenantTarget";
    private static final String LEGACY_RESOLVE_TARGET = "resolveRevenantTarget_";
    private static final String LEGACY_PRODUCE_ON_SELF = "produceUsingRevScrapyardAgent";
    private static final String PAGE_SYSTEMS = "pageRevenantScrapyardSystems_";
    private static final Pattern PAGE = Pattern.compile("_page");
    private static final int MAX_BUTTONS = 25;
    private static final String LABEL = "Use Revenant Scrapyard Agent";

    public RevenantScrapyardAgent() {
        super(ID, "Thrash \"Feral\" Burner, the Revenant");
    }

    public static Button startTurnButton(Player player) {
        return Buttons.gray(ownedButtonId(player, ID), LABEL, FactionEmojis.revenant);
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + SELECT_TARGET, LABEL, FactionEmojis.revenant);
    }

    @ButtonHandler(SELECT_TARGET)
    static void offerTargets(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (!agentOwner.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Thrash \"Feral\" Burner, the Revenant of Scrapyard agent, is no longer available.");
            return;
        }
        List<Button> buttons = targetButtons(game, agentOwner);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no eligible players.");
            return;
        }
        String message = agentOwner.getRepresentation() + ", choose the player who will produce 1 unit with "
                + "Thrash \"Feral\" Burner, the Revenant of Scrapyard agent.";
        String prefix = agentOwner.factionButtonChecker() + SELECT_TARGET;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, agentOwner.getCorrectChannel(), buttons, extraButtons, message, prefix, buttonID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                agentOwner.getCorrectChannel(), message, firstPage(buttons, extraButtons, prefix));
    }

    static List<Button> targetButtons(Game game, Player agentOwner) {
        return game.getRealPlayersExcludingThis(agentOwner).stream()
                .map(target -> Buttons.green(
                        ownedButtonId(agentOwner, ID, target.getFaction()),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_RESOLVE_TARGET)
    static void legacyResolveTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_RESOLVE_TARGET.length()));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_PRODUCE_ON_SELF)
    static void legacyProduceOnSelf(ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, "");
    }

    @ButtonHandler(PAGE_SYSTEMS)
    static void pageSystems(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String targetFaction = PAGE.split(buttonID.substring(PAGE_SYSTEMS.length()), 2)[0];
        if (!target.getFaction().equals(targetFaction)) {
            return;
        }
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                target.getCorrectChannel(),
                systemButtons(game, target),
                declineButtons(),
                systemPrompt(target),
                systemPagePrefix(target),
                buttonID);
    }

    public static List<Button> systemButtons(Game game, Player target) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(target, tile))
                .map(tile -> Buttons.green(
                        target.factionButtonChecker() + "produceOneUnitInTile_" + tile.getPosition()
                                + "_revenantScrapyard",
                        tile.getRepresentationForButtons(game, target)))
                .toList();
    }

    private static String systemPrompt(Player target) {
        return target.getRepresentation()
                + ", choose a system containing 1 or more of your ships in which to produce 1 unit due to Thrash \"Feral\" Burner, the Revenant of Scrapyard agent.";
    }

    private static String systemPagePrefix(Player target) {
        return target.factionButtonChecker() + PAGE_SYSTEMS + target.getFaction() + "_";
    }

    private static List<Button> declineButtons() {
        return List.of(Buttons.red("deleteButtons", "Decline"));
    }

    private static List<Button> firstPage(List<Button> buttons, List<Button> extraButtons, String prefix) {
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > MAX_BUTTONS) {
            return NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, MAX_BUTTONS, 0, false);
        }
        return displayedButtons;
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (systemButtons(game, target).isEmpty()) {
            return Optional.of(target.getRepresentationNoPing() + " does not have any ships on the game board");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> buttons = firstPage(systemButtons(use.game(), target), declineButtons(), systemPagePrefix(target));
        AgentOutcome outcome = AgentOutcome.of(Message.plain(target, systemPrompt(target), buttons));
        return target == use.user() ? outcome : outcome.deletingPressedMessage();
    }
}
