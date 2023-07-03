package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardspn.PNInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public abstract class PNAddRemove extends FrankenSubcommandData {
    public PNAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID").setRequired(true).setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> pnIDs = new ArrayList<>();

        //GET ALL PN OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.PROMISSORY_NOTE_ID)).toList()) {
            pnIDs.add(option.getAsString());
        }

        pnIDs.removeIf(StringUtils::isEmpty);
        pnIDs.removeIf(pn -> !Mapper.getAllPromissoryNoteIDs().contains(pn));

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        doAction(player, pnIDs);
        PNInfo.checkAndAddPNs(getActiveMap(), player);
        PNInfo.sendPromissoryNoteInfo(activeMap, player, false, event);
    }

    public abstract void doAction(Player player, List<String> pnIDs);

}
