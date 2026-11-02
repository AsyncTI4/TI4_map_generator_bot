package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.ShapeDrawing;
import ti4.image.draw.VoltronDecal;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

final class UnitUpgradeSheet {

    private static final int SHEET_WIDTH = 256;

    private UnitUpgradeSheet() {}

    private record TwilightsFallCounts(int mechs, int flagships) {}

    static int draw(PlayerAreaContext ctx, Player player, List<String> techs, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        PaImage.draw(g2, x, y, "pa_tech_unitupgrade_outlines.png");

        boolean brokenWarSun = ButtonHelper.isLawInPlay(game, "schematics");
        Set<String> unitPainted = new HashSet<>();

        if (techs != null) {
            drawResearchedUpgrades(ctx, player, techs, unitPainted, x, y);
        }
        List<UnitModel> playerUnitModels = new ArrayList<>(player.getUnitModels());
        drawUpgradesWithoutTech(ctx, player, playerUnitModels, unitPainted, x, y);
        drawSigmaFlagship(ctx, player, unitPainted, x, y);
        if ("true".equals(game.getStoredValue("zealotsHeroPurged"))) {
            drawZealotsPurgedUpgrades(ctx, player, x, y);
        }
        if (brokenWarSun) {
            drawWarSunSchematicsCrack(ctx, player, x, y);
        }
        if (player.getUnitsByAsyncID("ws").isEmpty()) {
            playerUnitModels.add(Mapper.getUnit("nowarsun"));
        }
        drawFactionIconsAndOverlays(ctx, player, playerUnitModels, unitPainted, x, y);

        g2.setColor(Color.WHITE);
        g2.drawRect(x - 2, y - 2, 252, 152);
        return x + SHEET_WIDTH;
    }

    private static void drawResearchedUpgrades(
            PlayerAreaContext ctx, Player player, List<String> techs, Set<String> unitPainted, int x, int y) {
        Game game = ctx.game();
        boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
        List<String> zealotsTechs =
                Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
        for (String tech : techs) {
            UnitModel unit = upgradedUnit(game, player, tech);
            if (unit == null) {
                continue;
            }
            Point unitOffset = unitOffset(unit.getAsyncId());
            drawUnit(ctx, Mapper.getUnitKey(unit.getAsyncId(), player.getColor()), x + unitOffset.x, y + unitOffset.y);
            unitPainted.add(unit.getAsyncId());

            if (zealotsHeroActive && zealotsTechs.contains(tech)) {
                drawPaOverlay(
                        ctx,
                        player,
                        "pa_tech_unitsnew_zealots_" + tech + ".png",
                        x + unitOffset.x,
                        y + unitOffset.y,
                        "Could not display active zealot tech");
            }
        }
    }

    private static UnitModel upgradedUnit(Game game, Player player, String tech) {
        TechnologyModel techInformation = Mapper.getTech(tech);
        if (techInformation == null || !techInformation.isUnitUpgrade()) {
            return null;
        }
        UnitModel unit = Mapper.getUnitModelByTechUpgrade(techInformation.getAlias());
        if (unit == null) {
            BotLogger.warning(
                    new LogOrigin(player),
                    game.getName() + " " + player.getUserName() + " Could not load unit associated with tech: "
                            + techInformation.getAlias());
        }
        return unit;
    }

    private static void drawUpgradesWithoutTech(
            PlayerAreaContext ctx,
            Player player,
            List<UnitModel> playerUnitModels,
            Set<String> unitPainted,
            int x,
            int y) {
        Game game = ctx.game();
        for (UnitModel unit : playerUnitModels) {
            boolean drawUpgradeWithoutTech =
                    unit.getIsUpgrade() && unit.getRequiredTechId().isEmpty();
            drawUpgradeWithoutTech |= "fs".equals(unit.getAsyncId())
                    && player.hasUnlockedBreakthrough("nekrobt")
                    && !game.getStoredValue("valefarZ").isEmpty();
            if (drawUpgradeWithoutTech) {
                Point unitOffset = unitOffset(unit.getAsyncId());
                drawUnit(
                        ctx,
                        Mapper.getUnitKey(unit.getAsyncId(), player.getColor()),
                        x + unitOffset.x,
                        y + unitOffset.y);
                unitPainted.add(unit.getAsyncId());
            }
        }
    }

    private static void drawSigmaFlagship(PlayerAreaContext ctx, Player player, Set<String> unitPainted, int x, int y) {
        UnitModel flagship = player.getUnitByBaseType("flagship");
        if (flagship == null || !flagship.getId().startsWith("sigma_")) {
            return;
        }
        Point unitOffset = unitOffset(flagship.getAsyncId());
        drawUnit(ctx, Mapper.getUnitKey(flagship.getAsyncId(), player.getColor()), x + unitOffset.x, y + unitOffset.y);
        unitPainted.add("fs");
        String pipsPath = "pa_leaders_pips_";
        if (flagship.getId().endsWith("_1")) {
            pipsPath += "i";
        } else if (flagship.getId().endsWith("_2")) {
            pipsPath += "ii";
        } else if (flagship.getId().endsWith("_3")) {
            pipsPath += "iii";
        }
        pipsPath += "_rdy.png";
        PaImage.draw(ctx.g2(), x + unitOffset.x + 32, y + unitOffset.y + 37, pipsPath);
    }

