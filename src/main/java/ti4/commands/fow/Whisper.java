package ti4.commands.fow;



import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Whisper extends FOWSubcommandData {


    public Whisper() {
        super(Constants.WHISPER, "Send a private message to a player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send the message").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.MSG, "Message to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ANON, "Send anonymously").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(activeGame, player, event);
        if (player_ == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Player to send message to could not be found");
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
        Whisper.sendWhisper(activeGame, player, player_, msg, anonY, event.getMessageChannel(), event.getGuild());
    }

    public static void sendWhisper(Game activeGame, Player player, Player player_, String msg, String anonY, MessageChannel feedbackChannel, Guild guild) {
        String message = "";
        String realIdentity = Helper.getPlayerRepresentation(player_, activeGame, guild, true);
        String player1 = Helper.getColourAsMention(guild, player.getColor());

        if (anonY.compareToIgnoreCase("y") == 0) {
                message =  "[REDACTED] says: " + msg;
        } else {
            message = "Attention " + realIdentity + "! " + player1 + " says: " + msg;
        }
        if (activeGame.isFoWMode()) {
            String fail = "Could not notify receiving player.";
            String success = "";
            String player2 = Helper.getColourAsMention(guild, player_.getColor());
            if (message.startsWith("[REDACTED]")) {
                success = player1 + "(You) anonymously said: \"" + msg + "\" to " + player2;
            } else {
                success = player1 + "(You) said: \"" + msg + "\" to " + player2;
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, feedbackChannel, message, fail, success);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        return;
    }


}
