package ti4.commands2.player;

import java.util.Collections;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

class SCUnplay extends GameStateSubcommand {

    public SCUnplay() {
        super(Constants.SC_UNPLAY, "Unplay a Strategy Card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card initiative number"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
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
        Game game = getGame();
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

        MessageHelper.sendMessageToEventChannel(event, "Strategy card has been flipped: " + CardEmojis.getSCBackFromInteger(scToUnplay) + " to " + CardEmojis.getSCFrontFromInteger(scToUnplay) + " (unplayed).");
    }

}
