package ti4.commands.cardspn;

import java.util.List;
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

public class ShowPN extends PNCardsSubcommandData {
    public ShowPN() {
        super(Constants.SHOW_PN, "Show Promissory Note to player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to enable").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Please select what Promissory Note to show");
            return;
        }
        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = "y".equalsIgnoreCase(longPNOption.getAsString()) || "yes".equalsIgnoreCase(longPNOption.getAsString());
        }

        int acIndex = option.getAsInt();
        String pnID = null;
        for (Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                pnID = so.getKey();
            }
        }

        if (pnID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such Promissory Note ID found, please retry");
            return;
        }

        Player targetPlayer = Helper.getPlayerFromEvent(game, null, event);
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
