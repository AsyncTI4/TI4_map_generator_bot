package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

class MakeCopiesOfACs extends GameStateSubcommand {

    public MakeCopiesOfACs() {
        super(Constants.MAKE_AC_COPIES, "Make Copies of action cards", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many copies to make, 2 or 3").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT).getAsInt();
        if (count > 3 || count < 1) {
            MessageHelper.replyToMessage(event, "Count must be between 2 or 3.");
            return;
        }

        Game game = getGame();
        if (count == 2) {
            Map<String, ActionCardModel> actionCards = Mapper.getActionCards("extra1");
            List<String> ACs = new ArrayList<>(actionCards.keySet());
            game.addActionCardDuplicates(ACs);
        }
        if (count == 3) {
            game.triplicateExplores();
            game.triplicateACs();
            game.triplicateSOs();
        }
    }
}
