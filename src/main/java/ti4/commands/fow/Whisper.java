package ti4.commands.fow;



import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Whisper extends FOWSubcommandData {
    
    
    public Whisper() {
        super(Constants.WHISPER, "Send a private message to a player");
        addOptions(new OptionData(OptionType.STRING, Constants.WHISPERMSG, "Message to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send the message").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ANON, "Send anonymously").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.replyToSlashCommand(event,"Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(activeMap, player, event);
        if (player_ == null) {
            MessageHelper.replyToSlashCommand(event,"Player to send message to could not be found");
            return;
        }

        OptionMapping whisperms = event.getOption(Constants.WHISPERMSG);
        OptionMapping anon = event.getOption(Constants.ANON);
        if (whisperms != null) {
            String msg = whisperms.getAsString();
            String message = "";
            if (anon != null)
            {
                String anonY = anon.getAsString();
                
                if (anonY.compareToIgnoreCase("y") == 0)
                {
                     message =  "Someone says: " + msg;
                }
                else
                {
                     message = Helper.getPlayerRepresentation(event, player) + " says: " + msg;
                }
            }
            else
            {
                message = Helper.getPlayerRepresentation(event, player) + " says: " + msg;
            }
        

            if (activeMap.isFoWMode()) {
                String fail = "Could not notify recieving player.";
                String success = "";
                if(message.startsWith("Someone"))
                {
                    success = "You anonymously sent: \"" + msg + "\" to " + Helper.getPlayerRepresentation(event, player_);
                }
                else
                {
                    success = "You sent: \"" + msg + "\" to " + Helper.getPlayerRepresentation(event, player_);
                }
                MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, event.getChannel(), message, fail, success);
            }
        }
    }
    @Override
    public void reply(SlashCommandInteractionEvent event) {
        return;
    }

    
}
