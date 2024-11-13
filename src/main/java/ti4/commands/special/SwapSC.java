package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SwapSC extends SpecialSubcommandData {
    public SwapSC() {
        super(Constants.SWAP_SC, "Swap your SC with player2. Use OPTIONAL faction_or_color_2 to swap two other players' SCs");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to swap SC with").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.OTHER_FACTION_OR_COLOR, "Faction or Color to swap SC with").setAutoComplete(true).setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        //resolve player1
        Player player1 = CommandHelper.getPlayerFromEvent(game, event);
        if (player1 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        //resolve player2
        Player player2 = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player 2 could not be found");
            return;
        }
        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Players provided are the same player");
            return;
        }

        if (player1.getSCs().size() > 1 || player2.getSCs().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot swap SCs because One or more players have multiple SCs. Command not yet implemented for this scenario");
            return;
        }

        Integer player1SC = player1.getSCs().stream().findFirst().get();
        Integer player2SC = player2.getSCs().stream().findFirst().get();

        if (player1SC == 0 || player2SC == 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot swap SCs because One or more players have no selected an SC yet");
            return;
        }

        player1.addSC(player2SC);
        player1.removeSC(player1SC);

        player2.addSC(player1SC);
        player2.removeSC(player2SC);

        String sb = player1.getRepresentation() + " swapped SC with " + player2.getRepresentation() + "\n" +
            "> " + player2.getRepresentation() + Emojis.getSCEmojiFromInteger(player2SC) + " " + ":arrow_right:" + " " + Emojis.getSCEmojiFromInteger(player1SC) + "\n" +
            "> " + player1.getRepresentation() + Emojis.getSCEmojiFromInteger(player1SC) + " " + ":arrow_right:" + " " + Emojis.getSCEmojiFromInteger(player2SC) + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
        // ListTurnOrder.turnOrder(event, activeMap);
    }
}
