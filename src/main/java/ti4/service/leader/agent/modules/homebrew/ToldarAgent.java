package ti4.service.leader.agent.modules.homebrew;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ToldarAgent extends TargetedAgent {

    static final String ID = "toldaragent";
    private static final String LEGACY_USE = "toldarAgent_";

    public ToldarAgent() {
        super(ID, "Maertin Donaais, the Toldar");
    }

    public static void offerOnCommoditiesReplenished(Game game, Player replenisher, int commodities) {
        for (Player toldar : game.getRealPlayers()) {
            if (toldar == replenisher || !toldar.hasUnexhaustedLeader(ID)) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    ownedButtonId(toldar, ID, replenisher.getFaction()),
                    useLabel(toldar, ID, "Toldar Agent"),
                    FactionEmojis.toldar));
            buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    toldar.getCardsInfoThread(),
                    toldar.getRepresentationUnfogged() + ", you may use "
                            + AgentLifecycle.agentNameForHolder(game, toldar, ID) + ", on "
                            + replenisher.getFactionEmojiOrColor() + " for "
                            + StringHelper.pluralize(commodities, "commodity", "commodities") + ".",
                    buttons);
        }
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(
                event, game, player, ID, StringUtils.substringBeforeLast(buttonID.substring(LEGACY_USE.length()), "_"));
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        String onTarget =
                use.payload() == use.user() ? "" : " on " + use.payload().getRepresentationNoPing();
        return " has exhausted " + use.agentName() + onTarget + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player toldar = use.user();
        Player washed = use.payload();
        int commodities = washed.getCommodities();
        washed.setTg(washed.getTg() + commodities);
        washed.setCommodities(0);
        Message washedNote = Message.addressedTo(
                washed,
                ", your " + StringHelper.pluralize(commodities, "commodity", "commodities") + " have been washed by "
                        + use.agentNameFor(washed) + ".");
        if (washed == toldar) {
            return AgentOutcome.of(washedNote);
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        toldar.factionButtonChecker() + "draw_1_ACDelete", "Draw 1 Action Card", CardEmojis.ActionCard),
                Buttons.blue(toldar.factionButtonChecker() + "gain_2_comms", "Gain 2 Commodities", MiscEmojis.comm));
        return AgentOutcome.of(
                washedNote,
                Message.addressedTo(toldar, " please choose to either gain 2 commodities or draw 1 AC", buttons));
    }
}
