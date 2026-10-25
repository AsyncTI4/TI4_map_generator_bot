package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.RenderRandom;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel.PlanetType;

final class PlanetRow {

    private static final int SUMMARY_WIDTH = 156;
    private static final int GROUP_GAP = 30;
    private static final List<String> CORNER_POSITIONS = List.of("tl", "tr", "bl", "br");
    private static final String SUMMARY_TEXT =
            "This is an overview of your resources and influence. The left side is resources, and the right side is influence.\nThe top number how many you have available\nThe middle number is the total\nThe bottom number is the 'optimal' available\nThe bottom-centre number is the flex 'optimal' available";

    private PlanetRow() {}

    static void draw(PlayerAreaContext ctx, Player player, int x, int y) {
        if (player.getPlanets().isEmpty()) {
            return;
        }
        Game game = ctx.game();
        List<String> planets = player.getPlanets();
        ctx.g2().setStroke(Strokes.STROKE_2);

        drawSummary(ctx, player, x, y);
        int deltaX = SUMMARY_WIDTH;

        if (player != ctx.viewer() && ctx.fogPrivate()) {
            RenderRandom.shuffle("playerArea.planetShuffle", planets);
        }

        List<String> realPlanets = new ArrayList<>();
        List<String> nonTile = new ArrayList<>();
        List<String> fakePlanets = new ArrayList<>();
        for (String planet : planets) {
            PlanetModel model = Mapper.getPlanet(planet);
            Set<PlanetType> types = EnumSet.noneOf(PlanetType.class);
            if (model != null && model.getPlanetTypes() != null) types.addAll(model.getPlanetTypes());

            if (types.contains(PlanetType.FAKE) || types.contains(PlanetType.SPACESTATION)) {
                fakePlanets.add(planet);
            } else if (game.getTileFromPlanet(planet) == null) {
                nonTile.add(planet);
            } else {
                realPlanets.add(planet);
            }
        }

        Tile homeTile = player.getHomeSystemTile();
        if (homeTile != null) {
            realPlanets.sort(byDistanceFromHome(game, creussGateOr(game, homeTile)));
        }

        for (String planet : realPlanets) {
            deltaX = PlanetCard.draw(ctx, player, planet, x, y, deltaX);
        }
        deltaX = drawGroup(ctx, player, nonTile, x, y, deltaX);
        deltaX = drawGroup(ctx, player, fakePlanets, x, y, deltaX);
        drawGroup(ctx, player, game.getPlanetsPlayerIsCoexistingOn(player), x, y, deltaX);
    }

    private static int drawGroup(PlayerAreaContext ctx, Player player, List<String> planets, int x, int y, int deltaX) {
        if (planets.isEmpty()) {
            return deltaX;
        }
        deltaX += GROUP_GAP;
        for (String planet : planets) {
            deltaX = PlanetCard.draw(ctx, player, planet, x, y, deltaX);
        }
        return deltaX;
    }

    private static Tile creussGateOr(Game game, Tile tile) {
        if (tile == null) {
            return null;
        }
        String gateTileID =
                switch (tile.getTileID()) {
                    case "51" -> "17";
                    case "118" -> "94";
                    default -> null;
                };
        if (gateTileID == null) {
            return tile;
        }
        Tile creussGate = game.getTile(gateTileID);
        return creussGate != null ? creussGate : tile;
    }

    private static Comparator<String> byDistanceFromHome(Game game, Tile homeTile) {
        String homePos = homeTile.getPosition();
        Point homePosition = MapPositionMapper.getTilePosition(homePos);
        return (planet1, planet2) -> {
            if (homePosition == null) return 0;

            Tile tile1 = creussGateOr(game, game.getTileFromPlanet(planet1));
            Tile tile2 = creussGateOr(game, game.getTileFromPlanet(planet2));
            if (tile1 == null || tile2 == null)
                return Comparator.nullsLast(null).compare(tile1, tile2);

            Point p1 = MapPositionMapper.getTilePosition(tile1.getPosition());
            Point p2 = MapPositionMapper.getTilePosition(tile2.getPosition());
            if (p1 == null || p2 == null) return Comparator.nullsLast(null).compare(p1, p2);

            int distance1 = distanceFromHome(homePos, homePosition, tile1, p1, planet1);
            int distance2 = distanceFromHome(homePos, homePosition, tile2, p2, planet2);
            if (distance1 != distance2) {
                return distance1 - distance2;
            }
            if (!tile1.getPosition().equalsIgnoreCase(tile2.getPosition())) {
                return tile2.getPosition().compareToIgnoreCase(tile1.getPosition());
            }
            return planet1.compareToIgnoreCase(planet2);
        };
    }

