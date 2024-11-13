package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowAllSO extends GameStateSubcommand {

    public ShowAllSO() {
        super(Constants.SHOW_ALL_SO, "Show all Secret Objectives to one player", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.OTHER_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        Player player_ = Helper.getOtherPlayerFromEvent(game, event);
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
