package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelperStats;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class VadenAgent extends TargetedAgent {

    public static final String ID = "vadenagent";

    static String buttonId(Player owner, Player target) {
        return AgentButtonIds.formatOwned(owner, ID, target.getFaction());
    }

    public static Button offer(Player owner) {
        return Buttons.gray(
                buttonId(owner, owner), AgentNames.offerVerb(owner, ID) + "Vaden Agent", FactionEmojis.vaden);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Yudri Sukhov, the Vaden";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        int initialCommodities = target.getCommodities();
        int maxInfluence = highestInfluence(use.game(), target);
        ButtonHelperStats.gainComms(use.event(), use.game(), target, maxInfluence, false, true);
        int gained = target.getCommodities() - initialCommodities;

        String message = target.getFactionEmojiOrColor() + " max influence planet has " + maxInfluence
                + " influence, so they gained " + gained + " commodit" + (gained == 1 ? "y" : "ies") + " ("
                + initialCommodities + "->" + target.getCommodities() + ") due to " + use.agentName() + ".";
        return AgentOutcome.notifyInFog(
                use.game(), target, message, use.user(), target.getFactionEmojiOrColor() + " has finished resolving");
    }

    private static int highestInfluence(Game game, Player target) {
        int maxInfluence = 0;
        for (String planetName : target.getPlanetsAllianceMode()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet != null) {
                maxInfluence = Math.max(maxInfluence, planet.getInfluence());
            }
        }
        return maxInfluence;
    }
}
