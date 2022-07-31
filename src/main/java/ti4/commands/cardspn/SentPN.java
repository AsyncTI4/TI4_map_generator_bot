package ti4.commands.cardspn;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsInfo;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.io.File;

public class SentPN extends PNCardsSubcommandData {
    public SentPN() {
        super(Constants.SEND_PN, "Send Promissory Note to player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to which to send the PN").setRequired(true));
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

        int acIndex = option.getAsInt();
        String id = null;
        for (java.util.Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                id = so.getKey();
            }
        }

        if (id == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Promissory Note ID found, please retry");
            return;
        }
        boolean areaPN = false;
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            User user = playerOption.getAsUser();
            Player targetPlayer = activeMap.getPlayer(user.getId());
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
            if ((id.endsWith("_sftt") || id.endsWith("_an")) &&
                    !promissoryNoteOwner.equals(targetPlayer.getFaction()) &&
                    !promissoryNoteOwner.equals(targetPlayer.getColor())) {
                targetPlayer.setPromissoryNotesInPlayArea(id);
                areaPN = true;
            }
            CardsInfo.sentUserCardInfo(event, activeMap, targetPlayer);
            CardsInfo.sentUserCardInfo(event, activeMap, player);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Need to specify player");
        }

        if (areaPN) {
            File file = GenerateMap.getInstance().saveImage(activeMap);
            MessageHelper.sendFileToChannel(event.getChannel(), file);
        }
    }
}