    private static void drawZealotsPurgedUpgrades(PlayerAreaContext ctx, Player player, int x, int y) {
        for (String tech : player.getPurgedTechs()) {
            UnitModel unit = upgradedUnit(ctx.game(), player, tech);
            if (unit == null) {
                continue;
            }
            Point unitOffset = unitOffset(unit.getAsyncId());
            drawPaOverlay(
                    ctx,
                    player,
                    "pa_tech_unitsnew_zealotspurged_" + tech + ".png",
                    x + unitOffset.x,
                    y + unitOffset.y,
                    "Could not display purged zealot tech");
        }
    }

    private static void drawWarSunSchematicsCrack(PlayerAreaContext ctx, Player player, int x, int y) {
        UnitModel unit = Mapper.getUnitModelByTechUpgrade("ws");
        Point unitOffset = unitOffset(unit.getAsyncId());
        UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
        BufferedImage wsCrackImage = ImageHelper.read(ImageResources.token("agenda_publicize_weapon_schematics"
                + (player.hasWarsunTech() ? Mapper.getColor(unitKey.colorID()).blackWhiteFileSuffix() : "_blk.png")));
        ctx.g2().drawImage(wsCrackImage, x + unitOffset.x, y + unitOffset.y, null);
    }

    private static TwilightsFallCounts twilightsFallCounts(Game game, List<UnitModel> playerUnitModels) {
        int mechs = 0;
        int flagships = 0;
        if (game.isTwilightsFallMode()) {
            for (UnitModel unit : playerUnitModels) {
                switch (unit.getAsyncId()) {
                    case "mf" -> mechs++;
                    case "fs" -> flagships++;
                }
            }
        }
        return new TwilightsFallCounts(mechs, flagships);
    }

    private static void drawFactionIconsAndOverlays(
            PlayerAreaContext ctx,
            Player player,
            List<UnitModel> playerUnitModels,
            Set<String> unitPainted,
            int x,
            int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        TwilightsFallCounts tfCounts = twilightsFallCounts(game, playerUnitModels);
        int tfMechIndex = 0;
        int tfFlagIndex = 0;
        for (UnitModel unit : playerUnitModels) {
            boolean isPurged = unit.getRequiredTechId().isPresent()
                    && player.getPurgedTechs().contains(unit.getRequiredTechId().get());
            Point unitFactionOffset = factionIconOffset(unit.getAsyncId());
            Point unitOffset = unitOffset(unit.getAsyncId());
            if (unit.getFaction().isPresent()) {
                UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
                if ("fs".equals(unit.getAsyncId())) {
                    tfFlagIndex = shiftFlagshipIcon(
                            ctx, player, unit, unitPainted, unitFactionOffset, tfCounts, tfFlagIndex, x, y);
                }
                if ("mf".equals(unit.getAsyncId()) && game.isTwilightsFallMode() && tfCounts.mechs() >= 2) {
                    if (!unit.getIsUpgrade()) {
                        unitFactionOffset.translate(-12, 12);
                    } else {
                        tfMechIndex++;
                        shiftTwilightsFallMechIcon(unitFactionOffset, tfCounts.mechs(), tfMechIndex);
                    }
                }
                FactionIcons.drawFactionBordered(
                        g2,
                        unit.getFaction().get().toLowerCase(),
                        x + unitFactionOffset.x,
                        y + unitFactionOffset.y,
                        32,
                        32,
                        unitPainted.contains(unit.getAsyncId()));
                VoltronDecal.drawIfVoltron(unitKey, x + unitOffset.x, y + unitOffset.y, g2, game);
            }
            if (isPurged) {
                ShapeDrawing.redX(g2, x + unitFactionOffset.x, y + unitFactionOffset.y, 20, true);
            }
            ctx.overlays().add(unit, x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32);
        }
    }

