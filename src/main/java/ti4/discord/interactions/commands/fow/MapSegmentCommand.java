package ti4.discord.interactions.commands.fow;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.MapSegment;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class MapSegmentCommand extends GameStateSubcommand {

    private static final String NAME = "name";
    private static final String RADIUS = "radius";
    private static final String CLUSTER = "cluster";
    private static final String REMOVE = "remove";
    private static final String MAKE_DEFAULT = "make_default";
    private static final String AUTO_SECTORS = "auto_sectors";
    private static final String GAP = "gap";

    MapSegmentCommand() {
        super(
                Constants.MAP_SEGMENT,
                "GM: split the map into sectors shown on their own (no options lists them)",
                true,
                true);
        addOptions(new OptionData(OptionType.STRING, NAME, "Segment name: lowercase letters, digits, - (max 20)"));
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Centre tile, or any tile of the cluster"));
        addOptions(new OptionData(OptionType.INTEGER, RADIUS, "Rings around the centre; with cluster an optional limit")
                .setRequiredRange(0, MapSegment.MAX_RADIUS));
        addOptions(new OptionData(
                OptionType.BOOLEAN, CLUSTER, "True: the segment is the connected tiles around position"));
        addOptions(new OptionData(OptionType.BOOLEAN, REMOVE, "True to delete this segment"));
        addOptions(new OptionData(
                OptionType.BOOLEAN, MAKE_DEFAULT, "True: GM view opens on this segment. False: clear the default"));
        addOptions(new OptionData(OptionType.BOOLEAN, AUTO_SECTORS, "True: every separate cluster becomes a sector"));
        addOptions(
                new OptionData(OptionType.INTEGER, GAP, "Empty hexes a cluster may jump (0-" + MapSegment.MAX_GAP + ")")
                        .setRequiredRange(0, MapSegment.MAX_GAP));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode() || !game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "Only the GM of a Fog of War game can manage map segments.");
            return;
        }
        List<String> replies = new ArrayList<>();
        Boolean autoSectors = event.getOption(AUTO_SECTORS, null, OptionMapping::getAsBoolean);
        if (autoSectors != null) {
            MapSegment.setAutoSectors(game, autoSectors);
            replies.add(
                    autoSectors
                            ? "Automatic sectors are on: every separate cluster of tiles is its own sector."
                            : "Automatic sectors are off.");
        }
        Integer gap = event.getOption(GAP, null, OptionMapping::getAsInt);
        if (gap != null) {
            MapSegment.setGap(game, gap);
            replies.add("Clusters may now jump " + gap + " empty hex(es).");
        }
        String name = event.getOption(NAME, "", OptionMapping::getAsString).trim();
        if (!name.isEmpty()) {
            replies.addAll(handleNamedSegment(event, game, name));
        }
        if (replies.isEmpty()) {
            replies.add(listSegments(game));
        }
        MessageHelper.replyToMessage(event, String.join("\n", replies));
    }

    private static List<String> handleNamedSegment(SlashCommandInteractionEvent event, Game game, String name) {
        List<String> replies = new ArrayList<>();
        if (event.getOption(REMOVE, false, OptionMapping::getAsBoolean)) {
            boolean removed = MapSegment.remove(game, name);
            replies.add(removed ? "Removed segment `" + name + "`." : "No segment called `" + name + "`.");
            return replies;
        }
        String centre = event.getOption(Constants.POSITION, null, OptionMapping::getAsString);
        if (centre != null) {
            replies.add(saveSegment(event, game, name, centre.trim()));
        }
        Boolean makeDefault = event.getOption(MAKE_DEFAULT, null, OptionMapping::getAsBoolean);
        if (makeDefault != null) {
            replies.add(updateDefault(game, name, makeDefault));
        }
        if (replies.isEmpty()) {
            replies.add(MapSegment.find(game, name)
                    .map(segment -> "Segment " + segment.describe() + ".")
                    .orElse("No segment called `" + name + "`."));
        }
        return replies;
    }

    private static String saveSegment(SlashCommandInteractionEvent event, Game game, String name, String centre) {
        boolean cluster = event.getOption(CLUSTER, false, OptionMapping::getAsBoolean);
        Integer radius = event.getOption(RADIUS, null, OptionMapping::getAsInt);
        String problem = validate(game, name, centre, radius, cluster);
        if (problem != null) {
            return problem;
        }
        MapSegment segment = cluster
                ? MapSegment.cluster(name, centre, radius == null ? 0 : radius)
                : new MapSegment(name, centre, radius);
        MapSegment.put(game, segment);
        return "Saved segment " + MapSegment.find(game, name).orElse(segment).describe() + ".";
    }

    private static String updateDefault(Game game, String name, boolean makeDefault) {
        if (!makeDefault) {
            MapSegment.clearDefault(game);
            return "No default segment any more. The GM view opens on the first segment.";
        }
        if (MapSegment.find(game, name).isEmpty()) {
            return "No segment called `" + name + "`, so it cannot be the default.";
        }
        MapSegment.setDefault(game, name);
        return "`" + name + "` is now the default segment.";
    }

    private static String validate(Game game, String name, String centre, Integer radius, boolean cluster) {
        if (!MapSegment.isValidName(name)) {
            return "Segment names use lowercase letters, digits and `-`, up to 20 characters.";
        }
        if (MapSegment.isReservedName(name)) {
            return "`" + MapSegment.MAIN + "` and `" + MapSegment.FRACTURE + "` are reserved segment names.";
        }
        if (radius == null && !cluster) {
            return "Give a `radius`, or set `cluster` to true.";
        }
        if (!PositionMapper.isTilePositionValid(centre)) {
            return "Tile position `" + centre + "` is invalid.";
        }
        if (cluster && game.getTileByPosition(centre) == null) {
            return "There is no tile at `" + centre + "` to grow a cluster from.";
        }
        boolean isNew = MapSegment.stored(game).stream()
                .noneMatch(segment -> segment.name().equals(name));
        if (isNew && MapSegment.stored(game).size() >= MapSegment.MAX_SEGMENTS) {
            return "This game already has the maximum of " + MapSegment.MAX_SEGMENTS + " segments.";
        }
        return null;
    }

    private static String listSegments(Game game) {
        List<MapSegment> segments = MapSegment.all(game);
        String settings = "Automatic sectors: **" + (MapSegment.isAutoSectors(game) ? "on" : "off")
                + "**, cluster gap: **" + MapSegment.gap(game) + "**.";
        if (segments.isEmpty()) {
            return "No map segments are defined. Maps frame themselves automatically.\n" + settings;
        }
        String defaultName =
                MapSegment.defaultSegment(game).map(MapSegment::name).orElse(null);
        return "Map segments:\n"
                + segments.stream()
                        .map(segment ->
                                "- " + segment.describe() + (segment.name().equals(defaultName) ? " (default)" : ""))
                        .collect(Collectors.joining("\n"))
                + "\n" + settings;
    }
}
