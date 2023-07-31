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
import ti4.map.Map;
import ti4.model.ActionCardModel;
import ti4.model.SecretObjectiveModel;

public class MakeCopiesOfACs extends ACCardsSubcommandData {
    public MakeCopiesOfACs() {
        super(Constants.MAKE_AC_COPIES, "Make Copies of ACS");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many copies to make, 2 or 3").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount;
            if (count > 3 || count < 1) {
                return;
            }
        }
        if (count == 2) {
            HashMap<String, ActionCardModel> actionCards = Mapper.getActionCards("extra1");
            List<String> ACs = new ArrayList<>(actionCards.keySet());
            activeMap.addActionCardDuplicates(ACs);
        }
        if (count ==3) {
            activeMap.triplicateRelics();
            activeMap.triplicateExplores();
            activeMap.triplicateACs();
            activeMap.triplicateSOs();

        }
    }
}
