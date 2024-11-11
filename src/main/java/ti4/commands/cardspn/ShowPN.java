package ti4.commands.cardspn;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowPN extends PlayerGameStateSubcommand {

    public ShowPN() {
        super(Constants.SHOW_PN, "Show Promissory Note to player", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.OTHER_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to enable"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        int index = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsInt();
        String pnID = null;
        for (Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
            if (so.getValue().equals(index)) {
                pnID = so.getKey();
            }
        }

        if (pnID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such Promissory Note ID found, please retry");
            return;
        }

        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = "y".equalsIgnoreCase(longPNOption.getAsString()) || "yes".equalsIgnoreCase(longPNOption.getAsString());
        }

        Player targetPlayer = Helper.getOtherPlayerFromEvent(game, event);
        if (targetPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Target player not found");
            return;
        }

        MessageEmbed pnEmbed = Mapper.getPromissoryNote(pnID).getRepresentationEmbed(!longPNDisplay, false, false);
        player.setPromissoryNote(pnID);

        String message = player.getRepresentation(false, false) + " showed you a promissory note:";

        MessageHelper.sendMessageToEventChannel(event, "PN shown");
        PNInfo.sendPromissoryNoteInfo(game, player, longPNDisplay);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, targetPlayer, message, List.of(pnEmbed));
    }
}
