package ti4.generator;

import org.jetbrains.annotations.NotNull;
import ti4.ResourceHelper;
import ti4.helpers.*;
import ti4.map.Map;
import ti4.map.*;

import javax.annotation.CheckForNull;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateMap {
    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;
    private int heightStorage;
    private int heightStats;
    private int heightForGameInfo;
    private int heightForGameInfoStorage;
    private int scoreTokenWidth;
    private int extraWidth = 200;
    private static Point tilePositionPoint = new Point(230, 295);
    private static Point numberPositionPoint = new Point(40, 27);
    private static HashMap<Player, Integer> userVPs = new HashMap<>();

    private final int width6 = 2000;
    private final int heght6 = 2100;
    private final int width8 = 2500;
    private final int heght8 = 3350;

    private static GenerateMap instance;

    private GenerateMap() {
        try {
            String controlID = Mapper.getControlID("red");
            BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), 0.45f);
            scoreTokenWidth = bufferedImage.getWidth();
        } catch (IOException e) {
            LoggerHandler.logError("Could read file data for setup file", e);
        }
        init(null);
        resetImage();
    }

    private void init(Map map) {
        int mapWidth = width6;
        int mapHeight = heght6;
        if (map != null && map.getPlayerCountForMap() == 8) {
            mapWidth = width8;
            mapHeight = heght8;
        }
        width = mapWidth + (extraWidth * 2);
        heightForGameInfo = mapHeight;
        heightForGameInfoStorage = heightForGameInfo;
        height = heightForGameInfo + mapHeight / 2 + 2200;
        heightStats = mapHeight / 2 + 2200;
        heightStorage = height;
    }

    private void resetImage() {
        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
    }

    public static GenerateMap getInstance() {
        if (instance == null) {
            instance = new GenerateMap();
        }
        return instance;
    }

    public File saveImage(Map map) {
        if (map.getDisplayTypeForced() != null) {
            return saveImage(map, map.getDisplayTypeForced());
        }
        return saveImage(map, DisplayType.all);
    }

    public File saveImage(Map map, @CheckForNull DisplayType displayType) {
        init(map);
        if (map.getDisplayTypeForced() != null) {
            displayType = map.getDisplayTypeForced();
        } else if (displayType == null) {
            displayType = map.getDisplayTypeForced();
            if (displayType == null) {
                displayType = DisplayType.all;
            }
        }
        if (displayType == DisplayType.stats) {
            heightForGameInfo = 40;
            height = heightStats;
        } else if (displayType == DisplayType.map) {
            heightForGameInfo = heightForGameInfoStorage;
            height = heightForGameInfoStorage + 300;
        } else {
            heightForGameInfo = heightForGameInfoStorage;
            height = heightStorage;
        }
        resetImage();
        File file = Storage.getMapImageStorage("temp.png");
        try {
            if (displayType == DisplayType.all || displayType == DisplayType.map) {
                HashMap<String, Tile> tileMap = new HashMap<>(map.getTileMap());
                String setup = tileMap.keySet().stream()
                        .filter(key -> key.startsWith("setup"))
                        .findFirst()
                        .orElse(null);
                if (setup != null) {
                    addTile(tileMap.get(setup), map, false);
                    tileMap.remove(setup);
                }
                tileMap.keySet().stream()
                        .sorted()
                        .forEach(key -> addTile(tileMap.get(key), map, true));

                tileMap.keySet().stream()
                        .sorted()
                        .forEach(key -> addTile(tileMap.get(key), map, false));
            }
            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            String timeStamp = getTimeStamp();
            graphics.drawString(map.getName() + " " + timeStamp, 0, 34);

            gameInfo(map, displayType);

            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("png").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(file));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(0.01f);
            }

            imageWriter.write(null, new IIOImage(mainImage, null, null), defaultWriteParam);
        } catch (IOException e) {
            LoggerHandler.log("Could not save generated map");
        }

        String timeStamp = getTimeStamp();
        String absolutePath = file.getParent() + "/" + map.getName() + "_" + timeStamp + ".jpg";
        try (FileInputStream fileInputStream = new FileInputStream(file);
             FileOutputStream fileOutputStream = new FileOutputStream(absolutePath)) {

            final BufferedImage image = ImageIO.read(fileInputStream);
            fileInputStream.close();

            final BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(image, 0, 0, Color.black, null);

            final boolean canWrite = ImageIO.write(convertedImage, "jpg", fileOutputStream);

            if (!canWrite) {
                throw new IllegalStateException("Failed to write image.");
            }
        } catch (IOException e) {
            LoggerHandler.log("Could not save jpg file", e);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        File jpgFile = new File(absolutePath);
        MapFileDeleter.addFileToDelete(jpgFile);
        return jpgFile;
    }

    @NotNull
    public static String getTimeStamp() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd - HH.mm.ss");
        return ZonedDateTime.now(ZoneOffset.UTC).format(fmt);

    }

    @CheckForNull
    private String getFactionPath(String factionID) {
        if (factionID.equals("null")) {
            return null;
        }
        String factionFileName = Mapper.getFactionFileName(factionID);
        String factionFile = ResourceHelper.getInstance().getFactionFile(factionFileName);
        if (factionFile == null) {
            LoggerHandler.log("Could not find faction: " + factionID);
        }
        return factionFile;
    }

    private String getGeneralPath(String tokenID) {
        String fileName = Mapper.getGeneralFileName(tokenID);
        String filePath = ResourceHelper.getInstance().getGeneralFile(fileName);
        if (filePath == null) {
            LoggerHandler.log("Could not find general token: " + tokenID);
        }
        return filePath;
    }

    private void gameInfo(Map map, DisplayType displayType) throws IOException {
        int widthOfLine = width - 50;
        int y = heightForGameInfo + 60;
        int x = 10;
        HashMap<String, Player> players = map.getPlayers();
        float percent = 0.15f;
        int deltaY = 35;
        int yDelta = 0;

        graphics.setFont(Storage.getFont50());
        graphics.setColor(Color.WHITE);
        graphics.drawString(map.getCustomName(), 0, y);
        y = strategyCards(map, y);
        y = scoreTrack(map, y + 20);
        y = objectives(map, y);
        if (displayType != DisplayType.stats) {
            playerInfo(map);
        }

        if (displayType == DisplayType.all || displayType == DisplayType.stats) {
            graphics.setFont(Storage.getFont32());
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(new BasicStroke(5));
            int realX = x;
            for (Player player : players.values()) {
                int baseY = y;
                x = realX;
                y += 34;
                graphics.setFont(Storage.getFont32());
                Color color = getColor(player.getColor());
                graphics.setColor(Color.WHITE);
                String userName = player.getUserName() + ("white".equals(player.getColor()) ? "" : " (" + player.getColor() + ")");
                graphics.drawString(userName, x, y);
                if (player.getFaction() == null || "white".equals(player.getColor()) || player.getColor() == null) {
                    continue;
                }

                y += 2;
                String faction = player.getFaction();
                if (faction != null) {
                    String factionPath = getFactionPath(faction);
                    if (factionPath != null) {
                        BufferedImage bufferedImage;
                        if ("keleres".equals(faction)) {
                            bufferedImage = resizeImage(ImageIO.read(new File(factionPath)), 0.7f);
                        } else {
                            bufferedImage = resizeImage(ImageIO.read(new File(factionPath)), percent);
                        }
                        graphics.drawImage(bufferedImage, x, y, null);
                    }
                }
                y += 4;
                int sc = player.getSC();
                String scText = sc == 0 ? " " : Integer.toString(sc);
                if (sc != 0) {
                    scText = getSCNumberIfNaaluInPlay(player, map, scText);
                }
                graphics.setColor(getSCColor(sc, map));
                graphics.setFont(Storage.getFont64());

                if (scText.contains("0/")) {
                    graphics.drawString("0", x + 90, y + 70 + yDelta);
                    graphics.setFont(Storage.getFont32());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(Integer.toString(sc), x + 120, y + 80 + yDelta);
                } else {
                    graphics.drawString(scText, x + 90, y + 70 + yDelta);
                }
                graphics.setFont(Storage.getFont32());
                graphics.setColor(Color.WHITE);
                String ccCount = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                x += 120;
                graphics.drawString(ccCount, x + 40, y + deltaY + 50);
                if (!player.getMahactCC().isEmpty()) {
                    graphics.drawString("+" + player.getMahactCC().size() + " FS", x + 40, y + deltaY + 90);
                }
                graphics.drawString("T/F/S", x + 40, y + deltaY + 10);

                String acImage = "pa_cardbacks_ac.png";
                String soImage = "pa_cardbacks_so.png";
                String pnImage = "pa_cardbacks_pn.png";
                String tradeGoodImage = "pa_cardbacks_tradegoods.png";
                String commoditiesImage = "pa_cardbacks_commodities.png";
                drawPAImage(x + 150, y + yDelta, soImage);
                graphics.drawString(Integer.toString(player.getSo()), x + 170, y + deltaY + 50);

                drawPAImage(x + 215, y + yDelta, acImage);
                int ac = player.getAc();
                int acDelta = ac > 9 ? 0 : 10;
                graphics.drawString(Integer.toString(ac), x + 225 + acDelta, y + deltaY + 50);

                drawPAImage(x + 280, y + yDelta, pnImage);
                graphics.drawString(Integer.toString(player.getPnCount()), x + 300, y + deltaY + 50);

                drawPAImage(x + 345, y + yDelta, tradeGoodImage);
                graphics.drawString(Integer.toString(player.getTg()), x + 365, y + deltaY + 50);

                drawPAImage(x + 410, y + yDelta, commoditiesImage);
                String comms = player.getCommodities() + "/" + player.getCommoditiesTotal();
                graphics.drawString(comms, x + 415, y + deltaY + 50);

                int vrf = player.getVrf();
                int irf = player.getIrf();
                String vrfImage = "pa_fragment_urf.png";
                String irfImage = "pa_fragment_irf.png";
                int xDelta = 0;
                xDelta = drawFrags(y, x, yDelta, vrf, vrfImage, xDelta);
                xDelta += 25;
                xDelta = drawFrags(y, x, yDelta, irf, irfImage, xDelta);


                int xDelta2 = 0;
                int hrf = player.getHrf();
                int crf = player.getCrf();
                String hrfImage = "pa_fragment_hrf.png";
                String crfImage = "pa_fragment_crf.png";
                xDelta2 = drawFrags(y + 73, x, yDelta, hrf, hrfImage, xDelta2);
                xDelta2 += 25;
                xDelta2 = drawFrags(y + 73, x, yDelta, crf, crfImage, xDelta2);

                xDelta = x + 550 + Math.max(xDelta, xDelta2);
                int yPlayArea = y - 30;
                y += 85;
                y += 200;
                int xDeltaSecondRow = xDelta;
                int yPlayAreaSecondRow = yPlayArea + 160;
                if (!player.getPlanets().isEmpty()) {
                    xDeltaSecondRow = planetInfo(player, map, xDeltaSecondRow, yPlayAreaSecondRow);
                }
                if (!player.getLeaders().isEmpty()) {
                    xDelta = leaderInfo(player, xDelta, yPlayArea);
                }

                if (!player.getRelics().isEmpty()) {
                    xDelta = relicInfo(player, xDelta, yPlayArea);
                }

                if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
                    xDelta = pnInfo(player, xDelta, yPlayArea, map);
                }

                if (!player.getTechs().isEmpty()) {
                    xDelta = techInfo(player, xDelta, yPlayArea, map);
                }

                g2.setColor(color);
                g2.drawRect(realX - 5, baseY, x + widthOfLine, y - baseY);
                y += 15;

            }
            y += 40;
            graphics.setColor(Color.WHITE);
            graphics.setFont(Storage.getFont32());
            graphics.drawString("LAWS", x, y);

            graphics.setFont(Storage.getFont26());
            LinkedHashMap<String, Integer> laws = map.getLaws();
            LinkedHashMap<String, String> lawsInfo = map.getLawsInfo();
            for (java.util.Map.Entry<String, Integer> lawEntry : laws.entrySet()) {
                y += 30;
                String lawID = lawEntry.getKey();
                String text = "(" + lawEntry.getValue() + ") ";
                String optionalText = lawsInfo.get(lawID);
                if (optionalText != null) {
                    text += "Elected: " + optionalText + " - ";
                }
                graphics.drawString(text + Mapper.getAgendaForOnly(lawID), x, y);
            }
        }
    }

    private int pnInfo(Player player, int x, int y, Map map) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        Collection<Player> players = map.getPlayers().values();
        for (String pn : player.getPromissoryNotesInPlayArea()) {
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);

            String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(pn);
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    String playerFaction = player_.getFaction();
                    if (playerColor != null && playerColor.equals(promissoryNoteOwner) ||
                            playerFaction != null && playerFaction.equals(promissoryNoteOwner)) {
                        String pnColorFile = "pa_pn_color_" + Mapper.getColorID(playerColor) + ".png";
                        drawPAImage(x + deltaX, y, pnColorFile);

                        String pnFactionIcon = "pa_tech_factionicon_" + playerFaction + "_rdy.png";
                        drawPAImage(x + deltaX, y, pnFactionIcon);
                        break;
                    }
                }
            }

            if (pn.endsWith("_sftt")){
                pn = "sftt";
            } else if (pn.endsWith("_an")) {
                pn = "alliance";
            }

            String pnName =  "pa_pn_name_" + pn + ".png";
            drawPAImage(x + deltaX, y, pnName);
            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private int drawFrags(int y, int x, int yDelta, int vrf, String vrfImage, int xDelta) {
        for (int i = 0; i < vrf; i++) {
            drawPAImage(x + 475 + xDelta, y + yDelta - 25, vrfImage);
            xDelta += 15;
        }
        return xDelta;
    }

    private int relicInfo(Player player, int x, int y) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        List<String> exhaustedRelics = player.getExhaustedRelics();
        for (String relicID : player.getRelics()) {


            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }
            String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
            String relicFileName = "pa_relics_" + relicID + statusOfPlanet + ".png";
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, "pa_relics_icon.png");
            drawPAImage(x + deltaX, y, relicFileName);
            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private int leaderInfo(Player player, int x, int y) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        for (Leader leader : player.getLeaders()) {
            boolean isExhaustedLocked = leader.isExhausted() || leader.isLocked();
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }
            String status = isExhaustedLocked ? "_exh" : "_rdy";
            String leaderFileName = "pa_leaders_factionicon_" + player.getFaction() + "_rdy.png";
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, leaderFileName);

            if (leader.getTgCount() != 0){
                graphics.setColor(new Color(241,176,0));
                graphics.setFont(Storage.getFont32());
                graphics.drawString( Integer.toString(leader.getTgCount()), x + deltaX + 3, y + 32);
            } else {
                String pipID;
                switch (leader.getId()) {
                    case Constants.AGENT -> pipID = "i";
                    case Constants.COMMANDER -> pipID = "ii";
                    case Constants.HERO -> pipID = "iii";
                    default -> pipID = "";
                }
                if (!pipID.isEmpty()) {
                    String leaderPipInfo = "pa_leaders_pips_" + pipID + status + ".png";
                    drawPAImage(x + deltaX, y, leaderPipInfo);
                }
            }

            String extraInfo = leader.getName().isEmpty() ? "" : "_" + leader.getName();
            String leaderInfoFileName = "pa_leaders_" + leader.getId() + "_" + player.getFaction() + extraInfo + status +".png";
            drawPAImage(x + deltaX, y, leaderInfoFileName);

            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private int planetInfo(Player player, Map map, int x, int y) {
        HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
        List<String> planets = player.getPlanets();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        for (String planet : planets) {

            UnitHolder unitHolder = planetsInfo.get(planet);
            if (!(unitHolder instanceof Planet planetHolder)) {
                LoggerHandler.log("Planet unitHolder not found: " + planet);
                continue;
            }

            boolean isExhausted = exhaustedPlanets.contains(planet);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            int resources = planetHolder.getResources();
            int influence = planetHolder.getInfluence();
            String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
            String planetFileName = "pc_planetname_" + planet + statusOfPlanet + ".png";
            String resFileName = "pc_res_" + resources + statusOfPlanet + ".png";
            String infFileName = "pc_inf_" + influence + statusOfPlanet + ".png";

            graphics.drawRect(x + deltaX - 2, y - 2, 52, 152);

            if (unitHolder.getTokenList().contains("attachment_titanspn.png")) {
                String planetTypeName = "pc_attribute_titanspn.png";
                drawPlanetImage(x + deltaX + 2, y + 2, planetTypeName);
            } else {
                String originalPlanetType = planetHolder.getOriginalPlanetType();
                if (!originalPlanetType.isEmpty()) {
                    if ("keleres".equals(player.getFaction()) && ("mentak".equals(originalPlanetType) ||
                            "xxcha".equals(originalPlanetType) ||
                            "argent".equals(originalPlanetType))) {
                        originalPlanetType = "keleres";
                    }

                    String planetTypeName = "pc_attribute_" + originalPlanetType + ".png";
                    drawPlanetImage(x + deltaX + 2, y + 2, planetTypeName);
                }
            }

            boolean hasAttachment = planetHolder.hasAttachment();
            if (hasAttachment) {
                String planetTypeName = "pc_upgrade.png";
                drawPlanetImage(x + deltaX + 26, y + 40, planetTypeName);
            }

            boolean hasAbility = planetHolder.isHasAbility();
            if (hasAbility) {
                String statusOfAbility = exhaustedPlanetsAbilities.contains(planet) ? "_exh" : "_rdy";
                String planetTypeName = "pc_legendary" + statusOfAbility + ".png";
                drawPlanetImage(x + deltaX + 26, y + 60, planetTypeName);
            }
            String originalTechSpeciality = planetHolder.getOriginalTechSpeciality();
            int deltaY = 175;
            if (!originalTechSpeciality.isEmpty()) {
                String planetTypeName = "pc_tech_" + originalTechSpeciality + statusOfPlanet + ".png";
                drawPlanetImage(x + deltaX + 26, y + 82, planetTypeName);
            } else {
                ArrayList<String> techSpeciality = planetHolder.getTechSpeciality();
                for (String techSpec : techSpeciality) {
                    if (techSpec.isEmpty()) {
                        continue;
                    }
                    String planetTypeName = "pc_tech_" + techSpec + statusOfPlanet + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 82, planetTypeName);
                    deltaY -= 20;
                }
            }


            drawPlanetImage(x + deltaX + 26, y + 103, resFileName);
            drawPlanetImage(x + deltaX + 26, y + 125, infFileName);
            drawPlanetImage(x + deltaX, y, planetFileName);


            deltaX += 56;
        }
        return x + deltaX + 20;
    }

    private int techInfo(Player player, int x, int y, Map map) {
        List<String> techs = player.getTechs();
        List<String> exhaustedTechs = player.getExhaustedTechs();
        if (techs.isEmpty()) {
            return y;
        }


        HashMap<String, String[]> techInfo = Mapper.getTechsInfo();
        java.util.Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : techs) {
            String techType = Mapper.getTechType(tech);
            List<String> techList = techsFiltered.get(techType);
            if (techList == null){
                techList = new ArrayList<>();
            }
            techList.add(tech);
            techsFiltered.put(techType, techList);
        }
        for (java.util.Map.Entry<String, List<String>> entry : techsFiltered.entrySet()) {
            List<String> list = entry.getValue();
            list.sort(new Comparator<String>() {
                @Override
                public int compare(String tech1, String tech2) {
                    String[] tech1Info = techInfo.get(tech1);
                    String[] tech2Info = techInfo.get(tech2);
                    try {
                        int t1 = tech1Info.length >= 3 ? tech1Info[2].length() : 0;
                        int t2 = tech2Info.length >= 3 ? tech2Info[2].length() : 0;
                        return (t1 < t2) ? -1: ((t1 == t2) ? (tech1Info[0].compareTo(tech2Info[0])) : 1);
                    } catch (Exception e) {
                        //do nothing
                    }
                    return 0;
                }
            });
        }
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));


        deltaX = techField(x, y, techsFiltered.get(Constants.BIOTIC), exhaustedTechs, techInfo, deltaX);
        deltaX = techField(x, y, techsFiltered.get(Constants.PROPULSION), exhaustedTechs, techInfo, deltaX);
        deltaX = techField(x, y, techsFiltered.get(Constants.CYBERNETIC), exhaustedTechs, techInfo, deltaX);
        deltaX = techField(x, y, techsFiltered.get(Constants.WARFARE), exhaustedTechs, techInfo, deltaX);
        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), exhaustedTechs, techInfo, deltaX, player, map);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, HashMap<String, String[]> techInfo, int deltaX) {
        if (techs == null){
            return deltaX;
        }
        for (String tech : techs) {
            boolean isExhausted = exhaustedTechs.contains(tech);
            String techStatus;
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
                techStatus = "_exh.png";
            } else {
                graphics.setColor(Color.WHITE);
                techStatus = "_rdy.png";
            }

            String[] techInformation = techInfo.get(tech);

            String techIcon;
            switch (techInformation[1]) {
                case Constants.WARFARE -> techIcon = Constants.WARFARE;
                case Constants.PROPULSION -> techIcon = Constants.PROPULSION;
                case Constants.CYBERNETIC -> techIcon = Constants.CYBERNETIC;
                case Constants.BIOTIC -> techIcon = Constants.BIOTIC;
                default -> techIcon = "";
            }

            if (!techIcon.isEmpty()){
                String techSpec = "pa_tech_techicons_" + techIcon + techStatus;
                drawPAImage(x + deltaX, y, techSpec);
            }

            if (techInformation.length >= 4 && !techInformation[3].isEmpty()){
                String techSpec = "pa_tech_factionicon_" + techInformation[3] + "_rdy.png";
                drawPAImage(x + deltaX, y, techSpec);
            }

            String techName = "pa_tech_techname_" + tech + techStatus;
            drawPAImage(x + deltaX, y, techName);


            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }

    private int techFieldUnit(int x, int y, List<String> techs, List<String> exhaustedTechs, HashMap<String, String[]> techInfo, int deltaX, Player player, Map map) {

        String outline = "pa_tech_unitsnew_outlines_generic.png";
        if ("nomad".equals(player.getFaction())){
            outline = "pa_tech_unitsnew_outlines_nomad.png";
        }
        if ("nekro".equals(player.getFaction())){
            for (Player player_ : map.getPlayers().values()) {
                if ("nomad".equals(player_.getFaction())){
                    outline = "pa_tech_unitsnew_outlines_nomad.png";
                    break;
                }
            }
        }
        drawPAImage(x + deltaX, y, outline);
        if (techs != null) {

            for (String tech : techs) {
                String[] techInformation = techInfo.get(tech);

                String unit = "pa_tech_unitsnew_" + Mapper.getColorID(player.getColor()) + "_";
                if (techInformation.length >= 5) {
                    unit += techInformation[4] + ".png";
                } else {
                    unit += tech + ".png";
                }
                drawPAImage(x + deltaX, y, unit);
                if (techInformation.length >= 4 && !techInformation[3].isEmpty()) {
                    String factionIcon = "pa_tech_unitsnew_" + techInformation[3] + "_" + tech + ".png";
                    drawPAImage(x + deltaX, y, factionIcon);
                }
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 224, 152);
        deltaX += 228;
        return deltaX;
    }

    private void drawPlanetImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            LoggerHandler.log("Could not display planet: " + resourceName, e);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            LoggerHandler.log("Could not display play area: " + resourceName, e);
        }
    }

    private int scoreTrack(Map map, int y) {

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(5));
        graphics.setFont(Storage.getFont50());
        int height = 140;
        int width = 150;
        for (int i = 0; i <= map.getVp(); i++) {
            graphics.setColor(Color.WHITE);
            graphics.drawString(Integer.toString(i), i * width + 55, y + (height / 2) + 25);
            g2.setColor(Color.RED);
            g2.drawRect(i * width, y, width, height);
        }

        Collection<Player> players = map.getPlayers().values();
        int tempCounter = 0;
        int tempX = 0;
        int tempWidth = 0;
        for (Player player : players) {
            try {
                String controlID = Mapper.getControlID(player.getColor());
                BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), 0.7f);
                tempWidth = bufferedImage.getWidth();
                Integer vpCount = userVPs.get(player);
                if (vpCount == null) {
                    vpCount = 0;
                }
                int x = vpCount * width + 5 + tempX;
                graphics.drawImage(bufferedImage, x, y + (tempCounter * bufferedImage.getHeight()), null);
            } catch (Exception e) {
                //nothing
//                LoggerHandler.log("Could not display player: " + player.getUserName() + " VP count", e);
            }
            tempCounter++;
            if (tempCounter >= 4) {
                tempCounter = 0;
                tempX = tempWidth;
            }
        }
        y += 180;
        return y;
    }

    public static String getSCNumberIfNaaluInPlay(Player player, Map map, String scText) {
        if (Constants.NAALU.equals(player.getFaction())) {
            boolean giftPlayed = false;
            for (Player player_ : map.getPlayers().values()) {
                if (player != player_ && player_.getPromissoryNotesInPlayArea().contains(Constants.NAALU_PN)) {
                    giftPlayed = true;
                    break;
                }
            }
            if (!giftPlayed) {
                scText = "0/" + scText;
            }
        } else if (player.getPromissoryNotesInPlayArea().contains(Constants.NAALU_PN)) {
            scText = "0/" + scText;
        }
        return scText;
    }

    private int strategyCards(Map map, int y) {
        y += 80;
        LinkedHashMap<Integer, Integer> scTradeGoods = map.getScTradeGoods();
        Collection<Player> players = map.getPlayers().values();
        Set<Integer> scPicked = players.stream().map(Player::getSC).collect(Collectors.toSet());
        int x = 20;
        for (java.util.Map.Entry<Integer, Integer> scTGs : scTradeGoods.entrySet()) {
            Integer sc = scTGs.getKey();
            if (!scPicked.contains(sc)) {
                graphics.setColor(getSCColor(sc));
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, y);
                Integer tg = scTGs.getValue();
                if (tg > 0) {
                    graphics.setFont(Storage.getFont26());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString("TG:" + tg, x, y + 30);
                }
            }
            x += 80;
        }
        return y + 40;
    }

    private void playerInfo(Map map) {
        int playerPosition = 1;
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        Player speaker = map.getPlayer(map.getSpeaker());
        for (java.util.Map.Entry<String, Player> playerEntry : map.getPlayers().entrySet()) {
            ArrayList<Point> points = PositionMapper.getPlayerPosition(playerPosition, map);
            if (points.isEmpty()) {
                continue;
            }
            Player player = playerEntry.getValue();
            String userName = player.getUserName();

            graphics.drawString(userName.substring(0, Math.min(userName.length(), 11)), points.get(0).x, points.get(0).y);
            Integer vpCount = userVPs.get(player);
            vpCount = vpCount == null ? 0 : vpCount;
            graphics.drawString("VP - " + vpCount, points.get(1).x, points.get(1).y);

            int sc = player.getSC();
            String scText = sc == 0 ? " " : Integer.toString(sc);
            scText = getSCNumberIfNaaluInPlay(player, map, scText);
            graphics.setColor(getSCColor(sc, map));
            graphics.setFont(Storage.getFont64());
            if (sc != 0) {
                graphics.drawString(scText, points.get(4).x, points.get(4).y);
            }
            graphics.setColor(Color.WHITE);
            graphics.setFont(Storage.getFont32());
            String ccID = Mapper.getCCID(player.getColor());
            String fleetCCID = Mapper.getFleeCCID(player.getColor());
            int x = points.get(2).x;
            int y = points.get(2).y;
            drawCCOfPlayer(ccID, x, y, player.getTacticalCC(), false, null);
//            drawCCOfPlayer(fleetCCID, x, y + 65, player.getFleetCC(), "letnev".equals(player.getFaction()));
            drawCCOfPlayer(fleetCCID, x, y + 65, player.getFleetCC(), false, player);
            drawCCOfPlayer(ccID, x, y + 130, player.getStrategicCC(), false, null);

            if (player == speaker) {
                String speakerID = Mapper.getTokenID(Constants.SPEAKER);
                String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
                if (speakerFile != null) {
                    BufferedImage bufferedImage = null;
                    try {
                        bufferedImage = ImageIO.read(new File(speakerFile));
                    } catch (IOException e) {
                        LoggerHandler.log("Could not read speaker file");
                    }
                    graphics.drawImage(bufferedImage, points.get(3).x, points.get(3).y, null);
                    graphics.setColor(Color.WHITE);
                }
            }
            if (player.isPassed()) {
                graphics.setColor(new Color(238, 58, 80));
                graphics.drawString("PASSED", points.get(5).x, points.get(5).y);
                graphics.setColor(Color.WHITE);
            }

            playerPosition++;
        }


    }

    private void drawCCOfPlayer(String ccID, int x, int y, int ccCount, boolean isLetnev, Player player) {
        String ccPath = Mapper.getCCPath(ccID);
        try {
            BufferedImage ccImage = resizeImage(ImageIO.read(new File(ccPath)), 0.75f);
            int delta = 20;
            if (isLetnev) {
                for (int i = 0; i < 2; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                }
                x += 20;
                for (int i = 2; i < ccCount + 2; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                }
            } else {
                int lastCCPosition = -1;
                for (int i = 0; i < ccCount; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                    lastCCPosition = i;
                }
                List<String> mahactCC = player.getMahactCC();
                if (!mahactCC.isEmpty()) {
                    for (String ccColor : mahactCC) {
                        lastCCPosition++;
                        String fleetCCID = Mapper.getCCPath(Mapper.getFleeCCID(ccColor));
                        BufferedImage ccImageExtra = resizeImage(ImageIO.read(new File(fleetCCID)), 0.75f);
                        graphics.drawImage(ccImageExtra, x + (delta * lastCCPosition), y, null);
                    }
                }
            }
        } catch (Exception e) {
            //None
//            LoggerHandler.log("Could not parse cc file for: " + ccID, e);
        }
    }

    private int techs(Player player, int y) {
        int x = 230;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));
        graphics.setFont(Storage.getFont26());
        Integer[] column = new Integer[1];
        column[0] = 0;
        List<String> techs = player.getTechs();
        List<String> exhaustedTechs = player.getExhaustedTechs();
        if (techs.isEmpty()) {
            return y;
        }
        techs.sort(Comparator.comparing(Mapper::getTechType));
        HashMap<String, String> techInfo = Mapper.getTechs();
        y += 25;
        for (String tech : techs) {
            switch (column[0]) {
                case 0 -> x = 230;
                case 1 -> x = 630;
                case 2 -> x = 1030;
                case 3 -> x = 1430;
                case 4 -> x = 1830;
            }
            if (exhaustedTechs.contains(tech)) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(getTechColor(Mapper.getTechType(tech)));
            }
            String techName = techInfo.get(tech);
            if (techName != null) {
                graphics.drawString(techName, x, y);
            }
            column[0]++;
            if (column[0] > 4) {
                column[0] = 0;
                y += 25;
            }
        }
        return y;
    }

    private int objectives(Map map, int y) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));
        userVPs = new HashMap<>();

        LinkedHashMap<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>(map.getScoredPublicObjectives());
        LinkedHashMap<String, Integer> revealedPublicObjectives = new LinkedHashMap<>(map.getRevealedPublicObjectives());
        LinkedHashMap<String, Player> players = map.getPlayers();
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesState1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesState2();
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        LinkedHashMap<String, Integer> customPublicVP = map.getCustomPublicVP();
        LinkedHashMap<String, String> customPublics = customPublicVP.keySet().stream().collect(Collectors.toMap(key -> key, name -> name, (key1, key2) -> key1, LinkedHashMap::new));
        Set<String> po1 = publicObjectivesState1.keySet();
        Set<String> po2 = publicObjectivesState2.keySet();
        Set<String> customVP = customPublicVP.keySet();
        Set<String> secret = secretObjectives.keySet();

        graphics.setFont(Storage.getFont26());
        graphics.setColor(new Color(230, 126, 34));
        Integer[] column = new Integer[1];
        column[0] = 0;
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState1, po1, 1, column, null);

        graphics.setColor(new Color(93, 173, 226));
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState2, po2, 2, column, null);

        graphics.setColor(Color.WHITE);
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, customPublics, customVP, null, column, customPublicVP);

        revealedPublicObjectives = new LinkedHashMap<>();
        scoredPublicObjectives = new LinkedHashMap<>();
        for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
            Player player = playerEntry.getValue();
            LinkedHashMap<String, Integer> secretsScored = player.getSecretsScored();
            revealedPublicObjectives.putAll(secretsScored);
            for (String id : secretsScored.keySet()) {
                scoredPublicObjectives.put(id, List.of(player.getUserID()));
            }
        }

        graphics.setColor(Color.RED);
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, secretObjectives, secret, 1, column, customPublicVP);
        if (column[0] != 0) {
            y += 40;
        }

        graphics.setColor(Color.green);
        displaySftT(y, x, players, column);


        return y;
    }

    private int displaySftT(int y, int x, LinkedHashMap<String, Player> players, Integer[] column) {
        for (Player player : players.values()) {
            List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
            for (String id : promissoryNotesInPlayArea) {
                if (id.endsWith("_sftt")) {
                    Set<String> keysToRemove = new HashSet<>();

                    switch (column[0]) {
                        case 0 -> x = 5;
                        case 1 -> x = 801;
                        case 2 -> x = 1598;
                    }
                    String[] pnSplit = Mapper.getPromissoryNote(id).split(";");
                    String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
                    StringBuilder name = new StringBuilder(pnSplit[0] + " - ");
                    for (Player player_ : players.values()) {
                        if (player_ != player) {
                            String playerColor = player_.getColor();
                            String playerFaction = player_.getFaction();
                            if (playerColor != null && playerColor.equals(promissoryNoteOwner) ||
                                    playerFaction != null && playerFaction.equals(promissoryNoteOwner)) {
                                name.append(playerFaction).append(" (").append(playerColor).append(")");
                            }
                        }
                    }

//                    graphics.drawString(name + " - " + 1 + " VP", x, y + 23);
                    boolean multiScoring = false;
                    drawScoreControlMarkers(x + 515, y, players, Collections.singletonList(player.getUserID()), multiScoring, 1, true);
//                    graphics.drawRect(x - 4, y - 5, 785, 38);
                    column[0]++;
                    if (column[0] > 2) {
                        column[0] = 0;
                        y += 43;
                    }
                }
            }
        }
        return y;
    }

    private int displayObjectives(int y, int x, LinkedHashMap<String, List<String>> scoredPublicObjectives, LinkedHashMap<String, Integer> revealedPublicObjectives,
                                  LinkedHashMap<String, Player> players, HashMap<String, String> publicObjectivesState, Set<String> po, Integer objectiveWorth, Integer[] column, LinkedHashMap<String, Integer> customPublicVP) {
        Set<String> keysToRemove = new HashSet<>();
        for (java.util.Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            switch (column[0]) {
                case 0 -> x = 5;
                case 1 -> x = 801;
                case 2 -> x = 1598;
            }

            String key = revealed.getKey();
            if (!po.contains(key)) {
                continue;
            }
            String name = publicObjectivesState.get(key);
            Integer index = revealedPublicObjectives.get(key);
            if (index == null) {
                continue;
            }
            keysToRemove.add(key);
            if (customPublicVP != null) {
                objectiveWorth = customPublicVP.get(key);
                if (objectiveWorth == null) {
                    objectiveWorth = 1;
                }
            }
            graphics.drawString("(" + index + ") " + name + " - " + objectiveWorth + " VP", x, y + 23);
            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            boolean multiScoring = Constants.CUSTODIAN.equals(key);
            if (scoredPlayerID != null) {
                drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, multiScoring, objectiveWorth, false);
            }
            graphics.drawRect(x - 4, y - 5, 785, 38);
            column[0]++;
            if (column[0] > 2) {
                column[0] = 0;
                y += 43;
            }
        }
        keysToRemove.forEach(revealedPublicObjectives::remove);

        return y;
    }

    private void drawScoreControlMarkers(int x, int y, LinkedHashMap<String, Player> players, List<String> scoredPlayerID, boolean multiScoring, Integer objectiveWorth, boolean justCalculate) {
        try {
            int tempX = 0;
            for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
                Player player = playerEntry.getValue();
                String userID = player.getUserID();
                if (scoredPlayerID.contains(userID)) {
                    String controlID = Mapper.getControlID(player.getColor());
                    if (controlID.contains("null")) {
                        continue;
                    }
                    BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), 0.55f);
                    Integer vpCount = userVPs.get(player);
                    if (vpCount == null) {
                        vpCount = 0;
                    }
                    if (multiScoring) {
                        int frequency = Collections.frequency(scoredPlayerID, userID);
                        vpCount += frequency * objectiveWorth;
                        for (int i = 0; i < frequency; i++) {
                            if (!justCalculate) {
                                graphics.drawImage(bufferedImage, x + tempX, y, null);
                            }
                            tempX += scoreTokenWidth;
                        }
                    } else {
                        vpCount += objectiveWorth;
                        if (!justCalculate) {
                            graphics.drawImage(bufferedImage, x + tempX, y, null);
                        }
                    }
                    userVPs.put(player, vpCount);
                }
                if (!multiScoring) {
                    tempX += scoreTokenWidth;
                }
            }
        } catch (Exception e) {
            LoggerHandler.log("Could not parse custodian CV token file", e);
        }
    }

    private Color getSCColor(int sc, Map map) {
        HashMap<Integer, Boolean> scPlayed = map.getScPlayed();
        if (scPlayed.get(sc) != null) {
            if (scPlayed.get(sc)) {
                return Color.GRAY;
            }
        }
        return getSCColor(sc);
    }

    private Color getSCColor(int sc) {
        return switch (sc) {
            case 1 -> new Color(255, 38, 38);
            case 2 -> new Color(253, 168, 24);
            case 3 -> new Color(247, 237, 28);
            case 4 -> new Color(46, 204, 113);
            case 5 -> new Color(26, 188, 156);
            case 6 -> new Color(52, 152, 171);
            case 7 -> new Color(155, 89, 182);
            case 8 -> new Color(124, 0, 192);
            case 9 -> new Color(251, 96, 213);
            case 10 -> new Color(165, 211, 34);
            default -> Color.WHITE;
        };
    }

    private Color getColor(String color) {
        if (color == null) {
            return Color.WHITE;
        }
        switch (color) {
            case "black":
                return Color.DARK_GRAY;
            case "blue":
                return Color.BLUE;
            case "green":
                return Color.GREEN;
            case "gray":
                return Color.GRAY;
            case "grey":
                return Color.GRAY;
            case "orange":
                return Color.ORANGE;
            case "pink":
                return new Color(246, 153, 205);
            case "purple":
                return new Color(166, 85, 247);
            case "red":
                return Color.RED;
            case "yellow":
                return Color.YELLOW;
            default:
                return Color.WHITE;
        }
    }

    private Color getTechColor(String type) {
        if (type == null) {
            return Color.WHITE;
        }
        return switch (type) {
            case "propulsion" -> new Color(102, 153, 255);
            case "biotic" -> new Color(0, 204, 0);
            case "warfare" -> new Color(204, 0, 0);
            case "cybernetic" -> new Color(230, 230, 0);
            default -> Color.WHITE;
        };
    }


    private void addTile(Tile tile, Map map, boolean justTile) {
        try {
            BufferedImage image = ImageIO.read(new File(tile.getTilePath()));
            Point positionPoint = PositionMapper.getTilePosition(tile.getPosition(), map);
            if (positionPoint == null) {
                System.out.println();
            }
            int tileX = positionPoint.x + extraWidth;
            int tileY = positionPoint.y;
            if (justTile) {
                graphics.drawImage(image, tileX, tileY, null);

                graphics.setFont(Storage.getFont20());
                graphics.setColor(Color.WHITE);
                graphics.drawString(tile.getPosition(), tileX + tilePositionPoint.x, tileY + tilePositionPoint.y);
                return;
            }
            ArrayList<Rectangle> rectangles = new ArrayList<>();

            Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
            UnitHolder spaceUnitHolder = unitHolders.stream().filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);
            if (spaceUnitHolder != null) {
                image = addToken(tile, image, tileX, tileY, spaceUnitHolder);
                unitHolders.remove(spaceUnitHolder);
                unitHolders.add(spaceUnitHolder);
            }
            int degree;
            int degreeChange = 5;
            for (UnitHolder unitHolder : unitHolders) {
                image = addSleeperToken(tile, image, tileX, tileY, unitHolder);
                image = addControl(tile, image, tileX, tileY, unitHolder, rectangles);
            }
            if (spaceUnitHolder != null) {
                image = addCC(tile, image, tileX, tileY, spaceUnitHolder);
            }
            for (UnitHolder unitHolder : unitHolders) {
                degree = 180;
                int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                if (unitHolder != spaceUnitHolder) {
                    image = addPlanetToken(tile, image, tileX, tileY, unitHolder, rectangles);
                }
                image = addUnits(tile, image, tileX, tileY, rectangles, degree, degreeChange, unitHolder, radius);
            }

        } catch (IOException e) {
            LoggerHandler.log("Error drawing tile: " + tile.getTileID(), e);
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage, float percent) throws IOException {
        int scaledWidth = (int) (originalImage.getWidth() * percent);
        int scaledHeight = (int) (originalImage.getHeight() * percent);
        Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT);
        BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    private BufferedImage addCC(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder) {
        HashSet<String> ccList = unitHolder.getCCList();
        int deltaX = 0;//ccList.size() * 20;
        int deltaY = 0;//ccList.size() * 20;
        for (String ccID : ccList) {
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
//                LoggerHandler.log("Could not parse cc file for: " + ccID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(ccPath)), 0.85f);
            } catch (Exception e) {
//                LoggerHandler.log("Could not parse cc file for: " + ccID, e);
            }
            Point centerPosition = unitHolder.getHolderCenterPosition();
            graphics.drawImage(image, tileX + 10 + deltaX, tileY + centerPosition.y - 40 + deltaY, null);
            deltaX += image.getWidth() / 5;
            deltaY += image.getHeight() / 4;
        }
        return image;
    }

    private BufferedImage addControl(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder, ArrayList<Rectangle> rectangles) {
        ArrayList<String> controlList = new ArrayList<>(unitHolder.getControlList());
        PlanetTokenPosition planetTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (planetTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String controlID : controlList) {
                if (controlID.contains(Constants.SLEEPER)) {
                    continue;
                }
                String controlPath = tile.getCCPath(controlID);
                if (controlPath == null) {
                    LoggerHandler.log("Could not parse control token file for: " + controlID);
                    continue;
                }
                float scale = 1.00f;
                try {
                    image = resizeImage(ImageIO.read(new File(controlPath)), scale);
                } catch (Exception e) {
                    LoggerHandler.log("Could not parse control token file for: " + controlID, e);
                }
                boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
                if (isMirage) {
                    tileX += Constants.MIRAGE_POSITION.x;
                    tileY += Constants.MIRAGE_POSITION.y;
                }
                Point position = planetTokenPosition.getPosition(controlID);
                if (position != null) {
                    graphics.drawImage(image, tileX + position.x, tileY + position.y, null);
                    rectangles.add(new Rectangle(tileX + position.x, tileY + position.y, image.getWidth(), image.getHeight()));
                } else {
                    graphics.drawImage(image, tileX + centerPosition.x + xDelta, tileY + centerPosition.y, null);
                    rectangles.add(new Rectangle(tileX + centerPosition.x + xDelta, tileY + centerPosition.y, image.getWidth(), image.getHeight()));
                    xDelta += 10;
                }
            }
            return image;
        } else {
            return oldFormatPlanetTokenAdd(tile, image, tileX, tileY, unitHolder, controlList);
        }
    }

    private BufferedImage addSleeperToken(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder) {
        Point centerPosition = unitHolder.getHolderCenterPosition();
        ArrayList<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.sort((o1, o2) -> {
            if ((o1.contains(Constants.SLEEPER) || o2.contains(Constants.SLEEPER))) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        for (String tokenID : tokenList) {
            if (tokenID.contains(Constants.SLEEPER) || tokenID.contains(Constants.DMZ_LARGE) || tokenID.contains(Constants.WORLD_DESTROYED)) {
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    LoggerHandler.log("Could not sleeper token file for: " + tokenID);
                    continue;
                }
                float scale = 0.85f;
                if (tokenPath.contains(Constants.DMZ_LARGE)) {
                    scale = 0.6f;
                } else if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
                    scale = 0.8f;
                }
                try {
                    image = resizeImage(ImageIO.read(new File(tokenPath)), scale);
                } catch (Exception e) {
                    LoggerHandler.log("Could not parse sleeper token file for: " + tokenID, e);
                }
                Point position = new Point(centerPosition.x - (image.getWidth() / 2), centerPosition.y - (image.getHeight() / 2));
                graphics.drawImage(image, tileX + position.x, tileY + position.y - 10, null);
            }
        }
        return image;
    }

    private BufferedImage addPlanetToken(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder, ArrayList<Rectangle> rectangles) {
        ArrayList<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.sort((o1, o2) -> {
            if ((o1.contains("nanoforge") || o1.contains("titanspn"))) {
                return -1;
            } else if ((o2.contains("nanoforge") || o2.contains("titanspn"))) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        PlanetTokenPosition planetTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (planetTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String tokenID : tokenList) {
                if (tokenID.contains(Constants.SLEEPER) || tokenID.contains(Constants.DMZ_LARGE) || tokenID.contains(Constants.WORLD_DESTROYED)) {
                    continue;
                }
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    LoggerHandler.log("Could not parse token file for: " + tokenID);
                    continue;
                }
                float scale = 1.00f;
                try {
                    image = resizeImage(ImageIO.read(new File(tokenPath)), scale);
                } catch (Exception e) {
                    LoggerHandler.log("Could not parse control token file for: " + tokenID, e);
                }
                if (tokenPath.contains(Constants.DMZ_LARGE) || tokenPath.contains(Constants.WORLD_DESTROYED)) {
                    graphics.drawImage(image, tileX + centerPosition.x - (image.getWidth() / 2), tileY + centerPosition.y - (image.getHeight() / 2), null);
                } else {
                    Point position = planetTokenPosition.getPosition(tokenID);
                    if (position != null) {
                        graphics.drawImage(image, tileX + position.x, tileY + position.y, null);
                        rectangles.add(new Rectangle(tileX + position.x, tileY + position.y, image.getWidth(), image.getHeight()));
                    } else {
                        graphics.drawImage(image, tileX + centerPosition.x + xDelta, tileY + centerPosition.y, null);
                        rectangles.add(new Rectangle(tileX + centerPosition.x + xDelta, tileY + centerPosition.y, image.getWidth(), image.getHeight()));
                        xDelta += 10;
                    }
                }
            }
            return image;
        } else {
            return oldFormatPlanetTokenAdd(tile, image, tileX, tileY, unitHolder, tokenList);
        }
    }

    private BufferedImage oldFormatPlanetTokenAdd(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder, ArrayList<String> tokenList) {
        int deltaY = 0;
        int offSet = 0;
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = tileX + centerPosition.x;
        int y = tileY + centerPosition.y - (tokenList.size() > 1 ? 35 : 0);
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                LoggerHandler.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(tokenPath)), 0.85f);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse control token file for: " + tokenID, e);
            }
            graphics.drawImage(image, x - (image.getWidth() / 2), y + offSet + deltaY - (image.getHeight() / 2), null);
            y += image.getHeight();
        }
        return image;
    }

    private BufferedImage addToken(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder) {
        HashSet<String> tokenList = unitHolder.getTokenList();
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = tileX;
        int y = tileY;
        int deltaX = 80;
        int deltaY = 0;
        ArrayList<Point> spaceTokenPositions = PositionMapper.getSpaceTokenPositions(tile.getTileID());
        if (spaceTokenPositions.isEmpty()) {
            x = tileX + centerPosition.x;
            y = tileY + centerPosition.y;
        }
        int index = 0;
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                LoggerHandler.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                float scale = tokenPath.contains(Constants.MIRAGE) ? 1.0f : 0.80f;
                image = resizeImage(ImageIO.read(new File(tokenPath)), scale);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse control token file for: " + tokenID, e);
            }

            if (tokenPath.contains(Constants.MIRAGE)) {
                graphics.drawImage(image, tileX + Constants.MIRAGE_POSITION.x, tileY + Constants.MIRAGE_POSITION.y, null);
            } else if (tokenPath.contains(Constants.SLEEPER)) {
                graphics.drawImage(image, tileX + centerPosition.x - (image.getWidth() / 2), tileY + centerPosition.y - (image.getHeight() / 2), null);
            } else {
                if (spaceTokenPositions.size() > index) {
                    Point point = spaceTokenPositions.get(index);
                    graphics.drawImage(image, x + point.x, y + point.y, null);
                    index++;
                } else {
                    graphics.drawImage(image, x + deltaX, y + deltaY, null);
                    deltaX += 30;
                    deltaY += 30;
                }
            }
        }
        return image;
    }

    private BufferedImage addUnits(Tile tile, BufferedImage image, int tileX, int tileY, ArrayList<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius) {
        HashMap<String, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        LinkedHashMap<String, Integer> units = new LinkedHashMap<>();

        for (java.util.Map.Entry<String, Integer> entry : tempUnits.entrySet()) {
            String id = entry.getKey();
            //contains mech image
            if (id != null && id.contains("mf")) {
                units.put(id, entry.getValue());
            }
        }
        for (String key : units.keySet()) {
            tempUnits.remove(key);
        }
        units.putAll(tempUnits);
        HashMap<String, Integer> unitDamage = unitHolder.getUnitDamage();
        float scaleOfUnit = 1.0f;
        PlanetTokenPosition planetTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        BufferedImage dmgImage = null;
        try {
            BufferedImage read = ImageIO.read(new File(Helper.getDamagePath()));
            dmgImage = resizeImage(read, 0.8f);
        } catch (IOException e) {
            LoggerHandler.log("Could not parse damage token file.", e);
        }

        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);

        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
            String unitID = unitEntry.getKey();
            Integer unitCount = unitEntry.getValue();

            Integer unitDamageCount = unitDamage.get(unitID);

            Color groupUnitColor = Color.WHITE;
            Integer bulkUnitCount = null;
            if (unitID.startsWith("ylw") || unitID.startsWith("gry") || unitID.startsWith("org") || unitID.startsWith("pnk")) {
                groupUnitColor = Color.BLACK;
            }
            if (unitID.endsWith(Constants.COLOR_FF)) {
                unitID = unitID.replace(Constants.COLOR_FF, Constants.BULK_FF);
                bulkUnitCount = unitCount;
            } else if (unitID.endsWith(Constants.COLOR_GF)) {
                unitID = unitID.replace(Constants.COLOR_GF, Constants.BULK_GF);
                bulkUnitCount = unitCount;
            }


            try {
                image = resizeImage(ImageIO.read(new File(tile.getUnitPath(unitID))), scaleOfUnit);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse unit file for: " + unitID, e);
            }
            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;
            }


            Point centerPosition = unitHolder.getHolderCenterPosition();

            for (int i = 0; i < unitCount; i++) {
                Point position = planetTokenPosition != null ? planetTokenPosition.getPosition(unitID) : null;
                boolean searchPosition = true;
                int x = 0;
                int y = 0;
                while (searchPosition && position == null) {
                    x = (int) (radius * Math.sin(degree));
                    y = (int) (radius * Math.cos(degree));
                    int possibleX = tileX + centerPosition.x + x - (image.getWidth() / 2);
                    int possibleY = tileY + centerPosition.y + y - (image.getHeight() / 2);
                    BufferedImage finalImage = image;
                    if (rectangles.stream().noneMatch(rectangle -> rectangle.intersects(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()))) {
                        searchPosition = false;
                    } else if (degree > 360) {
                        searchPosition = false;
                        degree += 3;//To change degree if we did not find place, might be better placement then
                    }
                    degree += degreeChange;
                    if (!searchPosition) {
                        rectangles.add(new Rectangle(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()));
                    }
                }
                int xOriginal = tileX + centerPosition.x + x;
                int yOriginal = tileY + centerPosition.y + y;
                int imageX = position != null ? tileX + position.x : xOriginal - (image.getWidth() / 2);
                int imageY = position != null ? tileY + position.y : yOriginal - (image.getHeight() / 2);
                if (isMirage) {
                    imageX += Constants.MIRAGE_POSITION.x;
                    imageY += Constants.MIRAGE_POSITION.y;
                }
                graphics.drawImage(image, imageX, imageY, null);
                if (bulkUnitCount != null) {
                    graphics.setFont(Storage.getFont24());
                    graphics.setColor(groupUnitColor);
                    int scaledNumberPositionX = (int) (numberPositionPoint.x * scaleOfUnit);
                    int scaledNumberPositionY = (int) (numberPositionPoint.y * scaleOfUnit);
                    graphics.drawString(Integer.toString(bulkUnitCount), imageX + scaledNumberPositionX, imageY + scaledNumberPositionY);
                }

                if (unitDamageCount != null && unitDamageCount > 0 && dmgImage != null) {
                    int imageDmgX = position != null ? tileX + position.x + (image.getWidth() / 2) - (dmgImage.getWidth() / 2) : xOriginal - (dmgImage.getWidth() / 2);
                    int imageDmgY = position != null ? tileY + position.y + (image.getHeight() / 2) - (dmgImage.getHeight() / 2) : yOriginal - (dmgImage.getHeight() / 2);
                    if (isMirage) {
                        imageDmgX = imageX + (int) (numberPositionPoint.x * scaleOfUnit) - dmgImage.getWidth();
                        imageDmgY = imageY + (int) (numberPositionPoint.y * scaleOfUnit) - dmgImage.getHeight();
                    }
                    graphics.drawImage(dmgImage, imageDmgX, imageDmgY, null);
                    unitDamageCount--;
                }
            }
        }
        return image;
    }
}
