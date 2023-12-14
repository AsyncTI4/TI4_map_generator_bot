package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
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
            longPNDisplay = "y".equalsIgnoreCase(longPNOption.getAsString()) || "yes".equalsIgnoreCase(longPNOption.getAsString());
        }

        String value = option.getAsString().toLowerCase();
        String pnID = null;
        int pnIndex;
        try {
            pnIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
                if (pn.getValue().equals(pnIndex)) {
                    pnID = pn.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
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
        Player pnOwner = activeGame.getPNOwner(pnID);
        if (promissoryNote.getPlayArea()) {
            player.setPromissoryNotesInPlayArea(pnID);
        } else { //return to owner
            player.removePromissoryNote(pnID);
            if (pnOwner != null) {
                if (pnOwner.getPromissoryNotesOwned().contains(pnID)) {
                    pnOwner.setPromissoryNote(pnID);
                    PNInfo.sendPromissoryNoteInfo(activeGame, pnOwner, false, event);
                }
            }
        }
        
       

        String emojiToUse = activeGame.isFoWMode() ? "" : pnOwner.getFactionEmoji();
        StringBuilder sb = new StringBuilder(player.getRepresentation() + " played promissory note: " + pnName + "\n");
        sb.append(emojiToUse).append(Emojis.PN);
        String pnText;

        pnText = Mapper.getPromissoryNote(pnID, longPNDisplay);
        sb.append(pnText).append("\n");

        //TERRAFORM TIP
        if ("terraform".equalsIgnoreCase(pnID)) {
            sb.append("`/add_token token:titanspn`\n");
        }
        
        if ("dspnkoll".equalsIgnoreCase(pnID)) {
            ButtonHelperFactionSpecific.offerKolleccPNButtons(player, activeGame, event);
        }
        //Fog of war ping
        if (activeGame.isFoWMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, sb.toString());
        }
        
        var posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID,
                player.getNumberTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            sendMessage("Combat modifier will be applied next time you push the combat roll button.");
        }

        sendMessage(sb.toString());
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
    }
}