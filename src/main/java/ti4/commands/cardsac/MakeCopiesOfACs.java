package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.model.ActionCardModel;

public class MakeCopiesOfACs extends ACCardsSubcommandData {
    public MakeCopiesOfACs() {
        super(Constants.MAKE_AC_COPIES, "Make Copies of ACS");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many copies to make, 2 or 3").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
          count = option.getAsInt();
            if (count > 3 || count < 1) {
                return;
            }
        }
        if (count == 2) {
            HashMap<String, ActionCardModel> actionCards = Mapper.getActionCards("extra1");
            List<String> ACs = new ArrayList<>(actionCards.keySet());
            activeGame.addActionCardDuplicates(ACs);
        }
        if (count ==3) {
            activeGame.triplicateRelics();
            activeGame.triplicateExplores();
            activeGame.triplicateACs();
            activeGame.triplicateSOs();

        }
    }
}
