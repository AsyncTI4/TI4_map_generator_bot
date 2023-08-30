package ti4.commands.player;

import java.util.Collections;
import java.util.LinkedHashSet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public class SCUnplay extends PlayerSubcommandData {
    public SCUnplay() {
        super(Constants.SC_UNPLAY, "Unplay an SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card #"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,"Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);

        if (player == null) {
            sendMessage("You're not a player of this game");
            return;
        }

        LinkedHashSet<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            sendMessage("No SC has been selected");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { //Only one SC selected
            sendMessage("Player has more than one SC. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToUnplay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        activeMap.setSCPlayed(scToUnplay, false);

        //fix sc reminders for all players
        for (Player player_ : activeMap.getPlayers().values()) {
            if (!player_.isRealPlayer()) {
                continue;
            }
            String faction = player_.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")) continue;
            player_.addFollowedSC(scToUnplay);
        }

        sendMessage("SC has been flipped: " + Helper.getSCBackEmojiFromInteger(scToUnplay) + " to " + Helper.getSCEmojiFromInteger(scToUnplay) + " (unplayed)");
    }

}
