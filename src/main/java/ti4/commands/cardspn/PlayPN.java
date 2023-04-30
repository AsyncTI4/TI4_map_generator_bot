package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

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
        String id = null;
        int pnIndex;
        try {
            pnIndex = Integer.parseInt(value);
            for (java.util.Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
                if (so.getValue().equals(pnIndex)) {
                    id = so.getKey();
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
                        id = pn.getKey();
                        foundSimilarName = true;
                        cardName = pnName;
                    }
                }
            }
        }

        if (id == null) {
            sendMessage("No such Promissory Note ID found, please retry");
            return;
        }

        String promissoryNote = Mapper.getPromissoryNote(id, true);
        String[] pn = promissoryNote.split(";");
        String pnOwner = Mapper.getPromissoryNoteOwner(id);
        if (pn.length > 3 && pn[3].equals("playarea")) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            for (Player player_ : activeMap.getPlayers().values()) {
                String playerColor = player_.getColor();
                String playerFaction = player_.getFaction();
                if (playerColor != null && playerColor.equals(pnOwner) || playerFaction != null && playerFaction.equals(pnOwner)) {
                    player_.setPromissoryNote(id);
                    PNInfo.sendPromissoryNoteInfo(activeMap, player_, false, event);
                    pnOwner = player_.getFaction();
                    break;
                }
            }
        }

        String emojiToUse = activeMap.isFoWMode() ? "" : Helper.getFactionIconFromDiscord(pnOwner);
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(event, player) + " played promissory note:\n");
        sb.append(emojiToUse + Emojis.PN);
        String pnText = "";

        //Handle AbsolMode Political Secret
        if (activeMap.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol + ":  *When you cast votes:* You may exhaust up to 3 of the {colour} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {colour} player.";
        } else {
            pnText = Mapper.getPromissoryNote(id, longPNDisplay);
        }
        sb.append(pnText).append("\n");
        
        //TERRAFORM TIP
        if (id.equalsIgnoreCase("terraform")) {
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
