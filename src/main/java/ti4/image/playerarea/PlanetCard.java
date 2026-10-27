package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel.PlanetType;

final class PlanetCard {

    private static final int CARD_STEP = 56;
    private static final int DETAIL_X = 26;
    private static final Map<String, String> DOUBLE_SKIP_ABBREVIATIONS = Map.ofEntries(
            Map.entry("propulsionpropulsion", "bb"),
            Map.entry("warfarewarfare", "rr"),
            Map.entry("cyberneticcybernetic", "yy"),
            Map.entry("bioticbiotic", "gg"),
            Map.entry("warfarepropulsion", "br"),
            Map.entry("propulsionwarfare", "br"),
            Map.entry("cyberneticpropulsion", "by"),
            Map.entry("propulsioncybernetic", "by"),
            Map.entry("cyberneticwarfare", "ry"),
            Map.entry("warfarecybernetic", "ry"),
            Map.entry("bioticwarfare", "gr"),
            Map.entry("warfarebiotic", "gr"),
            Map.entry("bioticcybernetic", "gy"),
            Map.entry("cyberneticbiotic", "gy"),
            Map.entry("bioticpropulsion", "bg"),
            Map.entry("propulsionbiotic", "bg"));

    private PlanetCard() {}

    private record Coexistence(boolean exhausted, boolean coexist, String faction) {}

