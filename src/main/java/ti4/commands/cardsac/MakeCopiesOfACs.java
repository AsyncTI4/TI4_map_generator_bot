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
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

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
            if(count > 3 || count < 1)
            {
                return;
            }
        }
        if (count == 2)
        {
            HashMap<String, ActionCardModel> actionCards = Mapper.getActionCards("extra1");
            List<String> ACs = new ArrayList<>(actionCards.keySet());
            activeMap.addActionCardDuplicates(ACs);
        }
        if(count ==3)
        {
            HashMap<String, ActionCardModel> actionCards2 = Mapper.getActionCards("extra1");
            List<String> ACs2 = new ArrayList<>(actionCards2.keySet());
            activeMap.addActionCardDuplicates(ACs2);
            actionCards2 = Mapper.getActionCards("extra2");
            ACs2 = new ArrayList<>(actionCards2.keySet());
            activeMap.addActionCardDuplicates(ACs2);
        }
    }
}
