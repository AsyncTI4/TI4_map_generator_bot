package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;

public class HeroPlay extends LeaderAction {
    public HeroPlay() {
        super(Constants.ACTIVE_LEADER, "Play Hero");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);

        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        action(event, "hero", activeGame, player);
    }

    @Override
    protected void options() {
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game activeGame, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        
        if (playerLeader == null) {
            sendMessage("Leader '" + leaderID + "'' could not be found. The leader might have been purged earlier.");
            return;
        }
        
        if (playerLeader.isLocked()) {
            sendMessage("Leader is locked, use command to unlock `/leaders unlock leader:" + leaderID + "`");
            sendMessage(Helper.getLeaderLockedRepresentation(playerLeader));
            return;
        }

        if (!playerLeader.getType().equals(Constants.HERO)) {
            sendMessage("Leader is not a hero");
            return;
        }

        sendMessage(Helper.getFactionLeaderEmoji(playerLeader));
        StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame)).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));

        if ("letnevhero".equals(playerLeader.getId()) || "nomadhero".equals(playerLeader.getId())) {
            playerLeader.setLocked(false);
            playerLeader.setActive(true);
            sendMessage(message + " - Leader will be PURGED after status cleanup");
        } else {
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                sendMessage(message + " - Leader " + leaderID + " has been purged");
            } else {
                sendMessage("Leader was not purged - something went wrong");
            }
            if ("titanshero".equals(playerLeader.getId())) {
                sendMessage("`Use the following command to add the attachment: /add_token token:titanshero`");
            }
        }
    }
}
