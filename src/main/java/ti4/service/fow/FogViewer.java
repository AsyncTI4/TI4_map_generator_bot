package ti4.service.fow;

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.service.testbed.TestBedService;

public record FogViewer(
        Game game, boolean privateView, @Nullable Player player) {

    public static FogViewer resolve(
            Game game, @Nullable GenericInteractionCreateEvent event, @Nullable Player explicitViewer) {
        return new FogViewer(game, isPrivateView(game, event), viewerOf(game, event, explicitViewer));
    }

    public static FogViewer unfogged(Game game) {
        return new FogViewer(game, false, null);
    }

    public static FogViewer of(Game game, @Nullable Player viewer) {
        return new FogViewer(game, false, viewer);
    }

    private static boolean isPrivateView(Game game, @Nullable GenericInteractionCreateEvent event) {
        return game.isFowMode()
                && event != null
                && (event instanceof UserOverridenGenericInteractionCreateEvent || isInPrivateChannel(event));
    }

    private static boolean isInPrivateChannel(GenericInteractionCreateEvent event) {
        Channel channel = event.getChannel();
        if (channel instanceof ThreadChannel thread) {
            channel = thread.getParentChannel();
        }
        return channel == null || channel.getName().endsWith(Constants.PRIVATE_CHANNEL);
    }

    @Nullable
    private static Player viewerOf(
            Game game, @Nullable GenericInteractionCreateEvent event, @Nullable Player explicitViewer) {
        if (explicitViewer != null) {
            return explicitViewer;
        }
        return event == null ? null : seatOf(game, event);
    }

    @Nullable
    public static Player seatOf(Game game, GenericInteractionCreateEvent event) {
        Player eventPlayer = CommandHelper.getPlayerFromGame(
                game, event.getMember(), event.getUser().getId());
        return TestBedService.resolveActingPlayer(game, event, eventPlayer);
    }

    public boolean canSeeStats(@Nullable Player owner) {
        return player != null && owner != null && FoWHelper.canSeeStatsOfPlayerInCurrentFog(game, owner, player);
    }

    public boolean canSeeStatsOfFaction(String faction) {
        return player != null && FoWHelper.canSeeStatsOfFactionInCurrentFog(game, faction, player);
    }

    public boolean seesHomeSystem(@Nullable Player owner) {
        return player != null && owner != null && FoWHelper.hasHomeSystemInView(owner, player);
    }

    public Set<String> visibleSystems() {
        return player == null ? Collections.emptySet() : FoWHelper.visibleSystems(game, player);
    }

    public Set<String> positionsInReach() {
        return player == null ? Collections.emptySet() : FoWHelper.getTilePositionsToShow(game, player);
    }
}
