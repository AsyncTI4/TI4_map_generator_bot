package ti4.service.fow.setup;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;

/**
 * PLAYER_ROLES step of the FoW setup wizard: before assigning factions/positions, let the GM confirm
 * which roster entries are actually playing. {@code Game.addPlayer} gives the GM a full {@link Player}
 * entry just like every real player (their Discord GM role is the only thing that marks them
 * different), so without this step they'd show up in the FACTIONS/TABLE_ORDER candidate lists with
 * "no faction yet" and need a table position they'll never use.
 */
final class FowSetupPlayerRolesService {

    private FowSetupPlayerRolesService() {}

    /** Seeded once from {@link Player#isGM()} so the common case (GM doesn't play) needs zero clicks. */
    private static void ensureInitialized(Game game, FowSetupWizardState state) {
        if (state.isPlayerRolesInitialized()) return;
        for (Player player : game.getPlayers().values()) {
            if (!player.isDummy() && player.isGM()) {
                state.getNonPlayingUserIds().add(player.getUserID());
            }
        }
        state.setPlayerRolesInitialized(true);
        FowSetupWizardService.saveState(game, state);
    }

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        ensureInitialized(game, state);
        sb.append("Mark anyone in this list who is **not** actually playing (a GM, helper, or observer) - ")
                .append("they'll be left out of faction/position assignment and seat order. Defaults to the ")
                .append("game's GM role holder(s); toggle freely, including a GM who's also playing.\n\n");
        for (Player player : game.getPlayers().values()) {
            if (player.isDummy()) continue;
            boolean playing = !state.getNonPlayingUserIds().contains(player.getUserID());
            sb.append("> ")
                    .append(player.getUserName())
                    .append(": ")
                    .append(playing ? "**Playing**" : "_GM / Observer_")
                    .append('\n');
            buttons.add(
                    playing
                            ? Buttons.gray(
                                    "fowSetupToggleRole_" + player.getUserID(),
                                    "Mark " + player.getUserName() + " as GM/Observer")
                            : Buttons.green(
                                    "fowSetupToggleRole_" + player.getUserID(),
                                    "Mark " + player.getUserName() + " as Playing"));
        }
    }

    @ButtonHandler("fowSetupToggleRole_")
    static void toggleRole(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        String userId = buttonID.replace("fowSetupToggleRole_", "");
        if (!state.getNonPlayingUserIds().remove(userId)) {
            state.getNonPlayingUserIds().add(userId);
        }
        FowSetupWizardService.saveState(game, state);
        FowSetupWizardService.openOrRefresh(game);
    }

    /** Shared by FACTIONS/TABLE_ORDER candidate lists: non-dummy roster entries not marked GM/observer. */
    static boolean isPlayerCandidate(Player player, FowSetupWizardState state) {
        return !player.isDummy() && !state.getNonPlayingUserIds().contains(player.getUserID());
    }
}
