package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class FrankenForceBagPass extends FrankenSubcommandData {
    public FrankenForceBagPass() {
        super(Constants.FORCE_BAG_PASS, "Pass all bags to the left");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping options = event.getOption(Constants.CONFIRM);
        if (options != null){
            String confirm = options.getAsString();
            if ("yes".equalsIgnoreCase(confirm)){
                FrankenDraftHelper.PassBags(activeGame);
            }
        }
        GameSaveLoadManager.saveMap(activeGame, event);

    }


}
