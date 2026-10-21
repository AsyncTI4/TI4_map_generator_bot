package ti4.image.tile;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.UnitPlacement.ImagePosition;
import ti4.image.tile.UnitPlacement.UnitShape;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.fow.FogViewer;

final class UnitHolderRenderer {

    private static final Map<String, String> JAIL_FACTIONS_BY_TILE = Map.of(
            "s11", "cabal",
            "s12", "nekro",
            "s13", "yssaril");
    private static final float BADGE_SCALE = 0.8f;
    private static final float LARGE_STACK_SCALE = 1.2f;
    private static final int GALVANIZED_MECH_BADGE_SHIFT_X = 10;

    private final Game game;
    private final Tile tile;
    private final DisplayType displayType;
    private final Graphics2D g;
    private final List<Rectangle> occupied;
    private final int degree;
    private final int degreeChange;
    private final UnitHolder unitHolder;
    private final int radius;

    private final FogViewer fogViewer;
    private final RenderWarnings warnings;

    @Nullable
    private final Player viewer;

    UnitHolderRenderer(
            Game game,
            @Nullable DisplayType displayType,
            Tile tile,
            Graphics2D g,
            List<Rectangle> occupied,
            int degree,
            int degreeChange,
            UnitHolder unitHolder,
            int radius,
            FogViewer fogViewer,
            RenderWarnings warnings) {
        this.game = game;
        this.tile = tile;
        this.displayType = displayType;
        this.g = g;
        this.occupied = occupied;
        this.degree = degree;
        this.degreeChange = degreeChange;
        this.unitHolder = unitHolder;
        this.radius = radius;
        this.fogViewer = fogViewer;
        this.warnings = warnings;
        this.viewer = fogViewer.player();
    }

