package ti4.service.franken;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.FrankenDraft;
import ti4.draft.FrankenDrazDraft;
import ti4.draft.InauguralSpliceFrankenDraft;
import ti4.draft.OnePickFrankenDraft;
import ti4.draft.OverdraftFrankenDraft;
import ti4.draft.PoweredFrankenDraft;
import ti4.draft.PoweredOnePickFrankenDraft;
import ti4.draft.PoweredOverdraftFrankenDraft;
import ti4.draft.TwilightsFallFrankenDraft;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class FrankenDraftStartService {

    public String startFrankenDraft(
            GenericInteractionCreateEvent event, Game game, boolean force, FrankenDraftMode draftMode) {
        String validationError = validateStartFrankenDraft(game, force);
        if (validationError != null) return validationError;

        FrankenDraftBagService.setUpFrankenFactions(game, event, force);
        FrankenDraftBagService.clearPlayerHands(game);

        if (draftMode == null) {
            game.setBagDraft(new FrankenDraft(game));
        } else {
            switch (draftMode) {
                case POWERED -> game.setBagDraft(new PoweredFrankenDraft(game));
                case ONEPICK -> game.setBagDraft(new OnePickFrankenDraft(game));
                case OVERDRAFT -> game.setBagDraft(new OverdraftFrankenDraft(game));
                case POWEREDONEPICK -> game.setBagDraft(new PoweredOnePickFrankenDraft(game));
                case POWEREDOVERDRAFT -> game.setBagDraft(new PoweredOverdraftFrankenDraft(game));
                case FRANKENDRAZ -> game.setBagDraft(new FrankenDrazDraft(game));
                case TWILIGHTSFALL -> {
                    game.setupTwilightsFallMode(event);
                    game.setBagDraft(new TwilightsFallFrankenDraft(game));
                }
                case INAUGURALSPLICE -> {
                    game.setupTwilightsFallMode(event);
                    game.setBagDraft(new InauguralSpliceFrankenDraft(game));
                }
            }
        }

        FrankenDraftBagService.startDraft(game);
        return null;
    }

    public String validateStartFrankenDraft(Game game, boolean force) {
        if (!force
                && game.getPlayers().values().stream().anyMatch(Player::isRealPlayer)
                && !game.isTwilightsFallMode()) {
            return "There are players that are currently set up already. Please rerun with force enabled to overwrite them.";
        }
        return null;
    }
}
