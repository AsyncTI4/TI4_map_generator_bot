package ti4.discord.interactions.commands.fow;

import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.image.GalaxyNames;
import ti4.message.MessageHelper;

class GalaxyCommand extends GameStateSubcommand {

    private static final String GALAXY = "galaxy";
    private static final String NAME = "name";
    private static final String AUTO = "auto";

    GalaxyCommand() {
        super(GALAXY, "GM: name the galaxies (main map and maps A-G); no options lists them", true, true);
        OptionData galaxy = new OptionData(OptionType.STRING, GALAXY, "Which galaxy: main or a-g");
        GalaxyNames.IDS.forEach(id -> galaxy.addChoice(id, id));
        addOptions(
                galaxy,
                new OptionData(OptionType.STRING, NAME, "New name: lowercase letters, digits, - (max 20)"),
                new OptionData(OptionType.BOOLEAN, AUTO, "True: go back to the automatic name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode() || !game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "Only the GM of a Fog of War game can name galaxies.");
            return;
        }
        String id = event.getOption(GALAXY, null, OptionMapping::getAsString);
        String name = event.getOption(NAME, "", OptionMapping::getAsString).trim();
        boolean auto = event.getOption(AUTO, false, OptionMapping::getAsBoolean);
        if (id == null || (name.isEmpty() && !auto)) {
            MessageHelper.replyToMessage(event, listGalaxies(game));
            return;
        }
        if (auto) {
            GalaxyNames.resetToAuto(game, id);
            MessageHelper.replyToMessage(
                    event, "Galaxy `" + id + "` is back to its automatic name `" + GalaxyNames.name(game, id) + "`.");
            return;
        }
        String problem = GalaxyNames.rename(game, id, name);
        MessageHelper.replyToMessage(event, problem != null ? problem : "Galaxy `" + id + "` is now `" + name + "`.");
    }

    private static String listGalaxies(Game game) {
        String lines = GalaxyNames.inUse(game).stream()
                .map(id -> "- `" + id + "`: `" + GalaxyNames.name(game, id) + "`"
                        + (GalaxyNames.isManual(game, id) ? " (named by the GM)" : " (automatic)"))
                .collect(Collectors.joining("\n"));
        String note = GalaxyNames.isMultiGalaxy(game)
                ? ""
                : "\n-# Only the main map is in use, so galaxy names are not shown yet.";
        return "**Galaxies in this game**\n" + lines + note;
    }
}
