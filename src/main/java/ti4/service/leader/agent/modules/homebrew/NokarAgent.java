package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.unit.AddUnitService;

public final class NokarAgent extends TargetedAgent {

    static final String ID = "nokaragent";

    public NokarAgent() {
        super(ID, "Sal Sparrow, the Nokar");
    }

    public static Button button(Player nokar) {
        return Buttons.gray(
                ownedButtonId(nokar, ID, nokar.getFaction()),
                useLabel(nokar, ID, "Nokar Agent to Place 1 Destroyer"),
                FactionEmojis.nokar);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        Optional<Tile> tile = activeSystem(game);
        if (tile.isEmpty()) {
            return Optional.of("Could not find the active system");
        }
        if (!FoWHelper.playerHasShipsInSystem(target, tile.get())) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no ships in the active system");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Tile tile = activeSystem(use.game()).orElseThrow();
        AddUnitService.addUnits(use.event(), tile, use.game(), target.getColor(), "1 destroyer");
        String message = target.getFactionEmojiOrColor() + " place 1 destroyer in "
                + tile.getRepresentationForButtons(use.game(), target) + " due to " + use.agentNameFor(target) + ". "
                + "A transaction may be done with transaction buttons.";
        return AgentOutcome.notifyInFog(use.game(), target, message, use.user());
    }
}