    private static int shiftFlagshipIcon(
            PlayerAreaContext ctx,
            Player player,
            UnitModel unit,
            Set<String> unitPainted,
            Point unitFactionOffset,
            TwilightsFallCounts tfCounts,
            int tfFlagIndex,
            int x,
            int y) {
        Game game = ctx.game();
        String unitFaction = unit.getFaction().orElse("nekro").toLowerCase();
        if (player.hasUnlockedBreakthrough("nekrobt")) {
            drawValefarZFlagships(ctx, unit, unitPainted, unitFactionOffset, x, y);
        } else if (game.getStoredValue("valefarZ").contains(unitFaction)) {
            BufferedImage valefarZ =
                    ImageHelper.read(ResourceHelper.getResourceFromFolder("extra/", "marker_valefarZ.png"));
            ctx.g2().drawImage(valefarZ, x + unitFactionOffset.x - 10, y + unitFactionOffset.y + 10, null);
            unitFactionOffset.translate(-24, -24);
        } else if (game.isTwilightsFallMode() && tfCounts.flagships() >= 2) {
            if (!unit.getIsUpgrade()) {
                if (tfCounts.flagships() >= 3) unitFactionOffset.translate(0, 8);
            } else {
                if (tfCounts.flagships() >= 3) unitFactionOffset.translate(0, -8);
                tfFlagIndex++;
                shiftTwilightsFallFlagshipIcon(unitFactionOffset, tfFlagIndex);
            }
        }
        return tfFlagIndex;
    }

    private static void drawValefarZFlagships(
            PlayerAreaContext ctx, UnitModel unit, Set<String> unitPainted, Point unitFactionOffset, int x, int y) {
        Game game = ctx.game();
        List<String> flagships =
                new ArrayList<>(Arrays.asList(game.getStoredValue("valefarZ").split("\\|")));
        Point offs = new Point(unitFactionOffset.x - 24, unitFactionOffset.y - 24);
        if (flagships.size() > 1) {
            offs.translate(0, -8);
            unitFactionOffset.translate(0, 8);
        }
        for (String fsFaction : flagships) {
            FactionIcons.drawFactionBordered(
                    ctx.g2(), fsFaction, x + offs.x, y + offs.y, 32, 32, unitPainted.contains(unit.getAsyncId()));
            Player owner = game.getPlayerFromColorOrFaction(fsFaction);
            UnitModel otherFlagship = owner == null ? null : owner.getUnitByBaseType("flagship");
            if (otherFlagship != null) {
                ctx.overlays().add(otherFlagship, x + offs.x, y + offs.y, 32, 32);
            }
            offs.translate(16, 8);
        }
    }

    private static void shiftTwilightsFallFlagshipIcon(Point offset, int flagshipIndex) {
        switch (flagshipIndex) {
            case 1 -> offset.translate(-24, -24);
            case 2 -> offset.translate(-8, -16);
            case 3 -> offset.translate(8, -8);
            case 4 -> offset.translate(24, 0);
            case 5 -> offset.translate(40, 8);
        }
    }

    private static void shiftTwilightsFallMechIcon(Point offset, int mechCount, int mechIndex) {
        switch (10 * mechCount + mechIndex) {
            case 21 -> offset.translate(12, -12);
            case 31 -> offset.translate(-4, -16);
            case 32 -> offset.translate(16, 4);
            case 41 -> offset.translate(-12, -12);
            case 42 -> offset.translate(12, -12);
            case 43 -> offset.translate(12, 12);
        }
    }

    private static void drawUnit(PlayerAreaContext ctx, UnitKey unitKey, int x, int y) {
        try {
            BufferedImage img = ImageHelper.read(ImageResources.unit(unitKey));
            ctx.g2().drawImage(img, x, y, null);
        } catch (Exception e) {
            BotLogger.error("Could not display UU", e);
        }
    }

    private static void drawPaOverlay(
            PlayerAreaContext ctx, Player player, String fileName, int x, int y, String failure) {
        try {
            BufferedImage img = ImageHelper.read(ImageResources.playerArea(fileName));
            ctx.g2().drawImage(img, x, y, null);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), failure, e);
        }
    }

    private static Point unitOffset(String asyncId) {
        return switch (AliasHandler.resolveUnit(asyncId)) {
            case "gf" -> new Point(3, 2);
            case "fs" -> new Point(151, 67);
            case "ff" -> new Point(7, 59);
            case "dn" -> new Point(93, 72);
            case "dd" -> new Point(52, 99);
            case "cv" -> new Point(82, 11);
            case "ca" -> new Point(126, 1);
            case "ws" -> new Point(191, 4);
            case "sd", "csd" -> new Point(46, 49);
            case "pd", "pds" -> new Point(47, 2);
            case "mf" -> new Point(3, 102);
            default -> new Point(0, 0);
        };
    }

    private static Point factionIconOffset(String asyncId) {
        return switch (AliasHandler.resolveUnit(asyncId)) {
            case "gf" -> new Point(3, 17);
            case "fs" -> new Point(185, 101);
            case "ff" -> new Point(7, 72);
            case "dn" -> new Point(116, 99);
            case "dd" -> new Point(62, 106);
            case "cv" -> new Point(105, 38);
            case "ca" -> new Point(149, 24);
            case "ws" -> new Point(204, 21);
            case "sd", "csd" -> new Point(52, 65);
            case "pd", "pds" -> new Point(51, 15);
            case "mf" -> new Point(9, 110);
            default -> new Point(0, 0);
        };
    }
}
