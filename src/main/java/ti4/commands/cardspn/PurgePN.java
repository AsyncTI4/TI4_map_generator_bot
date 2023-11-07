package ti4.commands.cardspn;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class PurgePN extends PNCardsSubcommandData {
    public PurgePN() {
        super(Constants.PURGE_PN, "Purge Promissory Note");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            sendMessage("Please select what Promissory Note to send");
            return;
        }

        int acIndex = option.getAsInt();
        String id = null;
        for (Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                id = so.getKey();
            }
        }

        if (id == null) {
            sendMessage("No such Promissory Note ID found, please retry");
            return;
        }
        activeGame.setPurgedPN(id);
        player.removePromissoryNote(id);
        sendMessage("PN Purged");
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
    }
}
