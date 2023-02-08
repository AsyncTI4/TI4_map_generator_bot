package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ShowAllPlayedSC extends FOWSubcommandData {

    public ShowAllPlayedSC() {
        super(Constants.FOG_SHOW_PLAYED_SC, "Remove a Fog of War tile from the map. ");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        HashMap<Integer, Boolean> scPlayed = activeMap.getScPlayed();
        List<Integer> playedScs = new ArrayList<Integer>();
        scPlayed.keySet().forEach(
                i -> {if(scPlayed.get(i)) playedScs.add(i);}
        );
        Collections.sort(playedScs);
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < playedScs.size(); i++) {
            msg.append("`").append(i).append("` ");
            msg.append(Helper.getSCBackEmojiFromInteger(i));
            msg.append(Helper.getSCAsMention(event.getGuild(), i));
            msg.append("\n");
        }
        MessageHelper.replyToMessage(event, msg.toString());
    }
}
