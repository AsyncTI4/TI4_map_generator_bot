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
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        Player player_ = Helper.getPlayerFromEvent(game, null, event);
        if (player_ == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }

        showAll(player, player_, game);
    }

    public static void showAll(Player player, Player player_, Game game) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sa = new StringBuilder();
        sa.append("You have shown your cards to player: ").append(player_.getUserName()).append("\n");
        sa.append("Your cards were presented in the order below. You may reference the number listed when discussing the cards:\n");
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Action Cards, they were also presented the cards in the order you see them so you may reference the number when talking to them:").append("\n");
        List<String> actionCards = new ArrayList<>(player.getActionCards().keySet());
        Collections.shuffle(actionCards);
        int index = 1;
        for (String id : actionCards) {
            sa.append(index).append(". ").append(Mapper.getActionCard(id).getRepresentation()).append("\n");
            sb.append(index).append(". ").append(Mapper.getActionCard(id).getRepresentation()).append("\n");
            index++;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sa.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, game, sb.toString());
    }
}
