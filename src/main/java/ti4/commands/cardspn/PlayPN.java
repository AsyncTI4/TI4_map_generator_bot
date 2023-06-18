package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.model.PromissoryNoteModel;

public class PlayPN extends PNCardsSubcommandData {
    public PlayPN() {
        super(Constants.PLAY_PN, "Play Promissory Note");
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
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
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            sendMessage("Please select what Promissory Note to play");
            return;
        }
        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = longPNOption.getAsString().equalsIgnoreCase("y") || longPNOption.getAsString().equalsIgnoreCase("yes");
        }

        String value = option.getAsString().toLowerCase();
        String pnID = null;
        int pnIndex;
        try {
            pnIndex = Integer.parseInt(value);
            for (java.util.Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
                if (pn.getValue().equals(pnIndex)) {
                    pnID = pn.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (java.util.Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
                String pnName = Mapper.getPromissoryNote(pn.getKey(), false);
                if (pnName != null) {
                    pnName = pnName.toLowerCase();
                    if (pnName.contains(value) || pn.getKey().contains(value)) {
                        if (foundSimilarName && !cardName.equals(pnName)) {
                            sendMessage("Multiple cards with similar name founds, please use ID");
                            return;
                        }
                        pnID = pn.getKey();
                        foundSimilarName = true;
                        cardName = pnName;
                    }
                }
            }
        }

        if (pnID == null) {
            sendMessage("No such Promissory Note ID found, please retry");
            return;
        }

        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnID);
        String pnName = promissoryNote.getName();
        String pnOwner = Mapper.getPromissoryNoteOwner(pnID);
        if (promissoryNote.getPlayArea()) {
            player.setPromissoryNotesInPlayArea(pnID);
        } else { //return to owner
            player.removePromissoryNote(pnID);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_.getPromissoryNotesOwned().contains(pnID)) {
                    player_.setPromissoryNote(pnID);
                    PNInfo.sendPromissoryNoteInfo(activeMap, player_, false, event);
                    pnOwner = player_.getFaction();
                    break;
                }
            }
        }
        
       

        String emojiToUse = activeMap.isFoWMode() ? "" : Helper.getFactionIconFromDiscord(pnOwner);
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap) + " played promissory note: "+pnName+"\n");
        sb.append(emojiToUse + Emojis.PN);
        String pnText = "";

        pnText = Mapper.getPromissoryNote(pnID, longPNDisplay);
        sb.append(pnText).append("\n");

        //TERRAFORM TIP
        if (pnID.equalsIgnoreCase("terraform")) {
            sb.append("`/add_token token:titanspn`\n");
        }

        //Fog of war ping
		if (activeMap.isFoWMode()) {
            // Add extra message for visibility
			FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, sb.toString());
		}

        sendMessage(sb.toString());
        PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
    }
}
