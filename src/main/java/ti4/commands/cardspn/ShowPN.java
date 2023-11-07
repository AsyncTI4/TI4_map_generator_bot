package ti4.commands.cardspn;
import java.util.Map;
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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            sendMessage("Please select what Promissory Note to show");
            return;
        }
        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = "y".equalsIgnoreCase(longPNOption.getAsString()) || "yes".equalsIgnoreCase(longPNOption.getAsString());
        }

        int acIndex = option.getAsInt();
        String acID = null;
        for (Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                acID = so.getKey();
            }
        }

        if (acID == null) {
            sendMessage("No such Promissory Note ID found, please retry");
            return;
        }

        Player targetPlayer = Helper.getPlayer(activeGame, null, event);
        if (targetPlayer == null) {
            sendMessage("Target player not found");
            return;
        }

        String sb = "---------\n" +
            "Game: " + activeGame.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Promissory Note:" + "\n" +
            Mapper.getPromissoryNote(acID, longPNDisplay) + "\n" +
            "---------\n";
        player.setPromissoryNote(acID);
        
        sendMessage("PN shown");
        PNInfo.sendPromissoryNoteInfo(activeGame, player, longPNDisplay);
        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, activeGame, sb);
    }
}
