package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;

public class ShowAllSO extends SOCardsSubcommandData {
    public ShowAllSO() {
        super(Constants.SHOW_ALL_SO, "Show all Secret Objectives to one player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
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

        Player player_ = Helper.getPlayerFromEvent(game, null, event);
        if (player_ == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player not found");
            return;
        }
        showAll(player, player_, game);
    }

    public void showAll(Player player, Player player_, Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Secret Objectives:").append("\n");
        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        Collections.shuffle(secrets);
        for (String id : secrets) {
            sb.append(SOInfo.getSecretObjectiveRepresentation(id)).append("\n");
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, game, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "All SOs shown to player");
    }
}
