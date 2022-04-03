package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.LoggerHandler;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

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
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateMap {
    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;
    private int heightForGameInfo;
    private int scoreTokenWidth;
    private static Point tilePositionPoint = new Point(230, 295);
    private static Point numberPositionPoint = new Point(45, 35);

    private static GenerateMap instance;

    private GenerateMap() {
        String tileFile = ResourceHelper.getInstance().getTileFile("6player_setup.png");
        File setupFile = new File(tileFile);
        BufferedImage setupImage = null;
        try {
            setupImage = ImageIO.read(setupFile);
            String controlID = Mapper.getControlID("red");
            BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), 0.4f);
            scoreTokenWidth = bufferedImage.getWidth();
        } catch (IOException e) {
            LoggerHandler.logError("Could read file data for setup file", e);
        }
        if (setupImage == null) {
            LoggerHandler.log("Could not init map generator");
            //todo message to user
        }
        width = Math.max(setupImage.getWidth(), 2000);
        heightForGameInfo = setupImage.getHeight();
        height = heightForGameInfo + setupImage.getHeight() / 2 + 800;
        resetImage();
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
        resetImage();
        //todo fix temp map name
        File file = Storage.getMapImageStorage("temp.png");
        try {
            HashMap<String, Tile> tileMap = new HashMap<>(map.getTileMap());
            String setup = tileMap.keySet().stream()
                    .filter(key -> key.startsWith("setup"))
                    .findFirst()
                    .orElse(null);
            if (setup != null) {
                addTile(tileMap.get(setup));
                tileMap.remove(setup);
            }
            tileMap.keySet().stream()
                    .sorted()
                    .forEach(key -> addTile(tileMap.get(key)));

            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            graphics.drawString(map.getName(), 0, 34);

            gameInfo(map);

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
        String absolutePath = file.getAbsolutePath().replace(".png", ".jpg");
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
        return new File(absolutePath);
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

    private void gameInfo(Map map) throws IOException {

        int widthOfLine = 1800;
        int y = heightForGameInfo + 20;
        int x = 10;
        HashMap<String, Player> players = map.getPlayers();
        float percent = 0.15f;
        int deltaY = 50;

        y = objectives(map, y);

        String speakerID = Mapper.getTokenID(Constants.SPEAKER);
        String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
        if (speakerFile != null) {
            BufferedImage bufferedImage = ImageIO.read(new File(speakerFile));
            graphics.drawImage(bufferedImage, x, heightForGameInfo - bufferedImage.getHeight() - 120, null);
            graphics.setColor(Color.WHITE);
            Player player = map.getPlayer(map.getSpeaker());
            if (player != null) {
                graphics.drawString(player.getUserName(), x, heightForGameInfo - 90);
            }
        }

        graphics.setFont(Storage.getFont32());
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(5));
        for (Player player : players.values()) {
            int baseY = y;
            y += 34;
            Color color = getColor(player.getColor());
            graphics.setColor(Color.WHITE);
            graphics.drawString(player.getUserName(), x, y);
            y += 2;
            String faction = player.getFaction();
            if (faction != null) {
                String factionPath = getFactionPath(faction);
                if (factionPath != null) {
                    BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(factionPath)), percent);
                    graphics.drawImage(bufferedImage, x, y, null);
                }
            }
            StringBuilder sb = new StringBuilder();
            int sc = player.getSC();
            String scText = sc == 0 ? " " : Integer.toString(sc);
            sb.append("SC: ").append(scText).append("   ");

            graphics.setColor(getSCColor(sc, map));

            graphics.drawString(sb.toString(), x + 100, y + deltaY);
            graphics.setColor(color);

            graphics.setColor(Color.WHITE);
            sb = new StringBuilder();
            sb.append(player.getTacticalCC()).append("T/");
            sb.append(player.getFleetCC()).append("F/");
            sb.append(player.getStrategicCC()).append("S ");
            sb.append("TG: ").append(player.getTg());
            sb.append(" C:").append(player.getCommodities()).append("/").append(player.getCommoditiesTotal());
            sb.append(" ").append("AC: ").append(player.getAc()).append(" ");
            sb.append("PN: ").append(player.getPn()).append(" ");
            sb.append("SO: ").append(player.getSo()).append(" scored: ").append(player.getSoScored()).append(" ");
            sb.append("CRF: ").append(player.getCrf()).append(" ");
            sb.append("HRF: ").append(player.getHrf()).append(" ");
            sb.append("IRF: ").append(player.getIrf()).append(" ");
            sb.append("VRF: ").append(player.getVrf()).append(" ");
            if (player.isPassed()) {
                sb.append(" PASSED");

            }
            graphics.drawString(sb.toString(), x + 200, y + deltaY);
            graphics.setColor(color);
            y += 90;
            g2.setColor(color);
            g2.drawRect(x - 5, baseY, x + widthOfLine, y - baseY);
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
            graphics.drawString(text + Mapper.getAgenda(lawID), x, y);
        }
    }

    private int objectives(Map map, int y) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));

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

        graphics.setFont(Storage.getFont20());
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

        return y;
    }

    private int displayObjectives(int y, int x, LinkedHashMap<String, List<String>> scoredPublicObjectives, LinkedHashMap<String, Integer> revealedPublicObjectives,
                                  LinkedHashMap<String, Player> players, HashMap<String, String> publicObjectivesState, Set<String> po, Integer objectiveWorth, Integer[] column, LinkedHashMap<String, Integer> customPublicVP) {
        Set<String> keysToRemove = new HashSet<>();
        for (java.util.Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            switch (column[0]) {
                case 0 -> x = 5;
                case 1 -> x = 671;
                case 2 -> x = 1338;
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
            if (customPublicVP != null){
                objectiveWorth = customPublicVP.get(key);
                if (objectiveWorth == null){
                    objectiveWorth = 1;
                }
            }
            graphics.drawString("(" + index + ") " + name + " - " + objectiveWorth + " VP", x, y + 23);
            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            boolean multiScoring = Constants.CUSTODIAN.equals(key);
            if (scoredPlayerID != null) {
                drawScoreControlMarkers(x + 415, y, players, scoredPlayerID, multiScoring);
            }
            graphics.drawRect(x - 4, y - 5, 662, 35);
            column[0]++;
            if (column[0] > 2) {
                column[0] = 0;
                y += 40;
            }
        }
        keysToRemove.forEach(revealedPublicObjectives::remove);

        return y;
    }

    private void drawScoreControlMarkers(int x, int y, LinkedHashMap<String, Player> players, List<String> scoredPlayerID, boolean multiScoring) {
        try {
            int tempX = 0;
            for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
                Player player = playerEntry.getValue();
                String userID = player.getUserID();
                if (scoredPlayerID.contains(userID)) {
                    String controlID = Mapper.getControlID(player.getColor());
                    BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), 0.4f);
                    if (multiScoring){
                        int frequency = Collections.frequency(scoredPlayerID, userID);
                        for (int i = 0; i < frequency; i++) {
                            graphics.drawImage(bufferedImage, x + tempX, y, null);
                            tempX += scoreTokenWidth;
                        }
                    } else {
                        graphics.drawImage(bufferedImage, x + tempX, y, null);
                    }
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
        return switch (sc) {
            case 1 -> new Color(255, 38, 38);
            case 2 -> new Color(253, 168, 24);
            case 3 -> new Color(247, 237, 28);
            case 4 -> new Color(46, 204, 113);
            case 5 -> new Color(26, 188, 156);
            case 6 -> new Color(52, 152, 171);
            case 7 -> new Color(155, 89, 182);
            case 8 -> new Color(124, 0, 192);
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
                return Color.PINK;
            case "purple":
                return Color.MAGENTA;
            case "red":
                return Color.RED;
            case "yellow":
                return Color.YELLOW;
            default:
                return Color.WHITE;
        }
    }


    private void addTile(Tile tile) {
        try {
            BufferedImage image = ImageIO.read(new File(tile.getTilePath()));
            Point positionPoint = PositionMapper.getTilePosition(tile.getPosition());
            int tileX = positionPoint.x;
            int tileY = positionPoint.y;
            graphics.drawImage(image, tileX, tileY, null);

            graphics.setFont(Storage.getFont20());
            graphics.setColor(Color.WHITE);
            graphics.drawString(tile.getPosition(), tileX + tilePositionPoint.x, tileY + tilePositionPoint.y);

            ArrayList<Rectangle> rectangles = new ArrayList<>();

            Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
            UnitHolder spaceUnitHolder = unitHolders.stream().filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);
            if (spaceUnitHolder != null) {
                image = addCC(tile, image, tileX, tileY, spaceUnitHolder);
                image = addToken(tile, image, tileX, tileY, spaceUnitHolder);
                unitHolders.remove(spaceUnitHolder);
                unitHolders.add(spaceUnitHolder);
            }
            int degree;
            int degreeChange = 5;
            for (UnitHolder unitHolder : unitHolders) {
                degree = 180;
                int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                image = addSleeperToken(tile, image, tileX, tileY, unitHolder);
                image = addControl(tile, image, tileX, tileY, unitHolder, rectangles);
                if (unitHolder != spaceUnitHolder) {
                    image = addPlanetToken(tile, image, tileX, tileY, unitHolder, rectangles);
                }
                if (spaceUnitHolder != null) {
                    image = addCC(tile, image, tileX, tileY, spaceUnitHolder);
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
                LoggerHandler.log("Could not parse cc file for: " + ccID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(ccPath)), 0.85f);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse cc file for: " + ccID, e);
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
        HashSet<String> tokenList = unitHolder.getTokenList();
        Point centerPosition = unitHolder.getHolderCenterPosition();
        for (String tokenID : tokenList) {
            if (tokenID.contains(Constants.SLEEPER)) {
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    LoggerHandler.log("Could not sleeper token file for: " + tokenID);
                    continue;
                }
                try {
                    image = resizeImage(ImageIO.read(new File(tokenPath)), 0.85f);
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
            }
            return o1.compareTo(o2);
        });
        PlanetTokenPosition planetTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (planetTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String tokenID : tokenList) {
                if (tokenID.contains(Constants.SLEEPER)) {
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
        float scaleOfUnit = 0.80f;
        PlanetTokenPosition planetTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        BufferedImage dmgImage = null;
        try {
            dmgImage = resizeImage(ImageIO.read(new File(Helper.getDamagePath())), scaleOfUnit);
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
            if (unitID.startsWith("ylw")) {
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
                    graphics.setFont(Storage.getFont26());
                    graphics.setColor(groupUnitColor);
                    int scaledNumberPositionX = (int) (numberPositionPoint.x * scaleOfUnit);
                    int scaledNumberPositionY = (int) (numberPositionPoint.y * scaleOfUnit);
                    graphics.drawString(Integer.toString(bulkUnitCount), imageX + scaledNumberPositionX, imageY + scaledNumberPositionY);
                }

                if (unitDamageCount != null && unitDamageCount > 0 && dmgImage != null) {
                    int imageDmgX = position != null ? tileX + position.x + (image.getWidth() / 2) - (dmgImage.getWidth() / 2) : xOriginal - (dmgImage.getWidth() / 2);
                    int imageDmgY = position != null ? tileY + position.y + (image.getHeight() / 2) - (dmgImage.getHeight() / 2) : yOriginal - (dmgImage.getHeight() / 2);
                    graphics.drawImage(dmgImage, imageDmgX, imageDmgY, null);
                    unitDamageCount--;
                }
            }
        }
        return image;
    }
}
