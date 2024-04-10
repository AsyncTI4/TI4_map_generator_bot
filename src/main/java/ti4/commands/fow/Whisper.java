package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import software.amazon.awssdk.utils.StringUtils;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Whisper extends FOWSubcommandData {

    public Whisper() {
        super(Constants.WHISPER, "Send a private message to a player in fog mode");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send the message").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.MSG, "Message to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ANON, "Send anonymously").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(activeGame, player, event);
        if (player_ == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player to send message to could not be found");
            return;
        }
        if (!activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game is not fog mode, and should not use this command. Instead whisper by beginning your message with to[color] or to[faction] from inside your cards info thread (for instance saying toblue hi)");
            return;
        }
        OptionMapping whisperms = event.getOption(Constants.MSG);
        OptionMapping anon = event.getOption(Constants.ANON);
        String msg = "";
        if (whisperms != null) {
            msg = whisperms.getAsString();
        }
        String anonY = "";
        if (anon != null) {
            anonY = anon.getAsString();
        }
        sendWhisper(activeGame, player, player_, msg, anonY, event.getMessageChannel(), event.getGuild());
    }

    public static void sendWhisper(Game activeGame, Player player, Player player_, String msg, String anonY, MessageChannel feedbackChannel, Guild guild) {
        String message;
        String realIdentity = player_.getRepresentation(true, true);
        String player1 = Emojis.getColorEmojiWithName(player.getColor());
        if (!activeGame.isFoWMode() && !(feedbackChannel instanceof ThreadChannel)) {
            feedbackChannel = player.getCardsInfoThread();
            MessageHelper.sendMessageToChannel(feedbackChannel, player.getRepresentation() + " Reminder you should start all whispers from your cards info channel, and do not need to use the /fow whisper command, you can just start a message with toblue or something");
        }
        if (!activeGame.isFoWMode()) {
            player1 = player.getFactionEmoji() + "(" + StringUtils.capitalize(player.getFaction()) + ") " + player1;
        }

        if (anonY.compareToIgnoreCase("y") == 0) {
            message = "[REDACTED] says: " + msg;
        } else {
            message = "Attention " + realIdentity + "! " + player1 + " says: " + msg;
        }
        if (activeGame.isFoWMode()) {
            String fail = "Could not notify receiving player.";
            String success;
            String player2 = Emojis.getColorEmojiWithName(player_.getColor());
            if (message.startsWith("[REDACTED]")) {
                success = player1 + "(You) anonymously said: \"" + msg + "\" to " + player2;
            } else {
                success = player1 + "(You) said: \"" + msg + "\" to " + player2;
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, feedbackChannel, message, fail, success);
        } else {
            String fail = "Could not notify receiving player.";
            String success;
            String player2 = Emojis.getColorEmojiWithName(player_.getColor());
            if (message.startsWith("[REDACTED]")) {
                success = player1 + "(You) anonymously said: \"" + msg + "\" to " + player2;
            } else {
                success = player1 + "(You) said: \"" + msg + "\" to " + player2;
            }
            String key = player.getFaction() + "whisperHistoryTo" + player_.getFaction();
            String whisperHistory = activeGame.getFactionsThatReactedToThis(key);
            if (whisperHistory.isEmpty()) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " is whispering for the first time this turn to " + ButtonHelper.getIdent(player_));
                activeGame.setCurrentReacts(key, "1");
            } else {
                int num = Integer.parseInt(whisperHistory);
                num = num + 1;
                activeGame.setCurrentReacts(key, "" + num);
                if (num == 5 || num == 10) {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " is sending whisper #" + num + " of this turn to " + ButtonHelper.getIdent(player_));
                }
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, feedbackChannel, message, fail, success);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
    }

}
