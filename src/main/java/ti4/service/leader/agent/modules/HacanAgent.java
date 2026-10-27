package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperStats;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class HacanAgent extends PlayerPickerAgent {

    static final String ID = "hacanagent";
    private static final String PICK_PLAYER_PREFIX = "hacanAgentRefresh";
    private static final int COMMODITIES_FOR_THE_HACAN = 2;

    public HacanAgent() {
        super(ID, "Carth of Golden Sands, the Hacan", PICK_PLAYER_PREFIX);
    }

    public static Button button(Player hacan) {
        return Buttons.gray(ownedButtonId(hacan, ID), useLabel(hacan, ID, "Hacan Agent"), FactionEmojis.Hacan);
    }

    @ButtonHandler(PICK_PLAYER_PREFIX + "_")
    public static void resolvePickedPlayer(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        resolvePickedPlayer(ID, buttonID, event, game, player);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Choice> use, Player target) {
        Player hacan = use.user();
        if (target == hacan) {
            return gainCommodities(use, hacan);
        }
        ButtonHelperStats.replenishComms(use.event(), use.game(), target, true);
        return AgentOutcome.of(
                Message.addressedTo(target, ", your commodities were replenished by " + use.agentNameFor(target) + "."),
                Message.plain(
                        hacan,
                        hacan.getFactionEmojiOrColor() + " replenished " + target.getFactionEmojiOrColor()
                                + "'s commodities."));
    }

    private static AgentOutcome gainCommodities(AgentUse<Choice> use, Player hacan) {
        int before = hacan.getCommodities();
        ButtonHelperStats.gainComms(use.event(), use.game(), hacan, COMMODITIES_FOR_THE_HACAN, false, true);
        int gained = hacan.getCommodities() - before;
        return AgentOutcome.of(Message.addressedTo(
                hacan,
                " gained " + gained + " commodit" + (gained == 1 ? "y" : "ies") + " (" + before + "->"
                        + hacan.getCommoditiesRepresentation() + ")."));
    }
}
