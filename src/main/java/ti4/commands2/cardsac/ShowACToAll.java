package ti4.commands2.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShowACToAll extends GameStateSubcommand {

    public ShowACToAll() {
        super(Constants.SHOW_AC_TO_ALL, "Show an Action Card to all players", false, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();

        String acID = null;
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if (ac.getValue().equals(acIndex)) {
                acID = ac.getKey();
                break;
            }
        }

        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action CardID found, please retry");
            return;
        }

        Game game = getGame();
        String sb = "Game: " + game.getName() + "\n" +
            "Player: " + player.getUserName() + "\n" +
            "Showed Action Card:" + "\n" +
            Mapper.getActionCard(acID).getRepresentation() + "\n";
        player.setActionCard(acID);
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
