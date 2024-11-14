package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SentACRandom extends ACCardsSubcommandData {
    public SentACRandom() {
        super(Constants.SEND_AC_RANDOM, "Send a random Action Card to a player");
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Target faction or color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }

        User user = AsyncTI4DiscordBot.jda.getUserById(otherPlayer.getUserID());
        if (user == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "User for faction not found. Report to ADMIN");
            return;
        }
        sendRandomACPart2(event, game, player, otherPlayer);
    }

    public void sendRandomACPart2(GenericInteractionCreateEvent event, Game game, Player player, Player player_) {
        Map<String, Integer> actionCardsMap = player.getActionCards();
        List<String> actionCards = new ArrayList<>(actionCardsMap.keySet());
        if (actionCards.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No Action Cards in hand");
            return;
        }
        Collections.shuffle(actionCards);
        String acID = actionCards.getFirst();
        // FoW specific pinging
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(game, event, player, player_, Emojis.ActionCard + " Action Card", null);
        }
        player.removeActionCard(actionCardsMap.get(acID));
        player_.setActionCard(acID);
        ACInfo.sendActionCardInfo(game, player_);
        ButtonHelper.checkACLimit(game, event, player_);
        ACInfo.sendActionCardInfo(game, player);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "# " + player.getRepresentation() + " you lost the AC " + Mapper.getActionCard(acID).getName());
        MessageHelper.sendMessageToChannel(player_.getCardsInfoThread(), "# " + player_.getRepresentation() + " you gained the AC " + Mapper.getActionCard(acID).getName());
    }
}
