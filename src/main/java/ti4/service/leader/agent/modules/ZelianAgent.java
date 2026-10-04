package ti4.service.leader.agent.modules;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.logging.BotLogger;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.tactical.TacticalActionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public final class ZelianAgent extends TargetedAgent {

    public static final String ID = "zelianagent";

    public static String buttonId(Player target) {
        return AgentButtonIds.format(ID, target.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Zelian A, the Zelian";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Tile tile = use.game().getTileByPosition(use.game().getActiveSystem());
        if (tile == null) {
            return AgentOutcome.of(Message.to(use.user(), "Could not find the active system"));
        }
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null || space.getUnitCount(UnitType.Infantry, target.getColor()) < 1) {
            return AgentOutcome.of(Message.to(
                    use.user(),
                    "Player did not have any infantry in the space area of the active system, no mech placed."));
        }
        RemoveUnitService.removeUnits(use.event(), tile, use.game(), target.getColor(), "1 inf");
        AddUnitService.addUnits(use.event(), tile, use.game(), target.getColor(), "1 mech");
        offerLandingIfUsedOnSelf(use, target, tile);

        String message = target.getFactionEmojiOrColor() + " replace 1 infantry with 1 mech in "
                + tile.getRepresentationForButtons(use.game(), target) + " due to " + use.agentName() + ".";
        return AgentOutcome.notifyInFog(use.game(), target, message, use.user(), message);
    }

    private static void offerLandingIfUsedOnSelf(AgentUse<Player> use, Player target, Tile tile) {
        if (!(use.event() instanceof ButtonInteractionEvent buttonEvent)
                || !buttonEvent.getButton().getLabel().contains("Yourself")) {
            return;
        }
        List<Button> landingButtons = TacticalActionService.getLandingTroopsButtons(use.game(), target, tile);
        buttonEvent
                .getMessage()
                .editMessage(buttonEvent.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(landingButtons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
