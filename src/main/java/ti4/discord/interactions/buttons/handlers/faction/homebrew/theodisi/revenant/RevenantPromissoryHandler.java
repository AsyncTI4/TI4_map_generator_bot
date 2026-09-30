package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RandomHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;

@UtilityClass
public class RevenantPromissoryHandler {
    public static void getRevenantPNButtons(Game game, Player player) {
        if (game == null
                || player == null
                || !player.getPromissoryNotesInPlayArea().contains("thpnrevenant")) {
            return;
        }

        List<String> unusedAgents = RevenantAbilityHandler.getCurrentPantheonAgentIds().stream()
                .filter(agentId -> Mapper.getLeader(agentId) != null)
                .filter(agentId ->
                        game.getRealPlayers().stream().noneMatch(currentOwner -> currentOwner.hasLeader(agentId)))
                .toList();
        if (unusedAgents.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", there are no unused Revenant Pantheon agents to gain.");
            return;
        }

        LeaderModel agent = Mapper.getLeader(RandomHelper.pickRandomFromList(unusedAgents));
        player.addLeader(agent.getId());
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " purged a random unused Revenant Pantheon card and gained "
                        + agent.getName() + " using _Revenant Rebirth_.",
                agent.getRepresentationEmbed());
    }

    public static void returnRebrithAtStartOfStatus(Game game) {
        for (Player holder : game.getPlayers().values()) {
            if (!holder.getPromissoryNotesInPlayArea().contains("thpnrevenant")) {
                continue;
            }

            Player owner = game.getPNOwner("thpnrevenant");
            if (owner == null || !owner.isRealPlayer()) {
                continue;
            }

            holder.removePromissoryNote("thpnrevenant");
            owner.setPromissoryNote("thpnrevenant");
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    "_Revenant Rebirth_ has been returned to " + owner.getRepresentationNoPing() + ".");
        }
    }
}
