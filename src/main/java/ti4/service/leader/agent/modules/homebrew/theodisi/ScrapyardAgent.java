package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class ScrapyardAgent extends AgentModule<Player> {

    static final String ID = "scrapyardagent";
    private static final String LEGACY_DECLARE = "sendRikkaReminderMessage";

    public ScrapyardAgent() {
        super(ID, "Rikka \"Razor Fang\" Corvin, the Scrapyard");
    }

    public static void offer(Player activePlayer, Player agentOwner) {
        if (activePlayer == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(ID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer == agentOwner ? activePlayer.getCorrectChannel() : agentOwner.getCardsInfoThread(),
                agentOwner.getRepresentation()
                        + ", you may exhaust _Rikka \"Razor Fang\" Corvin_ to let the active player swap the movement and combat values "
                        + "of 2 of their units until the end of this tactical action.",
                offerButtons(activePlayer, agentOwner));
    }

    static List<Button> offerButtons(Player activePlayer, Player agentOwner) {
        return List.of(
                Buttons.green(
                        ownedButtonId(agentOwner, ID, activePlayer.getFaction()),
                        "Declare Rikka",
                        FactionEmojis.scrapyard),
                Buttons.DECLINE_DELETE_BUTTONS);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_DECLARE)
    static void legacyDeclare(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_DECLARE.length()));
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player activePlayer = use.payload();
        return AgentOutcome.of(Message.plain(
                        activePlayer,
                        activePlayer.getRepresentation() + " is using " + use.agentNameFor(activePlayer)
                                + " to swap the movement and combat values of 2 of their units until the end of this"
                                + " tactical action. They will declare which units."))
                .deletingPressedMessage();
    }
}
