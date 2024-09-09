package ti4.commands.player;

import java.util.Collections;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SCUnplay extends PlayerSubcommandData {
    public SCUnplay() {
        super(Constants.SC_UNPLAY, "Unplay a Strategy Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card #"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);

        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You're not a player of this game.");
            return;
        }

        Set<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card has been selected.");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { //Only one SC selected
            MessageHelper.sendMessageToEventChannel(event, "Player has more than one strategy card. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToUnplay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        game.setSCPlayed(scToUnplay, false);

        //fix sc reminders for all players
        for (Player player_ : game.getPlayers().values()) {
            if (!player_.isRealPlayer()) {
                continue;
            }
            String faction = player_.getFaction();
            if (faction == null || faction.isEmpty() || "null".equals(faction)) continue;
            player_.addFollowedSC(scToUnplay);
        }

        MessageHelper.sendMessageToEventChannel(event, "Strategy card has been flipped: " + Emojis.getSCBackEmojiFromInteger(scToUnplay) + " to " + Emojis.getSCEmojiFromInteger(scToUnplay) + " (unplayed).");
    }

}
