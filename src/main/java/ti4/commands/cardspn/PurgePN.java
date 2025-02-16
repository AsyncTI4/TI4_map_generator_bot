package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PurgePN extends GameStateSubcommand {

    public PurgePN() {
        super(Constants.PURGE_PN, "Purge promissory note", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "Promissory note ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int pnIndex = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsInt();
        String id = null;
        for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
            if (pn.getValue().equals(pnIndex)) {
                id = pn.getKey();
            }
        }

        if (id == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such promissory note ID found, please retry");
            return;
        }

        purgePromissoryFromHand(getGame(), player, id);
        MessageHelper.sendMessageToEventChannel(event, "Promissory note purged.");
    }

    private static void purgePromissoryFromHand(Game game, Player player, String pn) {
        game.setPurgedPN(pn);
        player.removePromissoryNote(pn);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
    }
}
