package ti4.commands.cardsac;

import java.util.Map;

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

public class ShowACToAll extends ACCardsSubcommandData {
    public ShowACToAll() {
        super(Constants.SHOW_AC_TO_ALL, "Show an Action Card to all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to show to All");
            return;
        }

        int soIndex = option.getAsInt();
        String acID = null;
        boolean scored = false;
        for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                acID = so.getKey();
                break;
            }
        }

        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action CardID found, please retry");
            return;
        }

        String sb = "Game: " + game.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Action Card:" + "\n" +
            Mapper.getActionCard(acID).getRepresentation() + "\n";
        if (!scored) {
            player.setActionCard(acID);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