    static int draw(PlayerAreaContext ctx, Player player, String planetName, int x, int y, int deltaX) {
        Game game = ctx.game();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();
        try {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null) return deltaX;
            PlanetModel planetModel = planet.getPlanetModel();
            if (planetModel == null) return deltaX;

            int cardX = x + deltaX;
            Coexistence coexistence = coexistence(player, planetName);
            boolean isExhausted = coexistence.exhausted();
            String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
            Graphics2D g2 = ctx.g2();
            g2.setColor(isExhausted ? Color.GRAY : Color.WHITE);
            g2.drawRect(cardX - 2, y - 2, 52, 152);

            drawTraitIcon(ctx, player, planet, planetName, coexistence, cardX, y);
            drawAttachments(ctx, planet, planetModel, exhaustedPlanetsAbilities.contains(planetName), cardX, y);
            drawTechSkip(ctx, planet, statusOfPlanet, cardX, y);
            drawResourcesAndInfluence(ctx, planet, statusOfPlanet, cardX, y);

            g2.setColor(isExhausted ? Color.GRAY : Color.WHITE);
            if (planetModel.getShrinkNamePNAttach()) {
                VerticalText.draw(g2, planetModel.getShortName().toUpperCase(), cardX + 9, y + 144, Fonts.ti(16));
            } else {
                VerticalText.draw(g2, planetModel.getShortName().toUpperCase(), cardX + 7, y + 144, Fonts.ti(18));
            }
            drawCommodities(ctx, player, planet, cardX, y);
            return deltaX + CARD_STEP;
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "could not print out planet: " + planetName.toLowerCase(), e);
        }
        return deltaX;
    }

    private static Coexistence coexistence(Player player, String planetName) {
        boolean coexist = false;
        String coexistFaction = "";
        boolean isExhausted = player.getExhaustedPlanets().contains(planetName);
        for (Player other : player.getGame().getRealPlayers()) {
            if (other != player && other.getPlanets().contains(planetName)) {
                isExhausted = true;
                coexistFaction = other.getFaction();
                coexist = true;
                if (coexistFaction.contains("keleres")) {
                    coexistFaction = "keleres";
                }
            }
        }
        if (player.getGame().getPlayersPlanetsThatOthersAreCoexistingOn(player).contains(planetName)) {
            coexist = true;
        }
        return new Coexistence(isExhausted, coexist, coexistFaction);
    }

    private static String traitIcon(Game game, Player player, Planet planet, String planetName) {
        String planetDisplayIcon = planet.getOriginalPlanetType();
        List<PlanetType> originalPlanetTypes = Mapper.getPlanet(planetName).getPlanetTypes();
        if (originalPlanetTypes == null || originalPlanetTypes.isEmpty()) {
            planetDisplayIcon = "none";
        }
        if (originalPlanetTypes != null && originalPlanetTypes.contains(PlanetType.FACTION)) {
            planetDisplayIcon = Mapper.getPlanet(planetName).getFactionHomeworld();
            if (planetDisplayIcon == null) {
                planetDisplayIcon = player.getFaction();
            }
            if (game.isLiberationC4Mode() && "ghost".equals(planetDisplayIcon)) {
                planetDisplayIcon = "redcreuss";
            }
        }
        Set<String> planetTypes = planet.getPlanetTypes();
        if (!planetTypes.isEmpty() && planetTypes.size() > 1) {
            planetDisplayIcon = "combo_";
            if (planetTypes.contains("cultural")) planetDisplayIcon += "C";
            if (planetTypes.contains("hazardous")) planetDisplayIcon += "H";
            if (planetTypes.contains("industrial")) planetDisplayIcon += "I";
        }
        return planetDisplayIcon;
    }

    private static void drawTraitIcon(
            PlayerAreaContext ctx,
            Player player,
            Planet planet,
            String planetName,
            Coexistence coexistence,
            int x,
            int y) {
        Graphics2D g2 = ctx.g2();
        String planetDisplayIcon = traitIcon(ctx.game(), player, planet, planetName);
        if (coexistence.coexist() && coexistence.faction().isEmpty()) {
            FactionIcons.drawFaction(g2, "coexist", x - 2 + 20, y - 2 + 40, 30, 30);
        }
        if (planetDisplayIcon == null || planetDisplayIcon.isEmpty()) {
            return;
        }
        if ("keleres".equals(player.getFaction())
                && ("mentak".equals(planetDisplayIcon)
                        || "xxcha".equals(planetDisplayIcon)
                        || "argent".equals(planetDisplayIcon))) {
            planetDisplayIcon = "keleres";
        }
        if (Mapper.isValidFaction(planetDisplayIcon) || "redcreuss".equals(planetDisplayIcon)) {
            FactionIcons.drawFaction(g2, planetDisplayIcon, x - 2, y - 2, 52, 52);
        } else if ("triad".equals(planetName)) {
            PaImage.draw(g2, x + 3, y - 2, "pa_relics_icon.png");
        } else {
            drawDetail(g2, x + 1, y + 2, "pc_attribute_" + planetDisplayIcon + ".png");
            if (coexistence.coexist() && Mapper.isValidFaction(coexistence.faction())) {
                FactionIcons.drawFaction(g2, coexistence.faction(), x - 2 + 20, y - 2 + 40, 30, 30);
            }
        }
    }

    private static void drawAttachments(
            PlayerAreaContext ctx, Planet planet, PlanetModel planetModel, boolean abilityExhausted, int x, int y) {
        Graphics2D g2 = ctx.g2();
        if (planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
            String tokenPath = ImageResources.token(Constants.GLEDGE_CORE_PNG);
            BufferedImage image = ImageHelper.readScaled(tokenPath, 0.25f);
            g2.drawImage(image, x + 15, y + 112, null);
        }
        if (planet.hasAttachment()) {
            String planetChevrons = "pc_upgrade.png";
            if (planet.getTokenList().contains("attachment_tombofemphidia.png")) {
                planetChevrons = "pc_upgrade_tomb.png";
                ctx.overlays().add(Mapper.getExplore("toe"), x + DETAIL_X, y + 40, 20, 20);
            }
            drawDetail(g2, x + DETAIL_X, y + 40, planetChevrons);
        }
        if (planet.isLegendary()) {
            ctx.overlays().add(planetModel, x + DETAIL_X, y + 60, 20, 20);
            drawDetail(g2, x + DETAIL_X, y + 60, "pc_legendary" + (abilityExhausted ? "_exh" : "_rdy") + ".png");
        }
    }

    private static void drawTechSkip(PlayerAreaContext ctx, Planet planet, String statusOfPlanet, int x, int y) {
        Graphics2D g2 = ctx.g2();
        boolean hasBentorEncryptionKey =
                planet.getTokenList().stream().anyMatch(token -> token.contains("encryptionkey"));
        if (hasBentorEncryptionKey) {
            ctx.overlays().add("Bentor Encryption Key", null, x + DETAIL_X, y + 82, 20, 20);
            drawDetail(g2, x + DETAIL_X, y + 82, "pc_tech_bentor_encryptionkey.png");
            return;
        }
        String originalTechSpeciality = planet.getOriginalTechSpeciality();
        if (StringUtils.isNotBlank(originalTechSpeciality)) {
            String planetTechSkip = "pc_tech_" + originalTechSpeciality + statusOfPlanet + ".png";
            if (planet.getTechSpecialities().size() > 1) {
                StringBuilder techType = new StringBuilder();
                for (String type : planet.getTechSpecialities()) {
                    techType.append(type.toLowerCase());
                }
                String abbrev = DOUBLE_SKIP_ABBREVIATIONS.getOrDefault(techType.toString(), "");
                if (!abbrev.isEmpty()) {
                    planetTechSkip = "pc_tech_" + abbrev + statusOfPlanet + ".png";
                }
            }
            drawDetail(g2, x + DETAIL_X, y + 82, planetTechSkip);
            return;
        }
        for (String techSpec : planet.getTechSpeciality()) {
            if (techSpec.isEmpty()) {
                continue;
            }
            drawDetail(g2, x + DETAIL_X, y + 82, "pc_tech_" + techSpec + statusOfPlanet + ".png");
        }
    }

    private static void drawResourcesAndInfluence(
            PlayerAreaContext ctx, Planet planet, String statusOfPlanet, int x, int y) {
        Graphics2D g2 = ctx.g2();
        boolean gardenWorld = planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG);
        String resFileName = "pc_res" + statusOfPlanet + ".png";
        String infFileName = "pc_inf" + statusOfPlanet + ".png";
        int resources = planet.getResources();
        int influence = planet.getInfluence();
        if (gardenWorld) {
            resFileName = "pc_res_khrask" + statusOfPlanet + ".png";
            ctx.overlays().add("Garden World", null, x, y, 20, 20);
        }
        drawDetail(g2, x + DETAIL_X, y + 103, resFileName);
        drawDetail(g2, x + DETAIL_X, y + 125, infFileName);

        g2.setFont(Fonts.ti(16));
        int offset = 11 - g2.getFontMetrics().stringWidth("" + resources) / 2;
        if (gardenWorld) {
            g2.setColor(Color.BLACK);
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    g2.drawString("" + resources, x + DETAIL_X + offset + i, y + 119 + j);
                }
            }
        }
        g2.setColor(Color.WHITE);
        g2.drawString("" + resources, x + DETAIL_X + offset, y + 119);
        offset = 10 - g2.getFontMetrics().stringWidth("" + influence) / 2;
        g2.drawString("" + influence, x + 27 + offset, y + 141);
    }

    private static void drawCommodities(PlayerAreaContext ctx, Player player, Planet planet, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        if (!game.getStoredValue("CommsOnPlanet" + planet.getName()).isEmpty()) {
            int comms = Integer.parseInt(game.getStoredValue("CommsOnPlanet" + planet.getName()));
            g2.setColor(Color.GRAY);
            g2.setFont(Fonts.ti(26));
            int offset = 20 - g2.getFontMetrics().stringWidth("" + comms) / 2;
            if (game.isFacilitiesMode()) {
                g2.setColor(ColorUtil.TradeGoodColor);
            }
            g2.drawString(Integer.toString(comms), x + 15 + offset, y + 67);
        }
        if (player.getHarvestCounter() > 0 && "uikos".equalsIgnoreCase(planet.getName())) {
            int harvest = player.getHarvestCounter();
            g2.setFont(Fonts.ti(26));
            int offset = 20 - g2.getFontMetrics().stringWidth("" + harvest) / 2;
            g2.setColor(Color.GRAY);
            g2.drawString(Integer.toString(harvest), x + offset - 10, y + 69);
        }
    }

    private static void drawDetail(Graphics2D g2, int x, int y, String resourceName) {
        String resourcePath = ImageResources.planetCard(resourceName);
        g2.drawImage(ImageHelper.read(resourcePath), x, y, null);
    }
}
