package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetHeroActiveLeader extends LeaderAction {
    public SetHeroActiveLeader() {
        super(Constants.ACTIVE_LEADER, "Play Hero");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            editReplyMessage("Player could not be found");
            return;
        }
        action(event, "hero", activeMap, player);
    }

    @Override
    protected void options() {
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        String playerFaction = player.getFaction();

        if (playerLeader != null && playerLeader.isLocked()) {
            editReplyMessage("Leader locked, use command to unlock `/leaders unlock leader:hero`");
            return;
        }
        editReplyMessage(Helper.getPlayerFactionLeaderEmoji(player, leader));
        StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(event, player))
        .append(" played ")
        .append(playerLeader.getId()).append(" ")
        .append(Helper.getPlayerFactionLeaderEmoji(player, leader)).append(" ")
        .append(playerLeader.getName());
        if ("letnev".equals(playerFaction) || "nomad".equals(playerFaction)) {
            if (playerLeader != null && Constants.HERO.equals(playerLeader.getId())) {
                playerLeader.setLocked(false);
                playerLeader.setActive(true);
                MessageHelper.sendMessageToChannel(event.getChannel(), message.toString() + " - Leader will be PURGED after status cleanup");
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
            }
        } else if (playerLeader != null && Constants.HERO.equals(playerLeader.getId())) {
            boolean purged = player.removeLeader(leader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getChannel(), message.toString() + " - Leader " + leader + " has been purged");
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
            }

        }
    }
}