    void render() {
        boolean isSpace = Constants.SPACE.equals(unitHolder.getName());
        boolean containsDMZ = unitHolder.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
        if (isSpace && displayType == DisplayType.shipless) return;

        List<UnitKey> unitOrder = UnitOrder.sorted(game, unitHolder);
        UnitPlacement placement = new UnitPlacement(game, tile, unitHolder, occupied, radius, degree, degreeChange);
        String jailFaction = JAIL_FACTIONS_BY_TILE.get(tile.getTileID());
        boolean isJail = jailFaction != null;
        boolean showJail = viewer == null || (isJail && fogViewer.canSeeStatsOfFaction(jailFaction));

        BufferedImage dmgImage = ImageHelper.readScaled(Helper.getDamagePath(), BADGE_SCALE);
        BufferedImage galvTag = ImageHelper.readScaled(Helper.getGalvanizeTagPath(), BADGE_SCALE);
        BufferedImage galvSingle = ImageHelper.readScaled(Helper.getGalvanizeTokenPath(), BADGE_SCALE);

        Map<UnitType, Integer> unitTypeCounts = new HashMap<>();
        for (UnitKey unitKey : unitOrder) {
            if (unitKey == null || !Mapper.isValidColor(unitKey.getColor())) continue;
            if (isHiddenJailUnit(isJail, showJail, unitKey)) continue;

            Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
            if (player == null) {
                warnings.warnOnce(
                        "missing-unit-owner",
                        new LogOrigin(game),
                        "Could not find owner for " + unitKey + " in tile " + tile.getRepresentation()
                                + " while rendering.");
                continue;
            }
            if (displayType == DisplayType.unlocked && isHiddenWhenUnlocked(unitKey, player)) continue;

            int unitCount = unitHolder.getUnitCount(unitKey);
            Integer bulkUnitCount = isBulk(unitKey) ? unitCount : null;
            String unitPath = ImageResources.unitPath(unitKey, game);
            unitTypeCounts.putIfAbsent(unitKey.unitType(), 0);

            float scale = (bulkUnitCount != null && bulkUnitCount > 9) ? LARGE_STACK_SCALE : 1.0f;
            BufferedImage unitImage;
            try {
                unitImage = scale == 1.0f ? ImageHelper.read(unitPath) : ImageHelper.readScaled(unitPath, scale);
            } catch (Exception e) {
                BotLogger.error("Could not parse unit file for: " + unitKey + " in game " + game.getName(), e);
                continue;
            }
            if (unitImage == null) continue;

            BufferedImage decal = UnitImages.factionDecal(player, unitKey);
            BufferedImage overlay = UnitImages.overlay(unitKey, player);
            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            if (unitModel == null) {
                warnings.warnOnce(
                        "missing-unit-model",
                        new LogOrigin(player),
                        "No unit model found for async ID " + unitKey.asyncID() + " of " + player.getFaction() + " ("
                                + player.getColor() + ") in tile " + tile.getRepresentation() + " while rendering.");
                continue;
            }

            UnitShape shape = new UnitShape(
                    TokenPlanetPlacement.holderCenter(unitHolder, tile), unitKey.asyncID(), unitImage, isBulk(unitKey));

            List<Integer> unitStates = unitHolder.getUnitStates(unitKey);
            List<UnitState> states = UnitState.unitRenderOrder().stream()
                    .flatMap(st -> Collections.nCopies(unitStates.get(st.ordinal()), st).stream())
                    .toList();
            int unitRenderCt = 0;
            UnitState prevState = null;
            Point position = null;
            ImagePosition imagePos = null;
            for (UnitState stateToRender : states) {
                if (prevState != null && !prevState.isGalvanized() && stateToRender.isGalvanized()) {
                    unitRenderCt++;
                }
                prevState = stateToRender;

                position =
                        placement.unitPosition(shape, unitKey, unitRenderCt + unitTypeCounts.get(unitKey.unitType()));
                imagePos = placement.imagePosition(shape, position);
                int imageX = imagePos.x();
                int imageY = imagePos.y();

                UnitDecals.drawDimensionalTear(g, unitKey, player, unitImage, imageX, imageY);
                if (UnitDecals.isMisplaced(containsDMZ, isSpace, unitModel, player, unitKey)) {
                    UnitDecals.drawMisplacedMarker(g, unitKey, bulkUnitCount != null, scale, imageX, imageY);
                }
                if (!"caballed".equals(player.getDecalSet()) || decal == null || shape.fighterOrInfantry()) {
                    g.drawImage(unitImage, imageX, imageY, null);
                }
                if (!shape.fighterOrInfantry()) {
                    g.drawImage(decal, imageX, imageY, null);
                }
                UnitDecals.drawOverlay(g, tile, unitKey, player, overlay, imageX, imageY);
                UnitDecals.drawLawDecals(g, game, unitKey, imageX, imageY);
                UnitBadges.drawTag(g, game, unitKey, player, imagePos, unitRenderCt);
                if (bulkUnitCount != null) {
                    UnitBadges.drawBulkCount(g, unitKey, bulkUnitCount, imagePos);
                }
                if (stateToRender.isDamaged() && dmgImage != null) {
                    UnitBadges.drawDamage(g, placement, position, imagePos, unitImage, dmgImage, unitKey.unitType());
                }
                if (unitKey.unitType() == UnitType.Mech && stateToRender.isGalvanized() && galvSingle != null) {
                    Point galvPos = position;
                    galvPos.translate(GALVANIZED_MECH_BADGE_SHIFT_X, 0);
                    UnitBadges.drawDamage(g, placement, galvPos, imagePos, unitImage, galvSingle, unitKey.unitType());
                }

                unitRenderCt++;
                if (bulkUnitCount != null && bulkUnitCount > 0) break;
            }

            int galvAmt = unitHolder.getGalvanizedUnitCount(unitKey);
            if (galvAmt > 0 && unitKey.unitType() != UnitType.Mech && position != null) {
                UnitBadges.drawGalvanizeTag(
                        g, tile, unitHolder, position, imagePos, unitImage, galvTag, unitKey.unitType(), galvAmt);
            }
            unitTypeCounts.merge(unitKey.unitType(), unitRenderCt, Integer::sum);
        }
    }

    private static boolean isBulk(UnitKey unitKey) {
        return unitKey.unitType() == UnitType.Fighter || unitKey.unitType() == UnitType.Infantry;
    }

    private boolean isHiddenJailUnit(boolean isJail, boolean showJail, UnitKey unitKey) {
        if (!isJail || viewer == null) {
            return false;
        }
        String colorID = Mapper.getColorID(viewer.getColor());
        return !showJail && !unitKey.colorID().equals(colorID);
    }

    private boolean isHiddenWhenUnlocked(UnitKey unitKey, Player player) {
        Set<String> tileCCs = tile.getSpaceUnitHolder().getCcList();
        List<String> tileCCsColorIDs = tileCCs.stream()
                .map(cc -> cc.replace(".png", "").replace("command_", ""))
                .toList();
        if (tileCCsColorIDs.contains(unitKey.colorID())) return true;
        return player.isPassed() || player.isNeutral();
    }
}
