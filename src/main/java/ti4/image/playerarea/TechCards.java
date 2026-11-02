package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.tyris.TyrisBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.service.VeiledHeartService.VeiledCardType;

final class TechCards {

    private static final int CARD_STEP = 48;
    private static final Comparator<String> BY_REQUIREMENTS =
            (tech1, tech2) -> TechnologyModel.sortTechsByRequirements(Mapper.getTech(tech1), Mapper.getTech(tech2));

    private TechCards() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        Game game = ctx.game();
        Map<String, List<String>> techsByColour = techsByColour(game, player);

        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_2);

        int deltaX = 0;
        List<String> exhaustedTechs = player.getExhaustedTechs();
        if (game.isTwilightsFallMode()) {
            deltaX = drawGroup(ctx, player, x, y, techsByColour.get("generictf"), exhaustedTechs, deltaX);
        }
        deltaX = drawGroup(ctx, player, x, y, techsByColour.get(Constants.PROPULSION), exhaustedTechs, deltaX);
        deltaX = drawGroup(ctx, player, x, y, techsByColour.get(Constants.WARFARE), exhaustedTechs, deltaX);
        deltaX = drawGroup(ctx, player, x, y, techsByColour.get(Constants.CYBERNETIC), exhaustedTechs, deltaX);
        deltaX = drawGroup(ctx, player, x, y, techsByColour.get(Constants.BIOTIC), exhaustedTechs, deltaX);

        if (game.isVeiledHeartMode()) {
            deltaX = VeiledCards.draw(g2, x, y, VeiledCardType.ABILITY, deltaX, player);
        }
        deltaX = UnitUpgradeSheet.draw(ctx, player, techsByColour.get(Constants.UNIT_UPGRADE), x + deltaX, y) - x;
        if (game.isVeiledHeartMode()) {
            deltaX = VeiledCards.draw(g2, x, y, VeiledCardType.UNIT, deltaX, player);
        }

        List<String> purgedTechs = player.getPurgedTechs();
        if (!purgedTechs.isEmpty()) {
            purgedTechs = new ArrayList<>(purgedTechs);
            purgedTechs.removeIf(tech -> Mapper.getTech(tech) == null);
            purgedTechs.sort(BY_REQUIREMENTS);
            deltaX = drawGroup(ctx, player, x, y, purgedTechs, Collections.emptyList(), deltaX);
        }
        return x + deltaX;
    }

    private static Map<String, List<String>> techsByColour(Game game, Player player) {
        List<String> techs = new ArrayList<>(player.getTechs());
        if (player.hasUnlockedBreakthrough("tyrisbt")) {
            techs.addAll(TyrisBreakthroughHandler.getTyrisBTTechs(player, game));
        }
        Map<String, List<String>> techsByColour = new HashMap<>();
        for (String tech : techs) {
            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel == null) {
                continue;
            }
            String techType = techModel.getFirstType().toString();
            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techType = game.getStoredValue("colorChange" + tech);
            }
            techsByColour.computeIfAbsent(techType, type -> new ArrayList<>()).add(tech);
        }
        techsByColour.values().forEach(list -> list.sort(BY_REQUIREMENTS));
        return techsByColour;
    }

    private static int drawGroup(
            PlayerAreaContext ctx,
            Player player,
            int x,
            int y,
            List<String> techs,
            List<String> exhaustedTechs,
            int deltaX) {
        if (techs == null) {
            return deltaX;
        }
        Game game = ctx.game();
        boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
        List<String> zealotsTechs =
                Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
        for (String tech : techs) {
            if (player.getSingularityTechs().contains(tech)) {
                continue;
            }
            boolean zealotsHero = zealotsHeroActive && zealotsTechs.contains(tech);
            drawTech(ctx, player, tech, exhaustedTechs.contains(tech), zealotsHero, x + deltaX, y);
            deltaX += CARD_STEP;
        }
        return drawSingularities(ctx, player, techs, x, y, deltaX);
    }

    private static void drawTech(
            PlayerAreaContext ctx, Player player, String tech, boolean isExhausted, boolean zealotsHero, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        boolean isPurged = player.getPurgedTechs().contains(tech);
        String techStatus = isExhausted ? "_exh.png" : "_rdy.png";
        TechnologyModel techModel = Mapper.getTech(tech);

        String techIcon = techModel.getImageFileModifier();
        if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
            techIcon = game.getStoredValue("colorChange" + tech);
        }
        if (!techIcon.isEmpty()) {
            PaImage.draw(g2, x, y, "pa_tech_techicons_" + techIcon + techStatus);
        }
        if (zealotsHero) {
            PaImage.draw(g2, x, y, "pa_tech_techicons_zealots.png");
        }
        if (techModel.getSource() == ComponentSource.absol) {
            PaImage.draw(g2, x, y, "pa_source_absol" + (isExhausted ? "_exh" : "") + ".png");
        }

        if (techModel.getFaction().isPresent()) {
            String faction = techModel.getFaction().get();
            String icon = game.isLiberationC4Mode() && "ghost".equals(faction) ? "redcreuss" : faction;
            FactionIcons.drawFaction(g2, icon, x - 1, y + 108, 42, 42);
        } else {
            TechInitials.draw(g2, techModel.getInitials(), initialsColour(techModel, isExhausted || isPurged), x, y);
        }

        g2.setColor(isExhausted ? Color.GRAY : Color.WHITE);
        if (isPurged) g2.setColor(Color.RED);
        drawName(g2, techModel, x, y);
        if ("dslaner".equalsIgnoreCase(tech) || "tf-dslaner".equalsIgnoreCase(tech)) {
            VerticalText.draw(g2, "" + player.getAtsCount(), x + 15, y + 140, Fonts.ti(16));
        }
        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, techModel);
    }

    private static Color initialsColour(TechnologyModel techModel, boolean greyedOut) {
        if (greyedOut) {
            return Color.GRAY;
        }
        if (techModel.getTypes().size() > 1) {
            return Color.WHITE;
        }
        return switch (techModel.getFirstType()) {
            case PROPULSION -> ColorUtil.PropulsionTech;
            case BIOTIC -> ColorUtil.BioticTech;
            case CYBERNETIC -> ColorUtil.CyberneticTech;
            case WARFARE -> ColorUtil.WarfareTech;
            default -> Color.WHITE;
        };
    }

    static void drawName(Graphics2D g2, TechnologyModel techModel, int x, int y) {
        if (techModel.getShrinkName()) {
            g2.setFont(Fonts.ti(16));
            VerticalText.drawOneOrTwoLines(g2, techModel.getShortName(), x + 9, y + 116, 116);
        } else {
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, techModel.getShortName(), x + 7, y + 116, 116);
        }
    }

    private static int drawSingularities(
            PlayerAreaContext ctx, Player player, List<String> techs, int x, int y, int deltaX) {
        List<String> singularities = player.getSingularityTechs();
        if (singularities.isEmpty()) {
            return deltaX;
        }
        Graphics2D g2 = ctx.g2();
        String initials = singularityInitials(player, singularities.size());
        List<String> exhaustedTechs = player.getExhaustedTechs();
        for (int t = 0; t < singularities.size(); t++) {
            String tech = singularities.get(t);
            if (!techs.contains(tech)) {
                continue;
            }
            boolean isExhausted = exhaustedTechs.contains(tech)
                    || exhaustedTechs.contains(
                            "tf-singularity" + initials.toLowerCase().charAt(t));
            int cardX = x + deltaX;
            PaImage.draw(g2, cardX, y, "pa_tech_techicons_generictf_" + (isExhausted ? "exh" : "rdy") + ".png");
            TechnologyModel techModel = Mapper.getTech(tech);
            TechInitials.drawLarge(g2, initials.substring(t, t + 1), isExhausted ? Color.GRAY : Color.WHITE, cardX, y);
            drawName(g2, techModel, cardX, y);
            CardRect.draw(ctx, cardX - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, techModel);
            deltaX += CARD_STEP;
        }
        return deltaX;
    }

    private static String singularityInitials(Player player, int singularityCount) {
        String initials = (player.hasTech("tf-singularityx") ? "X" : "")
                + (player.hasTech("tf-singularityy") ? "Y" : "")
                + (player.hasTech("tf-singularityz") ? "Z" : "");
        if (singularityCount > initials.length()) {
            initials += "Q".repeat(singularityCount - initials.length());
        }
        return initials;
    }
}