    private static int distanceFromHome(String homePos, Point homePosition, Tile tile, Point position, String planet) {
        if (homePos.equals(tile.getPosition())) {
            return 0;
        }
        if (tile.isFracture()) {
            return "styx".equals(planet) ? 9000 : 8000;
        }
        if (CORNER_POSITIONS.contains(tile.getPosition())) {
            return 7000;
        }
        return (homePosition.x - position.x) * (homePosition.x - position.x)
                + (homePosition.y - position.y) * (homePosition.y - position.y);
    }

    private static void drawSummary(PlayerAreaContext ctx, Player player, int x, int y) {
        Graphics2D g2 = ctx.g2();
        PaImage.draw(g2, x - 2, y - 2, "pa_resinf_info.png");
        g2.setColor(Color.WHITE);
        CardRect.draw(ctx, x - 2, y - 2, 152, 152, "Resource & Influence Summary", SUMMARY_TEXT);
        if (player.hasUnlockedBreakthrough("xxchabt") || player.hasLeaderUnlocked("xxchahero")) {
            drawCombinedSummary(ctx, player, x, y);
        } else {
            drawSplitSummary(ctx, player, x, y);
        }
    }

    private static void drawCombinedSummary(PlayerAreaContext ctx, Player player, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
        int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
        if (Constants.gedsDeadId.equals(player.getUserID()) || RenderRandom.oneIn("playerArea.gedsDead", 100)) {
            PaImage.drawOpaque(g2, x - 2, y - 2, "pa_resinf_info_xxcha_gedsdead.png", 0.9f);
        } else {
            PaImage.drawOpaque(g2, x - 2, y - 2, "pa_resinf_info_xxcha.png", 0.9f);
        }
        FactionIcons.drawFactionFaded(g2, "xxcha", x + 75 - 94 / 2, y + 75 - 94 / 2, 95, 95, 0.15f);
        g2.setColor(Color.WHITE);
        OutlinedText.drawCentered(
                g2, String.valueOf(availablePlayerResources), new Rectangle(x, y + 75 - 35 + 5, 150, 35), Fonts.ti(35));
        g2.setColor(Color.GRAY);
        OutlinedText.drawCentered(
                g2, String.valueOf(totalPlayerResources), new Rectangle(x, y + 75 + 5, 150, 24), Fonts.ti(24));
    }

    private static void drawSplitSummary(PlayerAreaContext ctx, Player player, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
        int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
        int availablePlayerResourcesOptimal = Helper.getPlayerOptimalResourcesAvailable(player, game);
        int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, game);
        int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, game);
        int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, game);
        int availablePlayerFlex = Helper.getPlayerFlexResourcesInfluenceAvailable(player, game);

        drawColumn(
                g2,
                x + 30,
                y,
                availablePlayerResources,
                totalPlayerResources,
                availablePlayerResourcesOptimal,
                Color.decode("#d5bd4f"));
        drawColumn(
                g2,
                x + 90,
                y,
                availablePlayerInfluence,
                totalPlayerInfluence,
                availablePlayerInfluenceOptimal,
                Color.decode("#57b9d9"));

        g2.setColor(Color.WHITE);
        if (Constants.cagesId.equals(player.getUserID())) g2.setColor(Color.decode("#f616ce"));
        OutlinedText.drawCentered(
                g2, String.valueOf(availablePlayerFlex), new Rectangle(x, y + 115, 150, 20), Fonts.ti(18));
    }

    private static void drawColumn(
            Graphics2D g2, int x, int y, int available, int total, int optimal, Color optimalColor) {
        g2.setColor(Color.WHITE);
        OutlinedText.drawCentered(g2, String.valueOf(available), new Rectangle(x, y + 30, 32, 32), Fonts.ti(32));
        g2.setColor(Color.GRAY);
        OutlinedText.drawCentered(g2, String.valueOf(total), new Rectangle(x, y + 55, 32, 32), Fonts.ti(20));
        g2.setColor(optimalColor);
        OutlinedText.drawCentered(g2, String.valueOf(optimal), new Rectangle(x, y + 90, 32, 32), Fonts.ti(18));
    }
}
