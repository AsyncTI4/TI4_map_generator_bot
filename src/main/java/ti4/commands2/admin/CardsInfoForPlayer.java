package ti4.commands2.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class CardsInfoForPlayer extends GameStateSubcommand {

    CardsInfoForPlayer() {
        super(Constants.INFO, "Resent all my cards in Private Message", false, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to resend cards info for").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CardsInfo.sendCardsInfo(getGame(), getPlayer(), event);
        MessageHelper.sendMessageToEventChannel(event, "Cards Info sent");
    }
}
