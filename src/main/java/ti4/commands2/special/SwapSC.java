package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

class SwapSC extends GameStateSubcommand {

    public SwapSC() {
        super(Constants.SWAP_SC, "Swap two players' strategy cards", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to swap strategy card with")
            .setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to swap strategy card with (default: you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player1 = getPlayer();
        Player player2 = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (player2 == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Players provided are the same player");
            return;
        }

        if (player1.getSCs().size() > 1 || player2.getSCs().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot swap strategy cards because a player has multiple strategy cards. Command not yet implemented for this scenario.");
            return;
        }

        Integer player1SC = player1.getSCs().stream().findFirst().get();
        Integer player2SC = player2.getSCs().stream().findFirst().get();

        if (player1SC == 0 || player2SC == 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot swap strategy cards because a player has zero strategy cards.");
            return;
        }

        player1.addSC(player2SC);
        player1.removeSC(player1SC);

        player2.addSC(player1SC);
        player2.removeSC(player2SC);

        String sb = player1.getRepresentation() + " swapped strategy cards with " + player2.getRepresentation() + "\n" +
            "> " + player2.getRepresentation() + CardEmojis.getSCFrontFromInteger(player2SC) + " " + ":arrow_right:" + " " + CardEmojis.getSCFrontFromInteger(player1SC) + "\n" +
            "> " + player1.getRepresentation() + CardEmojis.getSCFrontFromInteger(player1SC) + " " + ":arrow_right:" + " " + CardEmojis.getSCFrontFromInteger(player2SC) + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
