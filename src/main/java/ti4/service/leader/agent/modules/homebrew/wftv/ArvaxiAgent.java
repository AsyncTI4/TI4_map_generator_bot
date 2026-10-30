package ti4.service.leader.agent.modules.homebrew.wftv;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class ArvaxiAgent extends AgentModule<ArvaxiAgent.Retrieval> {

    static final String ID = "arvaxiagent";
    private static final String LEGACY_PICK_TARGET = "arvaxiAgentPickTarget_";
    private static final String OFFER = "arvaxiAgentOffer_";

    public record Retrieval(int actionCardIndex, Player target) {}

    public ArvaxiAgent() {
        super(ID, "Marik, the Wise, the Arvaxi");
    }

    public static void postAgentOfferButtons(Game game, Player discarder, String acID) {
        if (ActionCardHelper.isSabotageOrShatter(acID)) return;
        Integer acIndex = game.getDiscardActionCards().get(acID);
        if (acIndex == null) return;
        String acName = Mapper.getActionCard(acID).getName();
        for (Player p : game.getRealPlayers()) {
            if (!p.hasUnexhaustedLeader(ID)) continue;
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray(
                    p.factionButtonChecker() + OFFER + acIndex + "_" + discarder.getFaction(),
                    useLabel(p, ID, "Arvaxi Agent") + " on " + acName,
                    CardEmojis.getACEmoji(game)));
            buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    p.getCardsInfoThread(),
                    p.getRepresentationUnfoggedNoPing() + ", " + discarder.getFactionNameOrColor() + " discarded _"
                            + acName + "_. You may exhaust Marik, the Wise to allow a player other than **"
                            + discarder.getFactionNameOrColor() + "** to spend 3 influence to retrieve it.",
                    buttons);
        }
    }

    @ButtonHandler(OFFER)
    static void chooseTarget(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String stripped = buttonID.replace(OFFER, "");
        int acIndex = Integer.parseInt(StringUtils.substringBefore(stripped, "_"));
        String discarderFaction = StringUtils.substringAfter(stripped, "_");

        String acID = ActionCardHelper.getDiscardedAcID(game, acIndex);
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Action card no longer in discard pile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String acName = Mapper.getActionCard(acID).getName();
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target.getFaction().equals(discarderFaction)) continue;
            buttons.add(Buttons.green(
                    ownedButtonId(player, ID, String.valueOf(acIndex), target.getFaction()),
                    target.getFactionNameOrColor(),
                    target.fogSafeEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "No eligible players found.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a player to receive _" + acName + "_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_PICK_TARGET)
    static void legacyPickTarget(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_PICK_TARGET.length()));
    }

    @Override
    public Optional<Retrieval> decode(Game game, Player user, String payload) {
        String index = StringUtils.substringBefore(payload, "_");
        if (!StringUtils.isNumeric(index) || index.isEmpty()) {
            return Optional.empty();
        }
        return AgentTargets.player(game, StringUtils.substringAfter(payload, "_"))
                .map(target -> new Retrieval(Integer.parseInt(index), target));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Retrieval retrieval) {
        if (ActionCardHelper.getDiscardedAcID(game, retrieval.actionCardIndex()) == null) {
            return Optional.of("That action card is no longer in the discard pile");
        }
        return Optional.empty();
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Retrieval> use) {
        return " has exhausted " + use.agentName() + " to allow "
                + use.payload().target().getRepresentationNoPing() + " to retrieve _"
                + cardName(use.game(), use.payload()) + "_ " + CardEmojis.getACEmoji(use.game())
                + " from the discard pile.";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Retrieval> use) {
        Game game = use.game();
        Player target = use.payload().target();
        String acName = cardName(game, use.payload());

        game.pickActionCard(target.getUserID(), use.payload().actionCardIndex());
        ActionCardHelper.sendActionCardInfo(game, target);

        List<Button> spendButtons = ButtonHelper.getExhaustButtonsWithTG(game, target, "inf");
        spendButtons.add(Buttons.DONE_EXHAUSTING_PLANETS_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", " + use.agentNameFor(target)
                        + " has been used. You have received _" + acName + "_. Please spend 3 influence.",
                spendButtons);
        return AgentOutcome.none().deletingPressedMessage();
    }

    private static String cardName(Game game, Retrieval retrieval) {
        String acID = ActionCardHelper.getDiscardedAcID(game, retrieval.actionCardIndex());
        return acID != null ? Mapper.getActionCard(acID).getName() : "the action card";
    }
}
