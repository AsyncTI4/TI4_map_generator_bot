package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.draft.FrankenDraft;
import ti4.draft.OnePickFrankenDraft;
import ti4.draft.PoweredFrankenDraft;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StartFrankenDraft extends FrankenSubcommandData {
    public StartFrankenDraft() {
        super(Constants.START_FRANKEN_DRAFT, "Start a franken draft");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FORCE, "'True' to forcefully overwrite existing faction setups (Default: False)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.POWERED, "'True' to add 1 extra faction tech/ability (Default: False)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONEPICK, "'True' to draft 1 item at a time. Default mode is first 3 then 2"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!force && game.getPlayers().values().stream().anyMatch(Player::isRealPlayer)) {
            String message = "There are players that are currently set up already. Please rerun the command with the force option set to True to overwrite them.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return;
        }
        
        boolean powered = event.getOption(Constants.POWERED, false, OptionMapping::getAsBoolean);
        boolean onepick = event.getOption(Constants.ONEPICK, false, OptionMapping::getAsBoolean);
        if (powered && onepick) {
          MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Choose only one mode, Powered or OnePick, not both.");
          return;
        }

        FrankenDraftHelper.setUpFrankenFactions(game, event, force);
        FrankenDraftHelper.clearPlayerHands(game);

        if (powered) {
            game.setBagDraft(new PoweredFrankenDraft(game));
        } else if (onepick) {
            game.setBagDraft(new OnePickFrankenDraft(game));
        } else {
            game.setBagDraft(new FrankenDraft(game));
        }

        FrankenDraftHelper.startDraft(game);
        GameSaveLoadManager.saveMap(game, event);
    }
}


