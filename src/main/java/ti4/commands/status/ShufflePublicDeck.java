package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

public class ShufflePublicDeck extends GameStateSubcommand {

    private static final String STAGE_OPTION_NAME = "stage";

    ShufflePublicDeck() {
        super("shuffle_public_objective_deck", "Shuffle Public Objective deck", true, false);
        addOptions(new OptionData(OptionType.INTEGER, STAGE_OPTION_NAME, "1 or 2").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int stage = event.getOption(STAGE_OPTION_NAME).getAsInt();
        if (stage != 1 && stage != 2) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Stage value must be 1 or 2.");
            return;
        }

        getGame().shuffleObjectiveDeck(stage);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Stage " + stage + " Public Objective deck shuffled.");
    }
}
