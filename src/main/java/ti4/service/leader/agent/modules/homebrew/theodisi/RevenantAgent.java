package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class RevenantAgent extends AgentModule<RevenantAgent.Readying> {

    static final String ID = "revenantagent";
    private static final String SELECT_TARGET = "selectRevenantAgentTarget";
    private static final String OFFER_TECHS = "useRevenantAgentOn_";
    private static final String READY_TECH = "readyTechWithRevBase_";
    private static final String OWNER_SEPARATOR = "~";
    private static final String SEPARATOR = "|";

    public record Readying(Player target, TechnologyModel tech) {}

    public RevenantAgent() {
        super(ID, "Xythis the Whispering, the Revenant");
    }

    public static void offerAfterExhaustingTech(Player player, String techId) {
        TechnologyModel tech = Mapper.getTech(techId);
        if (player == null || tech == null || !player.hasUnexhaustedLeader(ID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may exhaust Xythis the Whispering, the Revenant agent, to ready "
                        + tech.getNameRepresentation() + ".",
                selfReadyButtons(player, tech));
    }

    static List<Button> selfReadyButtons(Player player, TechnologyModel tech) {
        return List.of(
                Buttons.green(
                        ownedButtonId(player, ID, player.getFaction() + SEPARATOR + tech.getAlias()),
                        "Ready " + tech.getNameRepresentation()),
                Buttons.red("deleteButtons", "Decline"));
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + SELECT_TARGET, "Use Revenant Agent", FactionEmojis.revenant);
    }

    @ButtonHandler(SELECT_TARGET)
    static void offerTargets(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xythis the Whispering, the Revenant agent, is no longer available.");
            return;
        }
        List<Button> targets = game.getRealPlayersExcludingThis(player).stream()
                .filter(target ->
                        target.getExhaustedTechs().stream().anyMatch(techId -> Mapper.getTech(techId) != null))
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + OFFER_TECHS + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
        if (targets.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No other player has an exhausted technology.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose a player on whom to use Xythis the Whispering, the Revenant agent.",
                targets);
    }

    @ButtonHandler(OFFER_TECHS)
    static void offerTechs(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (!agentOwner.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xythis the Whispering, the Revenant agent, is no longer available.");
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(OFFER_TECHS.length()));
        if (target == null || target == agentOwner) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }
        List<Button> techButtons = targetReadyButtons(agentOwner, target);
        if (techButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player no longer has an exhausted technology.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose the technology that Xythis the Whispering, the Revenant agent, should ready.",
                techButtons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent technology choices to " + target.getRepresentationUnfoggedNoPing() + ".");
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> targetReadyButtons(Player agentOwner, Player target) {
        return target.getExhaustedTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .map(tech -> Buttons.green(
                        target.factionButtonChecker()
                                + READY_TECH
                                + agentOwner.getFaction()
                                + OWNER_SEPARATOR
                                + tech.getAlias(),
                        "Ready " + tech.getNameRepresentation()))
                .toList();
    }

    @ButtonHandler(READY_TECH)
    static void readyTech(ButtonInteractionEvent event, Game game, Player presser, String buttonID) {
        String rest = buttonID.substring(READY_TECH.length());
        String readying = presser.getFaction() + SEPARATOR + StringUtils.substringAfter(rest, OWNER_SEPARATOR);
        if (!rest.contains(OWNER_SEPARATOR)) {
            // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
            AgentLifecycle.pressLegacyButton(event, game, presser, ID, presser.getFaction() + SEPARATOR + rest);
            return;
        }
        Player agentOwner = game.getPlayerFromColorOrFaction(StringUtils.substringBefore(rest, OWNER_SEPARATOR));
        if (agentOwner == null) {
            return;
        }
        if (AgentLifecycle.use(game, agentOwner, ID, readying, event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public Optional<Readying> decode(Game game, Player user, String payload) {
        Player target = game.getPlayerFromColorOrFaction(StringUtils.substringBeforeLast(payload, SEPARATOR));
        TechnologyModel tech = Mapper.getTech(StringUtils.substringAfterLast(payload, SEPARATOR));
        if (target == null || tech == null) {
            return Optional.empty();
        }
        return Optional.of(new Readying(target, tech));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Readying readying) {
        if (!readying.target().getExhaustedTechs().contains(readying.tech().getAlias())) {
            return Optional.of(readying.tech().getName() + " is no longer exhausted");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Readying> use) {
        Player owner = use.user();
        Player target = use.payload().target();
        TechnologyModel tech = use.payload().tech();
        target.refreshTech(tech.getAlias());
        String readied = target.getRepresentationUnfogged() + " readied " + tech.getNameRepresentation() + " using ";
        return AgentOutcome.notifyInFog(
                use.game(), target, readied + use.agentNameFor(target) + ".", owner, readied + use.agentName() + ".");
    }
}
