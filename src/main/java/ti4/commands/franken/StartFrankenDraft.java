package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.draft.FrankenDraft;
import ti4.draft.PoweredFrankenDraft;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class StartFrankenDraft extends FrankenSubcommandData {
    public StartFrankenDraft() {
        super(Constants.START_FRANKEN_DRAFT, "Start a franken draft");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.POWERED, "'True' to add 1 extra faction tech/ability (Default: False)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        FrankenDraftHelper.clearPlayerHands(activeGame);

        boolean stratPings = event.getOption(Constants.POWERED, false, OptionMapping::getAsBoolean);
        if (stratPings){
            activeGame.setBagDraft(new PoweredFrankenDraft(activeGame));
        } else {
            activeGame.setBagDraft(new FrankenDraft(activeGame));
        }
        
        FrankenDraftHelper.startDraft(activeGame);
        GameSaveLoadManager.saveMap(activeGame, event);
    }
}
