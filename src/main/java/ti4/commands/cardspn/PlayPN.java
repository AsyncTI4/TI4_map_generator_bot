package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

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
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Promissory Note to play");
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
                    CardsInfo.sentUserCardInfo(event, activeMap, player_);
                    break;
                }
            }
        } 

        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(event, player) + " played promissory note:\n");
        sb.append(Helper.getFactionIconFromDiscord(pnOwner) + Emojis.PN);
        sb.append(Mapper.getPromissoryNote(id, longPNDisplay)).append("\n");
        
        //TERRAFORM TIP
        if (id.equalsIgnoreCase("terraform")) {
            sb.append("`/add_token token:titanspn`\n");
        }

        MessageHelper.sendMessageToChannel(event, sb.toString());
        CardsInfo.sentUserCardInfo(event, activeMap, player);
    }
}
