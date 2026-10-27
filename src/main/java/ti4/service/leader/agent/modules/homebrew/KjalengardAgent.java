package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class KjalengardAgent extends UntargetedAgent {

    static final String ID = "kjalengardagent";

    public KjalengardAgent() {
        super(ID, "Merkismathr Asvand, the Kjalengard");
    }

    public static Button button(Player kjalengard) {
        return Buttons.gray(
                ownedButtonId(kjalengard, ID), useLabel(kjalengard, ID, "Kjalengard Agent"), FactionEmojis.kjalengard);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player payload) {
        if (activeSystem(game).isEmpty()) {
            return Optional.of("There is no active system to move a **Glory** token into");
        }
        return Optional.empty();
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has exhausted " + use.agentName() + ", to potentially move a **Glory** token into the active system.";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player kjalengard = use.user();
        Tile activeSystem = activeSystem(game).orElseThrow();
        Message gloryMove = gloryMovePrompt(game, kjalengard, activeSystem);
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) {
            return AgentOutcome.of(gloryMove);
        }
        int oldCommodities = activePlayer.getCommodities();
        activePlayer.setCommodities(oldCommodities + activePlayer.getNeighbourCount());
        return AgentOutcome.of(
                Message.plain(
                        activePlayer,
                        activePlayer.getFactionEmojiOrColor() + " commodities went from " + oldCommodities + " -> "
                                + activePlayer.getCommodities() + "."),
                gloryMove);
    }

    private static Message gloryMovePrompt(Game game, Player kjalengard, Tile activeSystem) {
        List<Button> gloryTiles = ButtonHelperAgents.moveGloryButtons(game, kjalengard, activeSystem);
        if (gloryTiles.isEmpty()) {
            return Message.plain(
                    kjalengard,
                    kjalengard.getFactionEmoji()
                            + " there were no **Glory** tokens on the game board to move. Go win some battles and earn"
                            + " some, or your ancestors will laugh at ya when "
                            + (ThreadLocalRandom.current().nextInt(20) == 0 ? "(if) " : "") + "you reach Valhalla.");
        }
        return Message.addressedTo(
                kjalengard, ", please choose the system to move a **Glory** token from.", gloryTiles);
    }
}
