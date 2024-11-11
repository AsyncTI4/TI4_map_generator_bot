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
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public abstract class PNAddRemove extends FrankenSubcommandData {
    public PNAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> pnIDs = new ArrayList<>();

        //GET ALL PN OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.PROMISSORY_NOTE_ID)).toList()) {
            pnIDs.add(option.getAsString());
        }

        pnIDs.removeIf(StringUtils::isEmpty);
        pnIDs.removeIf(pn -> !Mapper.getAllPromissoryNoteIDs().contains(pn));

        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        doAction(player, pnIDs);
        game.checkPromissoryNotes();
        PNInfo.checkAndAddPNs(getActiveGame(), player);
        PNInfo.sendPromissoryNoteInfo(game, player, false, event);
    }

    public abstract void doAction(Player player, List<String> pnIDs);

}
