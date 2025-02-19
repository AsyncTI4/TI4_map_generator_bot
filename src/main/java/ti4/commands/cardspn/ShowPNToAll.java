package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShowPNToAll extends GameStateSubcommand {

    public ShowPNToAll() {
        super(Constants.SHOW_TO_ALL, "Show Promissory Note to table", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "Promissory note ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int pnIndex = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsInt();
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
        PromissoryNoteHelper.sendPromissoryNoteInfo(getGame(), player, false);
        MessageHelper.sendMessageToChannelWithEmbed(event.getChannel(), message, pnEmbed);
    }
}
