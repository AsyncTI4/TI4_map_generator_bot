package ti4.commands.draft.manage;

import java.util.Collection;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class DraftManagerSetupNucleusCommand extends GameStateSubcommand {
    public DraftManagerSetupNucleusCommand() {
        super(
                Constants.DRAFT_MANAGE_SETUP_NUCLEUS,
                "Setup a Nucleus draft, throwing out any current draft settings",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Collection<Player> players = game.getPlayers().values();
        if (players.size() < 3) {
            String msg = "Need at least 3 players in the game to setup a Nucleus draft, only found "
                    + String.join(
                            ", ",
                            players.stream().map(Player::getRepresentation).toArray(String[]::new)) + ".";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            return;
        }
        if (players.size() > 8) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Nucleus templates only go up to 8 players; you'll need to remove some people from the draft and then select a template.");
        }
        DraftSystemSettings settings = new DraftSystemSettings(game, null);
        settings.setupNucleusPreset();
        game.setDraftSystemSettings(settings);
        settings.postMessageAndButtons(event);
    }
}
