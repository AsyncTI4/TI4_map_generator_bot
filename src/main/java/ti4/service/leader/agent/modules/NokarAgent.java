package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.unit.AddUnitService;

public final class NokarAgent extends TargetedAgent {

    public static final String ID = "nokaragent";

    static String buttonId(Player owner, Player target) {
        return AgentButtonIds.formatOwned(owner, ID, target.getFaction());
    }

    public static Button offer(Player owner) {
        return Buttons.gray(
                buttonId(owner, owner),
                AgentNames.offerVerb(owner, ID) + "Nokar Agent to Place 1 Destroyer",
                FactionEmojis.nokar);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Sal Sparrow, the Nokar";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Tile tile = use.game().getTileByPosition(use.game().getActiveSystem());
        if (tile == null) {
            return AgentOutcome.of(Message.to(use.user(), "Could not find the active system"));
        }
        if (!FoWHelper.playerHasShipsInSystem(target, tile)) {
            return AgentOutcome.of(
                    Message.to(use.user(), "Player did not have a ship in the active system, no destroyer placed"));
        }
        AddUnitService.addUnits(use.event(), tile, use.game(), target.getColor(), "1 destroyer");
        String message = target.getFactionEmojiOrColor() + " place 1 destroyer in "
                + tile.getRepresentationForButtons(use.game(), target) + " due to " + use.agentName() + ". "
                + "A transaction may be done with transaction buttons.";
        return AgentOutcome.notifyInFog(use.game(), target, message, use.user(), message);
    }
}
