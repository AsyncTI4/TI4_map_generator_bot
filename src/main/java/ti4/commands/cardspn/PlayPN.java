package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TemporaryCombatModifierModel;

public class PlayPN extends PNCardsSubcommandData {
    public PlayPN() {
        super(Constants.PLAY_PN, "Play Promissory Note");
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
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
            MessageHelper.sendMessageToEventChannel(event, "Please select what Promissory Note to play");
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
                String pnName = Mapper.getPromissoryNote(pn.getKey()).getName();
                if (pnName != null) {
                    pnName = pnName.toLowerCase();
                    if (pnName.contains(value) || pn.getKey().contains(value)) {
                        if (foundSimilarName && !cardName.equals(pnName)) {
                            MessageHelper.sendMessageToEventChannel(event, "Multiple cards with similar name founds, please use ID");
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
            MessageHelper.sendMessageToEventChannel(event, "No such Promissory Note ID found, please retry");
            return;
        }

        playPN(event, game, player, longPNDisplay, pnID);
    }

    private void playPN(GenericInteractionCreateEvent event, Game game, Player player, boolean longPNDisplay, String pnID) {
        PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pnID);
        String pnName = pnModel.getName();
        Player pnOwner = game.getPNOwner(pnID);
        if (pnModel.getPlayArea()) {
            player.setPromissoryNotesInPlayArea(pnID);
        } else { //return to owner
            player.removePromissoryNote(pnID);
            if (pnOwner != null) {
                if (pnOwner.getPromissoryNotesOwned().contains(pnID)) {
                    pnOwner.setPromissoryNote(pnID);
                    PNInfo.sendPromissoryNoteInfo(game, pnOwner, false, event);
                }
            }
        }

        MessageEmbed pnEmbed = pnModel.getRepresentationEmbed();
        String emojiToUse = game.isFowMode() || pnOwner == null ? "" : pnOwner.getFactionEmoji();
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentation()).append(" played promissory note: ");
        sb.append(emojiToUse).append(Emojis.PN).append("**").append(pnName).append("**\n");

        if ("dspnkoll".equalsIgnoreCase(pnID)) {
            ButtonHelperFactionSpecific.offerKolleccPNButtons(game, player);
        }

        //Fog of war ping
        if (game.isFowMode()) {
            // Add extra message for visibility
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, sb.toString());
        }

        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), sb.toString(), pnEmbed);
        PNInfo.sendPromissoryNoteInfo(game, player, false);

        TemporaryCombatModifierModel posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID, player.getNumberTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }
}