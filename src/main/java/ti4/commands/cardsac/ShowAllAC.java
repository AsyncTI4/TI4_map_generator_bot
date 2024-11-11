package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowAllAC extends PlayerGameStateSubcommand {

    public ShowAllAC() {
        super(Constants.SHOW_ALL_AC, "Show all Action Cards one player", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.OTHER_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player factionToShowTo = Helper.getOtherPlayerFromEvent(game, event);
        if (factionToShowTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }
        Player player = getPlayer();
        showAll(player, factionToShowTo, game);
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
