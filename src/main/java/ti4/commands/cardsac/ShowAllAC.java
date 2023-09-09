package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;

public class ShowAllAC extends ACCardsSubcommandData {
    public ShowAllAC() {
        super(Constants.SHOW_ALL_AC, "Show all Action Cards one player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        Player player_ = Helper.getPlayer(activeGame, null, event);
        if (player_ == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }

        showAll(player, player_, activeGame);
    }

    public static void showAll(Player player, Player player_, Game activeGame) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeGame.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Action Cards:").append("\n");
        List<String> actionCards = new ArrayList<>(player.getActionCards().keySet());
        Collections.shuffle(actionCards);
        int index = 1;
        for (String id : actionCards) {
            sb.append(index).append(". ").append(Mapper.getActionCard(id).getRepresentation()).append("\n");
            index++;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, activeGame, sb.toString());
    }
}
