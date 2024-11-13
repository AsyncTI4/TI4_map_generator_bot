package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowAllPN extends PNCardsSubcommandData {
    public ShowAllPN() {
        super(Constants.SHOW_ALL_PN, "Show Promissory Note to player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to enable").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        Player targetPlayer = CommandHelper.getPlayerFromEvent(game, event);
        if (targetPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Target player not found");
            return;
        }

        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = "y".equalsIgnoreCase(longPNOption.getAsString()) || "yes".equalsIgnoreCase(longPNOption.getAsString());
        }

        showAll(player, targetPlayer, game, longPNDisplay);
    }

    public void showAll(Player player, Player targetPlayer, Game game, boolean longPNDisplay) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Promissory Notes:").append("\n");
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotes().keySet());
        Collections.shuffle(promissoryNotes);
        int index = 1;
        for (String id : promissoryNotes) {
            sb.append(index).append(". ").append(Mapper.getPromissoryNote(id).getName()).append(" (original owner ").append(game.getPNOwner(id).getFactionEmojiOrColor()).append(")").append("\n");
            index++;
        }

        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, game, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "All PNs shown to player");
    }
}
