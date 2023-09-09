package ti4.commands.cardsac;

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

public class ShowACToAll extends ACCardsSubcommandData {
    public ShowACToAll() {
        super(Constants.SHOW_AC_TO_ALL, "Show an Action Card to all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
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
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to show to All");
            return;
        }

        int soIndex = option.getAsInt();
        String acID = null;
        boolean scored = false;
        for (java.util.Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                acID = so.getKey();
                break;
            }
        }

        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action CardID found, please retry");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeGame.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Action Card:").append("\n");

        sb.append(Mapper.getActionCard(acID).getRepresentation()).append("\n");
        if (!scored) {
            player.setActionCard(acID);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
