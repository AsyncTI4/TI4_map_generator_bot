package ti4.discord.interactions.commands.megagame;

import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.map.MapFocus;
import ti4.image.map.MapRenderPipeline;
import ti4.message.MessageHelper;
import ti4.service.megagame.MegagamePlayerReach;

class MegagameShowGameAs extends MegagameEphemeralSubcommand {

    private static final String DISTANCE = "distance";
    private static final int MAX_DISTANCE = 10;

    MegagameShowGameAs() {
        super("show_game_as", "Map lit around one player's systems, rest darkened, only their stats (only to you)");
        addOptions(
                factionOption(),
                new OptionData(
                                OptionType.INTEGER,
                                DISTANCE,
                                "Also light systems up to this many steps away (default 1)")
                        .setRequiredRange(0, MAX_DISTANCE));
    }

    @Override
    void runEphemeral(SlashCommandInteractionEvent event, Game game) {
        Player target = targetPlayer(event, game);
        if (target == null) {
            reply(event, "Could not find that player.");
            return;
        }
        int distance = event.getOption(DISTANCE, 1, OptionMapping::getAsInt);
        Set<String> presence = MegagamePlayerReach.presence(game, target);
        Set<String> lit = MegagamePlayerReach.withinDistance(game, target, presence, distance);
        reply(
                event,
                "Showing the game as " + target.getRepresentationUnfoggedNoPing() + " with distance " + distance + ": "
                        + presence.size() + " systems with presence, " + lit.size() + " lit.");
        MapRenderPipeline.queueFocused(
                game,
                new MapFocus(target.getFaction(), lit),
                fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }
}
