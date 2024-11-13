package ti4.commands2.admin;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.commands.uncategorized.CardsInfo;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class CardsInfoForPlayer extends GameStateSubcommand {

    CardsInfoForPlayer() {
        super(Constants.INFO, "Resent all my cards in Private Message", false, false);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to which to show Action Card").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        User user = playerOption.getAsUser();
        Player player = getGame().getPlayer(user.getId());
        CardsInfo.sendCardsInfo(getGame(), player, event);
        MessageHelper.sendMessageToEventChannel(event, "Cards Info sent");
    }
}
