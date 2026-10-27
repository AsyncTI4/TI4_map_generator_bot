package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.tactical.TacticalActionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public final class ZelianAgent extends TargetedAgent {

    static final String ID = "zelianagent";

    public ZelianAgent() {
        super(ID, "Zelian A, the Zelian");
    }

    public static Button button(Player zelian) {
        return Buttons.gray(
                ownedButtonId(zelian, ID, zelian.getFaction()),
                useLabel(zelian, ID, "Zelian Agent Yourself"),
                FactionEmojis.zelian);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        Optional<Tile> tile = activeSystem(game);
        if (tile.isEmpty()) {
            return Optional.of("Could not find the active system");
        }
        UnitHolder space = tile.get().getUnitHolders().get(Constants.SPACE);
        if (space == null || space.getUnitCount(UnitType.Infantry, target.getColor()) < 1) {
            return Optional.of(
                    target.getFactionEmojiOrColor() + " has no infantry in the space area of the active system");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Tile tile = activeSystem(use.game()).orElseThrow();
        RemoveUnitService.removeUnits(use.event(), tile, use.game(), target.getColor(), "1 inf");
        AddUnitService.addUnits(use.event(), tile, use.game(), target.getColor(), "1 mech");

        String message = target.getFactionEmojiOrColor() + " replace 1 infantry with 1 mech in "
                + tile.getRepresentationForButtons(use.game(), target) + " due to " + use.agentNameFor(target) + ".";
        AgentOutcome outcome = AgentOutcome.notifyInFog(use.game(), target, message, use.user());
        if (target != use.user()) {
            return outcome;
        }
        return outcome.and(Message.addressedTo(
                target,
                ", you may now land troops in " + tile.getRepresentationForButtons(use.game(), target) + ".",
                TacticalActionService.getLandingTroopsButtons(use.game(), target, tile)));
    }
}
