package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsInfo;
import ti4.commands.player.SendTG;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.io.File;

public class SentPN extends PNCardsSubcommandData {
    public SentPN() {
        super(Constants.SEND_PN, "Send Promissory Note to player");
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Promissory Note to send");
            return;
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
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Multiple cards with similar name founds, please use ID");
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
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Promissory Note ID found, please retry");
            return;
        }
        boolean areaPN = false;
        Player targetPlayer = Helper.getPlayer(activeMap, null, event);

        if (targetPlayer == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Player in game");
            return;
        }

        String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
        if (player.getPromissoryNotesInPlayArea().contains(id)) {
            String playerColor = targetPlayer.getColor();
            String playerFaction = targetPlayer.getFaction();
            if (!(playerColor != null && playerColor.equals(promissoryNoteOwner)) &&
                    !(playerFaction != null && playerFaction.equals(promissoryNoteOwner))) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Can send Promissory Notes from Play Area just to Owner of the Note");
                return;
            }
        }

        player.removePromissoryNote(id);
        targetPlayer.setPromissoryNote(id);
        boolean sendSftT = false;
        boolean sendAlliance = false;
        if ((id.endsWith("_sftt") || id.endsWith("_an")) &&
                !promissoryNoteOwner.equals(targetPlayer.getFaction()) &&
                !promissoryNoteOwner.equals(targetPlayer.getColor())) {
            targetPlayer.setPromissoryNotesInPlayArea(id);
            if (id.endsWith("_sftt")) {
                sendSftT = true;
            } else {
                sendAlliance = true;
            }
            areaPN = true;
        }
        CardsInfo.sentUserCardInfo(event, activeMap, targetPlayer);
        CardsInfo.sentUserCardInfo(event, activeMap, player);
        String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
        String message = Helper.getPlayerRepresentation(event, player) + " sent " + Emojis.PN + text + "PN to " + Helper.getPlayerRepresentation(event, targetPlayer);
        if (activeMap.isFoWMode()) {
            String fail = "User for faction not found. Report to ADMIN";
            String success = message + "\nThe other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(targetPlayer, activeMap, event, message, fail, success);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }

        //Turned off, as we might change back
        if (areaPN && false) {
            File file = GenerateMap.getInstance().saveImage(activeMap, event);
            MessageHelper.sendFileToChannel(event.getChannel(), file);
        }
    }
}
