package ti4.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ti4.ResourceHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CalendarHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitRenderGenerator {

    private final ResourceHelper resourceHelper = ResourceHelper.getInstance();

    private record ImagePosition(
        int originalX,
        int originalY,
        int x,
        int y
    ) {}

    private record SystemContext(
        boolean isSpace,
        boolean isMirage,
        boolean hasMirage,
        boolean isJail,
        boolean showJail,
        Point unitOffset,
        float mirageDragRatio,
        int mirageDragX,
        int mirageDragY,
        int spaceUnitXOffset,
        int spaceUnitYOffset,
        UnitTokenPosition unitTokenPosition
    ) {}

    private record PositioningContext(
        Point centerPosition,
        String holderName,
        String tileID,
        String unitId,
        BufferedImage unitImage,
        int planetHolderSize,
        boolean fighterOrInfantry
    ) {}

    private final Game game;
    private final Tile tile;
    private final DisplayType displayType;
    private static final Point numberPositionPoint = new Point(40, 27);
    private final int TILE_PADDING = 100;
    private final Graphics tileGraphics;
    private final List<Rectangle> rectangles;
    private final int degree;
    private final int degreeChange;
    private final UnitHolder unitHolder;
    private final int radius;
    private final Player frogPlayer;

    SystemContext ctx;

    public UnitRenderGenerator(Game game, DisplayType displayType, Tile tile, Graphics tileGraphics, List<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius, Player frogPlayer) {
        this.game = game;
        this.tile = tile;
        this.displayType = displayType;
        this.tileGraphics = tileGraphics;
        this.rectangles = rectangles;
        this.degree = degree;
        this.degreeChange = degreeChange;
        this.unitHolder = unitHolder;
        this.radius = radius;
        this.frogPlayer = frogPlayer;
    }

    public void render() {
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);
        boolean containsDMZ = unitHolder.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
        if (isSpace && displayType == DisplayType.shipless) return;

        BufferedImage unitImage;
        Map<UnitKey, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        Map<UnitKey, Integer> units = sortUnits(tempUnits);
        Map<UnitKey, Integer> unitDamage = unitHolder.getUnitDamage();

        // Contains pre-computed values used in unit rendering.
        ctx = buildSystemContext(tile, unitHolder, frogPlayer);

        BufferedImage dmgImage = ImageHelper.readScaled(Helper.getDamagePath(), 0.8f);

        Map<UnitType, Integer> unitTypeCounts = new HashMap<>();

        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            if (shouldSkipInvalidUnit(unitKey)) continue;
            if (shouldHideJailUnit(frogPlayer, unitKey)) continue;

            Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
            if (player == null) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Could not find owner for " + unitKey + " in tile " + tile.getRepresentation());
                continue;
            }
            Integer unitCount = unitEntry.getValue();
            Integer bulkUnitCount = getBulkUnitCount(unitKey, unitCount);
            String unitPath = getUnitPath(unitKey);

            unitTypeCounts.putIfAbsent(unitKey.getUnitType(), 0);

            float scale = (bulkUnitCount != null && bulkUnitCount > 9) ? 1.2f : 1.0f;
            try {
                unitImage = scale == 1.0f ? ImageHelper.read(unitPath) : ImageHelper.readScaled(unitPath, scale);

            } catch (Exception e) {
                BotLogger.error("Could not parse unit file for: " + unitKey + " in game " + game.getName(), e);
                continue;
            }
            if (unitImage == null) continue;
            if (bulkUnitCount != null && bulkUnitCount > 0) unitCount = 1;

            Integer unitDamageCount = unitDamage.get(unitKey);
            BufferedImage decal = getUnitDecal(player, unitKey);
            BufferedImage spoopy = getSpoopyImage(unitKey, player);
            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            if (unitModel == null) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " a unit model could not be found for the unit with an async ID of " + unitKey.asyncID());
                continue;
            }

            // Contains pre-computed values common to this 'unit class'
            // (e.g. all fighters, all infantry, all mechs, etc.)
            PositioningContext posCtx = new PositioningContext(
                unitHolder.getHolderCenterPosition(),
                unitHolder.getName(),
                tile.getTileID(),
                unitKey.asyncID(),
                unitImage,
                tile.getPlanetUnitHolders().size(),
                Set.of(UnitType.Infantry, UnitType.Fighter).contains(unitKey.getUnitType()));

            // DRAW UNITS
            for (int i = 0; i < unitCount; i++) {
                Point position = calculateUnitPosition(posCtx, unitKey, i + unitTypeCounts.get(unitKey.getUnitType()));
                ImagePosition imagePos = calculateImagePosition(posCtx, position);
                int imageX = imagePos.x();
                int imageY = imagePos.y();

                if (containsDMZ || (isSpace && unitModel.getIsPlanetOnly()) || (!isSpace && unitModel.getIsSpaceOnly())) {
                    String badPath = resourceHelper.getPositionFile("badpos_" + (bulkUnitCount != null ? "tkn_" : "") + unitKey.asyncID() + ".png");
                    BufferedImage badPositionImage = scale == 1.0f ? ImageHelper.read(badPath) : ImageHelper.readScaled(badPath, scale);
                    tileGraphics.drawImage(badPositionImage, imageX - 5, imageY - 5, null);
                }

                if (unitKey.getUnitType() == UnitType.Spacedock && player.ownsUnitSubstring("cabal_spacedock")) {
                    BufferedImage dimTear = ImageHelper.read(resourceHelper.getDecalFile("DimensionalTear.png"));
                    if (dimTear != null) {
                        int dtX = imageX + (unitImage.getWidth() - dimTear.getWidth()) / 2;
                        int dtY = imageY + (unitImage.getHeight() - dimTear.getHeight()) / 2;
                        tileGraphics.drawImage(dimTear, dtX, dtY, null);
                    }
                }

                if (!"caballed".equals(player.getDecalSet()) || decal == null || posCtx.fighterOrInfantry) {
                    tileGraphics.drawImage(unitImage, imageX, imageY, null);
                }

                if (!posCtx.fighterOrInfantry) {
                    tileGraphics.drawImage(decal, imageX, imageY, null);
                }

                if ("81".equals(tile.getTileID()) && "muaat".equals(player.getFaction()) && unitKey.getUnitType() == UnitType.Warsun) {
                    BufferedImage faceNovaSeed = ImageHelper.read(resourceHelper.getDecalFile("NovaSeed.png"));
                    tileGraphics.drawImage(faceNovaSeed, imageX, imageY, null);
                } else if (spoopy != null) {
                    tileGraphics.drawImage(spoopy, imageX, imageY, null);
                }

                // INFORMATIONAL DECALS
                optionallyDrawMechTearDecal(unitKey, imageX, imageY);
                optionallyDrawWarsunCrackDecal(unitKey, imageX, imageY);

                // UNIT TAGS
                drawUnitTags(unitKey, player, imagePos, i);

                if (bulkUnitCount != null) {
                    Color groupUnitColor = switch (Mapper.getColor(unitKey.getColorID()).getTextColor()) {
                        case "black" -> Color.BLACK;
                        default -> Color.WHITE;
                    };
                    Color groupUnitStroke = switch (Mapper.getColor(unitKey.getColorID()).getTextColor()) {
                        case "black" -> Color.WHITE;
                        default -> Color.BLACK;
                    };
                    drawBulkUnitCount(tileGraphics, bulkUnitCount, groupUnitColor, groupUnitStroke, imagePos); // TODO: can only show two player's Fighter/Infantry
                }

                if (unitDamageCount != null && unitDamageCount > 0 && dmgImage != null) {
                    drawDamageIcon(position, imagePos, unitImage, dmgImage, unitKey.getUnitType());
                    unitDamageCount--;
                }
            }

            // Persist unit type counts
            if (unitTypeCounts.containsKey(unitKey.getUnitType())) {
                unitTypeCounts.put(unitKey.getUnitType(), unitTypeCounts.get(unitKey.getUnitType()) + unitCount);
            } else {
                unitTypeCounts.put(unitKey.getUnitType(), unitCount);
            }
        }
    }

    private void optionallyDrawMechTearDecal(UnitKey unitKey, int imageX, int imageY) {
        if (unitKey.getUnitType() != UnitType.Mech || !ButtonHelper.anyLawInPlay(game, "articles_war", "absol_articleswar")) return;
        String imagePath = "agenda_articles_of_war" + DrawingUtil.getBlackWhiteFileSuffix(unitKey.getColorID());
        BufferedImage mechTearImage = ImageHelper.read(resourceHelper.getTokenFile(imagePath));
        tileGraphics.drawImage(mechTearImage, imageX, imageY, null);
    }

    private void optionallyDrawWarsunCrackDecal(UnitKey unitKey, int imageX, int imageY) {
        if (unitKey.getUnitType() != UnitType.Warsun || !ButtonHelper.isLawInPlay(game, "schematics")) return;
        String imagePath = "agenda_publicize_weapon_schematics" + DrawingUtil.getBlackWhiteFileSuffix(unitKey.getColorID());
        BufferedImage wsCrackImage = ImageHelper.read(resourceHelper.getTokenFile(imagePath));
        tileGraphics.drawImage(wsCrackImage, imageX, imageY, null);
    }

    private void drawUnitTags(UnitKey unitKey, Player player, ImagePosition imagePos, int iteration) {
        if (iteration != 0 || UnitType.Infantry.equals(unitKey.getUnitType()) || !game.isShowUnitTags()) {
            return;
        }

        UnitModel unitModel = game.getUnitFromUnitKey(unitKey);
        if (player == null || unitModel == null || !unitModel.getIsShip()) {
            return;
        }

        String factionTag = player.getFactionModel().getShortTag();
        BufferedImage plaquette = ImageHelper.read(resourceHelper.getUnitFile("unittags_plaquette.png"));
        Point plaquetteOffset = getUnitTagLocation(unitKey.asyncID());

        int tagX = imagePos.x() + plaquetteOffset.x;
        int tagY = imagePos.y() + plaquetteOffset.y;

        // Draw plaquette background
        tileGraphics.drawImage(plaquette, tagX, tagY, null);

        // Draw faction icon
        DrawingUtil.drawPlayerFactionIconImage(tileGraphics, player, tagX, tagY, 32, 32);

        // Draw faction tag text
        tileGraphics.setColor(Color.WHITE);
        Rectangle textBounds = new Rectangle(tagX + 25, tagY + 17, 40, 13);
        DrawingUtil.drawCenteredString(tileGraphics, factionTag, textBounds, Storage.getFont13());
    }

    private SystemContext buildSystemContext(Tile tile, UnitHolder unitHolder, Player frogPlayer) {
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);
        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
        boolean hasMirage = false;
        if (isSpace) {
            Set<String> tokenList = unitHolder.getTokenList();
            hasMirage = tokenList.stream().anyMatch(tok -> tok.contains("mirage"))
                && (tile.getPlanetUnitHolders().size() != 3 + 1);
        }

        Map<String, String> jailTiles = Map.of(
            "s11", "cabal",
            "s12", "nekro",
            "s13", "yssaril");

        String jailFaction = jailTiles.get(tile.getTileID());
        boolean isJail = jailTiles.get(tile.getTileID()) != null;
        boolean showJail = frogPlayer == null ||
            (isJail && FoWHelper.canSeeStatsOfFaction(game, jailFaction, frogPlayer));

        float mirageDragRatio = 2.0f / 3;
        int mirageDragX = Math.round(((float) 345 / 8 + TILE_PADDING) * (1 - mirageDragRatio));
        int mirageDragY = Math.round(((float) (3 * 300) / 4 + TILE_PADDING) * (1 - mirageDragRatio));

        Point unitOffset = game.isAllianceMode() ? PositionMapper.getAllianceUnitOffset() : PositionMapper.getUnitOffset();

        int spaceUnitXOffset = unitOffset != null ? unitOffset.x : 10;
        int spaceUnitYOffset = unitOffset != null ? unitOffset.y : -7;

        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            unitTokenPosition = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
        }

        return new SystemContext(
            isSpace,
            isMirage,
            hasMirage,
            isJail,
            showJail,
            unitOffset,
            mirageDragRatio,
            mirageDragX,
            mirageDragY,
            spaceUnitXOffset,
            spaceUnitYOffset,
            unitTokenPosition);
    }

    private void drawBulkUnitCount(Graphics g, int count, Color color, Color stroke, ImagePosition imagePos) {
        g.setFont(count > 9 ? Storage.getFont28() : Storage.getFont24());
        int offsetX = numberPositionPoint.x + (count > 9 ? 5 : 0);
        int offsetY = numberPositionPoint.y + (count > 9 ? 5 : 0);
        BasicStroke strokeWidth = new BasicStroke(4.0f);
        DrawingUtil.superDrawString((Graphics2D) g, count == 1 ? " 1" : Integer.toString(count), imagePos.x() + offsetX, imagePos.y() + offsetY, color,
            MapGenerator.HorizontalAlign.Left, MapGenerator.VerticalAlign.Bottom, strokeWidth, stroke);
    }

    private void drawDamageIcon(
        Point unitPos,
        ImagePosition imagePos,
        BufferedImage unitImage,
        BufferedImage dmgImage,
        UnitType unitType
    ) {
        int imageX = imagePos.x();
        int imageY = imagePos.y();

        int imageDmgX, imageDmgY;

        if (ctx.isMirage) {
            imageDmgX = imageX - TILE_PADDING;
            imageDmgY = imageY - TILE_PADDING;
        } else if (unitType == UnitType.Mech) {
            imageDmgX = getDamageX(unitPos, imagePos, dmgImage);
            imageDmgY = getDamageY(unitPos, imagePos, dmgImage);
        } else {
            imageDmgX = getCenteredDamageX(unitPos, imagePos, unitImage, dmgImage);
            imageDmgY = getCenteredDamageY(unitPos, imagePos, unitImage, dmgImage);
        }

        if (unitPos != null && ctx.isSpace) {
            imageDmgX -= 7;
        }

        tileGraphics.drawImage(dmgImage, TILE_PADDING + imageDmgX, TILE_PADDING + imageDmgY, null);
    }

    private int getDamageX(Point position, ImagePosition imagePosition, BufferedImage dmgImage) {
        return position != null ? position.x : imagePosition.originalX() - dmgImage.getWidth();
    }

    private int getDamageY(Point position, ImagePosition imagePosition, BufferedImage dmgImage) {
        return position != null ? position.y : imagePosition.originalY() - dmgImage.getHeight();
    }

    private int getCenteredDamageX(Point position, ImagePosition imagePosition, BufferedImage unitImage, BufferedImage dmgImage) {
        return position != null
            ? position.x + (unitImage.getWidth() / 2) - (dmgImage.getWidth() / 2)
            : imagePosition.originalX() - (dmgImage.getWidth() / 2);
    }

    private int getCenteredDamageY(Point position, ImagePosition imagePosition, BufferedImage unitImage, BufferedImage dmgImage) {
        return position != null
            ? position.y + (unitImage.getHeight() / 2) - (dmgImage.getHeight() / 2)
            : imagePosition.originalY() - (dmgImage.getHeight() / 2);
    }

    private boolean shouldSkipInvalidUnit(UnitKey unitKey) {
        return unitKey == null || !Mapper.isValidColor(unitKey.getColor());
    }

    private boolean shouldHideJailUnit(Player frogPlayer, UnitKey unitKey) {
        if (!ctx.isJail || frogPlayer == null) {
            return false;
        }
        String colorID = Mapper.getColorID(frogPlayer.getColor());
        return !ctx.showJail && !unitKey.getColorID().equals(colorID);
    }

    private String getUnitPath(UnitKey unitKey) {
        String unitPath = resourceHelper.getUnitFile(unitKey);
        if (unitPath == null) return null;

        // Handle bulk unit replacements
        unitPath = switch (unitKey.getUnitType()) {
            case Fighter -> unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
            case Infantry -> unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
            default -> unitPath;
        };

        Player player = game.getPlayerByColorID(unitKey.getColorID()).orElse(null);
        if (player == null) return unitPath;

        // Handle special unit replacements
        return switch (unitKey.getUnitType()) {
            case Lady -> unitPath.replace("lady", "fs");
            case Cavalry -> {
                boolean hasM2Tech = game.getPNOwner("cavalry") != null &&
                    game.getPNOwner("cavalry").hasTech("m2");
                String version = hasM2Tech ? "Memoria_2.png" : "Memoria_1.png";
                yield resourceHelper.getUnitFile(version);
            }
            default -> unitPath;
        };
    }

    private BufferedImage getUnitDecal(Player player, UnitKey unitKey) {
        if (player == null) return null;
        return ImageHelper.read(resourceHelper.getDecalFile(player.getDecalFile(unitKey.asyncID())));
    }

    private BufferedImage getSpoopyImage(UnitKey unitKey, Player player) {
        BufferedImage spoopy = null;

        // Random spoopy warsun
        int spoopyChance = CalendarHelper.isNearHalloween() ? 10 : 1000;
        if (unitKey.getUnitType() == UnitType.Warsun && RandomHelper.isOneInX(spoopyChance)) {
            String spoopypath = resourceHelper.getSpoopyFile();
            spoopy = ImageHelper.read(spoopypath);
        }

        // Ghemina special units
        if (unitKey.getUnitType() == UnitType.Lady) {
            String name = "units_ds_ghemina_lady_wht.png";
            String spoopyPath = resourceHelper.getDecalFile(name);
            spoopy = ImageHelper.read(spoopyPath);
        }
        if (unitKey.getUnitType() == UnitType.Flagship && player.ownsUnit("ghemina_flagship_lord")) {
            String name = "units_ds_ghemina_lord_wht.png";
            String spoopyPath = resourceHelper.getDecalFile(name);
            spoopy = ImageHelper.read(spoopyPath);
        }

        return spoopy;
    }

    private Integer getBulkUnitCount(UnitKey unitKey, int unitCount) {
        return Set.of(UnitType.Fighter, UnitType.Infantry).contains(unitKey.getUnitType()) ? unitCount : null;
    }

    private Map<UnitKey, Integer> sortUnits(Map<UnitKey, Integer> tempUnits) {
        Map<UnitKey, Integer> sortedUnits = new LinkedHashMap<>();

        List<UnitType> typeOrder = new ArrayList<>();
        // Token units are drawn first, always
        typeOrder.addAll(List.of(UnitType.Infantry, UnitType.Fighter));
        // Ground unit ordering
        typeOrder.addAll(List.of(UnitType.Spacedock, UnitType.Pds, UnitType.Mech));
        typeOrder.addAll(List.of(UnitType.Monument, UnitType.PlenaryOrbital)); // other misc
        // Space unit ordering
        typeOrder.addAll(List.of(UnitType.Flagship, UnitType.Dreadnought, UnitType.Carrier, UnitType.Cruiser, UnitType.Destroyer));
        typeOrder.addAll(List.of(UnitType.Warsun, UnitType.TyrantsLament, UnitType.Cavalry, UnitType.Lady));

        List<String> playerOrder = unitHolder.getUnitColorsOnHolder();
        if (game.getActivePlayer() != null && !playerOrder.isEmpty()) {
            String activePlayerColor = null;
            if ("space".equals(unitHolder.getName())) {
                activePlayerColor = game.getActivePlayer().getColorID();
            } else {
                for (Player p : game.getPlayers().values()) {
                    if (p.hasPlanet(unitHolder.getName())) activePlayerColor = p.getColorID();
                }
            }
            if (activePlayerColor != null && playerOrder.contains(activePlayerColor)) {
                while (!playerOrder.getLast().equals(activePlayerColor)) {
                    Collections.rotate(playerOrder, 1);
                }
            }
        }

        // Add all units in order
        for (UnitType type : typeOrder) {
            for (String colorID : playerOrder) {
                for (Map.Entry<UnitKey, Integer> entry : tempUnits.entrySet()) {
                    UnitKey id = entry.getKey();
                    if (id != null && id.getUnitType() == type && id.getColorID().equals(colorID)) {
                        sortedUnits.put(id, entry.getValue());
                    }
                }
            }
        }

        // Add remaining units
        for (UnitKey key : sortedUnits.keySet())
            tempUnits.remove(key);
        sortedUnits.putAll(tempUnits);
        return sortedUnits;
    }

    private Point calculateUnitPosition(
        PositioningContext posCtx,
        UnitKey unitKey,
        int count
    ) {
        Point predefinedPosition = getTokenPosition(unitKey, posCtx);

        // Handle space units (non-fighter/infantry units in space with predefined positions)
        if (ctx.isSpace && predefinedPosition != null && !posCtx.fighterOrInfantry) {
            predefinedPosition.translate(ctx.spaceUnitXOffset * count, ctx.spaceUnitYOffset * count);
            return predefinedPosition;
        }

        // Use predefined token position if available
        if (predefinedPosition != null) {
            return predefinedPosition;
        }

        // Default to spiral positioning
        Point position = calculateSpiralPosition(posCtx, radius, degree, degreeChange, rectangles);

        // add found position to 'rectangles' to prevent any future units from being placed there
        Point centerPosition = posCtx.centerPosition;
        int unitWidth = posCtx.unitImage.getWidth();
        int unitHeight = posCtx.unitImage.getHeight();

        rectangles.add(
            new Rectangle(
                centerPosition.x + position.x - (unitWidth / 2),
                centerPosition.y + position.y - (unitHeight / 2),
                unitWidth,
                unitHeight));
        return position;
    }

    private Point getTokenPosition(UnitKey unitKey, PositioningContext posCtx) {
        boolean fighterOrInfantry = Set.of(UnitType.Infantry, UnitType.Fighter).contains(unitKey.getUnitType());
        String positionKey = fighterOrInfantry ? "tkn_" + posCtx.unitId : posCtx.unitId;
        return ctx.unitTokenPosition.getPosition(positionKey);
    }

    private Point calculateSpiralPosition(
        PositioningContext posCtx,
        int radius,
        int degree,
        int degreeChange,
        List<Rectangle> rectangles
    ) {
        Point centerPosition = posCtx.centerPosition;
        int unitWidth = posCtx.unitImage.getWidth();
        int unitHeight = posCtx.unitImage.getHeight();

        int foundX = 0;
        int foundY = 0;

        boolean searchPosition = true;
        while (searchPosition) {
            int x = (int) (radius * Math.sin(degree));
            int y = (int) (radius * Math.cos(degree));
            int candidateX = centerPosition.x + x - (unitWidth / 2);
            int candidateY = centerPosition.y + y - (unitHeight / 2);
            if (rectangles.stream().noneMatch(rectangle -> rectangle.intersects(candidateX, candidateY,
                unitWidth, unitHeight))) {
                searchPosition = false;
            } else if (degree > 360) {
                searchPosition = false;
            }

            degree += degreeChange;

            if (!searchPosition) {
                foundX = candidateX;
                foundY = candidateY;
            }
        }

        rectangles.add(
            new Rectangle(foundX, foundY, unitWidth, unitHeight));

        return new Point(foundX, foundY);
    }

    private ImagePosition calculateImagePosition(PositioningContext posCtx, Point position) {
        int xOriginal = posCtx.centerPosition.x + position.x;
        int yOriginal = posCtx.centerPosition.y + position.y;

        // Add padding
        int imageX = position.x + TILE_PADDING;
        int imageY = position.y + TILE_PADDING;

        // Handle mirage positions
        if (ctx.isMirage) {
            if (posCtx.planetHolderSize() == 3 + 1) {
                imageX += Constants.MIRAGE_TRIPLE_POSITION.x;
                imageY += Constants.MIRAGE_TRIPLE_POSITION.y;
            } else {
                imageX += Constants.MIRAGE_POSITION.x;
                imageY += Constants.MIRAGE_POSITION.y;
            }
        } else if (ctx.hasMirage) {
            // Center the image
            imageX += (posCtx.unitImage.getWidth() / 2);
            imageY += (posCtx.unitImage.getHeight() / 2);

            // Apply mirage drag transformation
            imageX = Math.round(ctx.mirageDragRatio * imageX) + ctx.mirageDragX() +
                (posCtx.fighterOrInfantry() ? 60 : 0);
            imageY = Math.round(ctx.mirageDragRatio * imageY) + ctx.mirageDragY();

            // Adjust back from center
            imageX -= (posCtx.unitImage.getWidth() / 2);
            imageY -= (posCtx.unitImage.getHeight() / 2);
        }

        return new ImagePosition(xOriginal, yOriginal, imageX, imageY);
    }

    private static Point getUnitTagLocation(String unitID) {
        return switch (unitID) {
            case "ws" -> new Point(-10, 45); // War Sun
            case "fs", "lord", "lady", "tyrantslament", "cavalry" -> new Point(10, 55); // Flagship
            case "dn" -> new Point(10, 50); // Dreadnought
            case "ca" -> new Point(0, 40); // Cruiser
            case "cv" -> new Point(0, 40); // Carrier
            case "gf", "ff" -> new Point(-15, 12); // Infantry/Fighter
            case "dd" -> new Point(-10, 30); // Destroyer
            case "mf" -> new Point(-10, 20); // Mech
            case "pd" -> new Point(-10, 20); // PDS
            case "sd", "csd", "plenaryorbital" -> new Point(-10, 20); // Space Dock
            default -> new Point(0, 0);
        };
    }
}
