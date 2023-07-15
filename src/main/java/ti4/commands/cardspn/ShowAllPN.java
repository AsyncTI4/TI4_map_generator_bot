package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;

public class ShowAllPN extends PNCardsSubcommandData {
    public ShowAllPN() {
        super(Constants.SHOW_ALL_PN, "Show Promissory Note to player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to enable").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        Player targetPlayer = Helper.getPlayer(activeMap, null, event);
        if (targetPlayer == null) {
            sendMessage("Target player not found");
            return;
        }

        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = longPNOption.getAsString().equalsIgnoreCase("y") || longPNOption.getAsString().equalsIgnoreCase("yes");
        }

        showAll(player, targetPlayer, activeMap, longPNDisplay);
    }

    public void showAll(Player player, Player targetPlayer, Map activeMap, boolean longPNDisplay) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Promissory Notes:").append("\n");
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotes().keySet());
        Collections.shuffle(promissoryNotes);
        int index = 1;
        for (String id : promissoryNotes) {
            sb.append(index).append(". ").append(Mapper.getPromissoryNote(id, longPNDisplay)).append("\n");
            index++;
        }

        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, activeMap, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, "All PNs shown to player");
    }
}
