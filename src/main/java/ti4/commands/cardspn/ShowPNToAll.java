package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowPNToAll extends PNCardsSubcommandData {
    public ShowPNToAll() {
        super(Constants.SHOW_PN_TO_ALL, "Show promissory note to the entire table.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "ID of the promissory note to show").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found.");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Please select what promissory note (by ID) to show to all.");
            return;
        }

        int pnIndex = option.getAsInt();
        String pnID = null;
        for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
            if (pn.getValue().equals(pnIndex)) {
                pnID = pn.getKey();
                break;
            }
        }

        if (pnID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such promissory note ID found, please retry.");
            return;
        }

        MessageEmbed pnEmbed = Mapper.getPromissoryNote(pnID).getRepresentationEmbed(false, false, false);
        player.setPromissoryNote(pnID);

        String message = player.getRepresentation(false, false) + " showed a promissory note:";
        PNInfo.sendPromissoryNoteInfo(game, player, false);
        MessageHelper.sendMessageToChannelWithEmbed(event.getChannel(), message, pnEmbed);
    }
}
