package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class StartFrankenDraft extends FrankenSubcommandData {
    public StartFrankenDraft() {
        super(Constants.START_FRANKEN_DRAFT, "Start a franken draft");
        addOptions(new OptionData(OptionType.STRING, Constants.POWERED, "1 extra faction tech/ability, enter yes or no, default no"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        FrankenDraftHelper.clearPlayerHands(activeGame);

        OptionMapping stratPings = event.getOption(Constants.POWERED);
        if (stratPings != null){
            String stratP = stratPings.getAsString();
            if ("yes".equalsIgnoreCase(stratP)){
                FrankenDraftHelper.makeBags(activeGame, true);
                activeGame.setPowered(true);
            } else {
                FrankenDraftHelper.makeBags(activeGame, false);
                activeGame.setPowered(false);
            }
        }else{
            FrankenDraftHelper.makeBags(activeGame, false);
            activeGame.setPowered(false);
        }
        GameSaveLoadManager.saveMap(activeGame, event);
       
    }


}
