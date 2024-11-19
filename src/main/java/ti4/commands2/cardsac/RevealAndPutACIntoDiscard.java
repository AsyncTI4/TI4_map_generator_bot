package ti4.commands2.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RevealAndPutACIntoDiscard extends GameStateSubcommand {

    public RevealAndPutACIntoDiscard() {
        super(Constants.REVEAL_AND_PUT_AC_INTO_DISCARD, "Reveal Action Card from deck and put into discard pile", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String acID = game.drawActionCardAndDiscard();
        String sb = "Game: " + game.getName() + " " +
            "Player: " + getPlayer().getUserName() + "\n" +
            "Revealed and discarded Action card: " +
            Mapper.getActionCard(acID).getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
    }
}
