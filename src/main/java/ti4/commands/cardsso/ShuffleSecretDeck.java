package ti4.commands.cardsso;

import java.util.Collections;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

class ShuffleSecretDeck extends GameStateSubcommand {

    public ShuffleSecretDeck() {
        super("shuffle_deck", "Shuffles the secret objective deck", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Collections.shuffle(getGame().getSecretObjectives());
        MessageHelper.sendMessageToEventChannel(event, "Shuffled the secret objective deck.");
    }
}
