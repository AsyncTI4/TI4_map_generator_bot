package ti4.generator;

import com.pngencoder.PngEncoder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.*;
import ti4.map.Map;
import ti4.map.*;
import ti4.message.BotLogger;

import javax.imageio.ImageIO;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public class GenerateMap {
    public static final int DELTA_X = 8;
    public static final int DELTA_Y = 24;
    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;
    private int heightStorage;
    private int heightStats;
    private int heightForGameInfo;
    private int heightForGameInfoStorage;
    private static int scoreTokenWidth = 0;
    private int extraWidth = 200;
    private static Point tilePositionPoint = new Point(230, 295);
    private static Point labelPositionPoint = new Point(90, 295);
    private static Point numberPositionPoint = new Point(40, 27);
    private static HashMap<Player, Integer> userVPs = new HashMap<>();

    private final int width6 = 2000;
    private final int heght6 = 2100;
    private final int width8 = 2500;
    private final int heght8 = 3350;

    private Boolean isFoWPrivate = null;
    private Player fowPlayer = null;
    private HashMap<String, Tile> tilesToDisplay = new HashMap<>();

    private static HashMap<String, String> unitIDToID = new HashMap<>();

    private static GenerateMap instance;

    private GenerateMap() {
        try {
            String controlID = Mapper.getControlID("red");
            BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), 0.45f);
            scoreTokenWidth = bufferedImage.getWidth();
        } catch (IOException e) {
            BotLogger.log("Could read file data for setup file");
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

    public File saveImage(Map map, @Nullable SlashCommandInteractionEvent event) {
        if (map.getDisplayTypeForced() != null) {
            return saveImage(map, map.getDisplayTypeForced(), event);
        }
        return saveImage(map, DisplayType.all, event);
    }

    public File saveImage(Map map, @Nullable DisplayType displayType, @Nullable SlashCommandInteractionEvent event) {
        long startup = System.currentTimeMillis();
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
            height = heightForGameInfoStorage + 640;
        } else {
            heightForGameInfo = heightForGameInfoStorage;
            height = heightStorage;
        }
        resetImage();

        isFoWPrivate = null;
        fowPlayer = null;
        tilesToDisplay = new HashMap<>(map.getTileMap());
        if (map.isFoWMode() && event != null) {
            isFoWPrivate = false;
            if (event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)) {
                isFoWPrivate = true;
                Player player = getFowPlayer(map, event);
                fowPlayer = Helper.getGamePlayer(map, player, event, null);

                Set<String> tilesToShow = fowFilter(map);
                updatePlayerFogTiles(map, fowPlayer, tilesToShow);

                Set<String> keys = new HashSet<>(tilesToDisplay.keySet());
                keys.removeAll(tilesToShow);
                for (String key : keys) {
                    tilesToDisplay.remove(key);
                    tilesToDisplay.put(key, fowPlayer.buildFogTile(key));
                }
            }
        }
        File file = Storage.getMapImageStorage("temp.png");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            if (displayType == DisplayType.all || displayType == DisplayType.map) {
                HashMap<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
                String setup = tileMap.keySet().stream()
                        .filter(key -> key.startsWith("setup"))
                        .findFirst()
                        .orElse(null);
                if (setup != null) {
                    addTile(tileMap.get(setup), map, TileStep.Setup);
                    tileMap.remove(setup);
                }

                Set<String> tiles = tileMap.keySet();
                Set<String> tilesWithExtra = new HashSet<String>(map.getAdjacentTileOverrides().values());
                tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), map, TileStep.Tile));
                tilesWithExtra.stream().forEach(key -> addTile(tileMap.get(key), map, TileStep.Extras));
                tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), map, TileStep.Units));
            }
            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            String timeStamp = getTimeStamp();
            graphics.drawString(map.getName() + " " + timeStamp, 0, 34);
            gameInfo(map, displayType);

            String testing = System.getenv("TESTING");
            if (testing == null && displayType == DisplayType.all && (isFoWPrivate == null || !isFoWPrivate)) {
                new Thread(() -> {
                    WebHelper.putMap(map.getName(), mainImage);
                    WebHelper.putData(map.getName(), map);
                }).start();
            }

            new PngEncoder()
                    .withBufferedImage(mainImage)
                    .withCompressionLevel(1)
                    .toFile(file);
        } catch (IOException e) {
            BotLogger.log(map.getName() + ": Could not save generated map");
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
            BotLogger.log("Could not save jpg file");
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        File jpgFile = new File(absolutePath);
        MapFileDeleter.addFileToDelete(jpgFile);
        long time = System.currentTimeMillis() - startup;
//        BotLogger.log("Image for game: " + map.getName() + " Generation took: " + time + " ms");
        return jpgFile;
    }

    private Player getFowPlayer(Map map, @Nullable SlashCommandInteractionEvent event) {
        String user = event.getUser().getId();
        return map.getPlayer(user);
    }

    private Set<String> fowFilter(Map map) {
        Set<String> tilesWithPlayerUnitsPlanets = new HashSet<>();
        if (fowPlayer != null) {
            // Get all tiles with the player in it
            for (java.util.Map.Entry<String, Tile> tileEntry : tilesToDisplay.entrySet()) {
                if (FoWHelper.playerIsInSystem(map, tileEntry.getValue(), fowPlayer)) {
                    tilesWithPlayerUnitsPlanets.add(tileEntry.getKey());
                }
            }

            Set<String> tileIDsToShow = new HashSet<>(tilesWithPlayerUnitsPlanets);
            for (String tileID : tilesWithPlayerUnitsPlanets) {
                Set<String> adjacentTiles = FoWHelper.getAdjacentTiles(map, tileID, fowPlayer);
                tileIDsToShow.addAll(adjacentTiles);
            }
            return tileIDsToShow;
        }
        return Collections.emptySet();
    }

    private void updatePlayerFogTiles(Map map, Player player, Set<String> tileKeys) {
        for (String key_ : tileKeys) {
            Tile tileToUpdate = map.getTileByPosition(key_);

            if (tileToUpdate != null) {
                player.updateFogTile(tileToUpdate, "Round " + map.getRound());
            }
        }
    }

    @NotNull
    public static String getTimeStamp() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd - HH.mm.ss");
        return ZonedDateTime.now(ZoneOffset.UTC).format(fmt);

    }

    @Nullable
    private String getFactionPath(String factionID) {
        if (factionID.equals("null")) {
            return null;
        }
        String factionFileName = Mapper.getFactionFileName(factionID);
        String factionFile = ResourceHelper.getInstance().getFactionFile(factionFileName);
        if (factionFile == null) {
            BotLogger.log("Could not find faction: " + factionID);
        }
        return factionFile;
    }

    private void gameInfo(Map map, DisplayType displayType) throws IOException {
        int widthOfLine = width - 50;
        int y = heightForGameInfo + 60;
        int x = 10;
        HashMap<String, Player> players = map.getPlayers();
        int deltaY = 35;
        int yDelta = 0;

        graphics.setFont(Storage.getFont50());
        graphics.setColor(Color.WHITE);
        graphics.drawString(map.getCustomName(), 0, y);

        y = strategyCards(map, y);

        int tempY = y;
        userVPs = new HashMap<>();
        y = objectives(map, y + 180, graphics, userVPs, false);
        y = laws(map, y);
        tempY = scoreTrack(map, tempY + 20);
        if (displayType != DisplayType.stats) {
            playerInfo(map);
        }

        if (displayType == DisplayType.all || displayType == DisplayType.stats) {
            graphics.setFont(Storage.getFont32());
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(new BasicStroke(5));
            int realX = x;
            HashMap<String, Integer> unitCount = new HashMap<>();
            for (Player player : players.values()) {
                int baseY = y;
                x = realX;

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
                if (convertToGeneric) {
                    continue;
                }

                y += 34;
                graphics.setFont(Storage.getFont32());
                Color color = getColor(player.getColor());
                graphics.setColor(Color.WHITE);
                String userName = player.getUserName() + ("null".equals(player.getColor()) ? "" : " (" + player.getColor() + ")");
                graphics.drawString(userName, x, y);
                if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
                    continue;
                }

                y += 2;
                String faction = player.getFaction();
                if (faction != null) {
                    String factionPath = getFactionPath(faction);
                    if (factionPath != null) {
                        BufferedImage bufferedImage = ImageIO.read(new File(factionPath));
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
                if (player.isPassed()) {
                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(new Color(238, 58, 80));
                    graphics.drawString("PASSED", x + 5, y + 95 + yDelta);
                }

                graphics.setFont(Storage.getFont32());
                graphics.setColor(Color.WHITE);
                String ccCount = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                x += 120;
                graphics.drawString(ccCount, x + 40, y + deltaY + 40);
                if (!player.getMahactCC().isEmpty()) {
                    graphics.drawString("+" + player.getMahactCC().size() + " FS", x + 40, y + deltaY + 70);
                }
                graphics.drawString("T/F/S", x + 40, y + deltaY);

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
                graphics.drawString(Integer.toString(player.getTg()), x + 360, y + deltaY + 50);

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

                int soCount = objectivesSO(map, yPlayArea + 150, player);

                int xDeltaSecondRow = xDelta;
                int yPlayAreaSecondRow = yPlayArea + 160;
                if (!player.getPlanets().isEmpty()) {
                    xDeltaSecondRow = planetInfo(player, map, xDeltaSecondRow, yPlayAreaSecondRow);
                }

                reinforcements(player, map, width - 450, yPlayAreaSecondRow, unitCount);

                if (!player.getLeaders().isEmpty()) {
                    xDelta = leaderInfo(player, xDelta, yPlayArea, map);
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
                if (soCount > 4) {
                    y += (soCount - 4) * 43;
                }
                g2.drawRect(realX - 5, baseY, x + widthOfLine, y - baseY);
                y += 15;

            }
            y += 40;
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

            boolean commanderUnlocked = false;
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
                        Leader leader = player_.getLeader(Constants.COMMANDER);
                        if (leader != null) {
                            commanderUnlocked = !leader.isLocked();
                        }
                        break;
                    }
                }
            }

            if (pn.endsWith("_sftt")) {
                pn = "sftt";
            } else if (pn.endsWith("_an")) {
                pn = "alliance";
                if (!commanderUnlocked) {
                    pn += "_exh";
                }
            }

            String pnName = "pa_pn_name_" + pn + ".png";
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

    private int leaderInfo(Player player, int x, int y, Map map) {
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

            if (leader.getTgCount() != 0) {
                graphics.setColor(new Color(241, 176, 0));
                graphics.setFont(Storage.getFont32());
                graphics.drawString(Integer.toString(leader.getTgCount()), x + deltaX + 3, y + 32);
            } else {
                String pipID;
                switch (leader.getId()) {
                    case Constants.AGENT -> pipID = "i";
                    case Constants.COMMANDER -> pipID = "ii";
                    case Constants.HERO -> pipID = "iii";
                    default -> pipID = "";
                }

                if (!pipID.isEmpty()) {
                    String leaderPipInfo = "pa_leaders_pips_" + pipID;
                    if (!isExhaustedLocked && leader.isActive()) {
                        leaderPipInfo += "_active" + ".png";
                    } else {
                        leaderPipInfo += status + ".png";
                    }
                    drawPAImage(x + deltaX, y, leaderPipInfo);
                }
            }

            String extraInfo = leader.getName().isEmpty() ? "" : "_" + leader.getName();
            String leaderInfoFileName = "pa_leaders_" + leader.getId() + "_" + player.getFaction() + extraInfo + status + ".png";
            drawPAImage(x + deltaX, y, leaderInfoFileName);
            deltaX += 48;
            if (leader.getId().equals(Constants.COMMANDER) && player.getFaction().equals("mahact")) {
                List<String> mahactCCs = player.getMahactCC();

                Collection<Player> players = map.getPlayers().values();
                for (Player player_ : players) {
                    if (player_ != player) {
                        String playerColor = player_.getColor();
                        String playerFaction = player_.getFaction();
                        if (playerColor != null && mahactCCs.contains(playerColor)) {
                            Leader leader_ = player_.getLeader(Constants.COMMANDER);
                            if (leader_ != null) {
                                boolean locked = leader_.isLocked();
                                String imperiaColorFile = "pa_leaders_imperia";
                                if (locked) {
                                    imperiaColorFile += "_exh";
                                } else {
                                    imperiaColorFile += "_rdy";
                                }
                                imperiaColorFile += ".png";
                                String leaderFileName_ = "pa_leaders_factionicon_" + playerFaction + "_rdy.png";
                                graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
                                drawPAImage(x + deltaX, y, leaderFileName_);

                                drawPAImage(x + deltaX, y, imperiaColorFile);
                                String status_ = locked ? "_exh" : "_rdy";
                                String leaderPipInfo = "pa_leaders_pips_ii" + status_ + ".png";
                                drawPAImage(x + deltaX, y, leaderPipInfo);
                                deltaX += 48;
                            }
                        }
                    }
                }
            }
        }
        return x + deltaX + 20;
    }

    private void reinforcements(Player player, Map map, int x, int y, HashMap<String, Integer> unitCount) {
        HashMap<String, Tile> tileMap = map.getTileMap();
        drawPAImage(x, y, "pa_reinforcements.png");
        if (unitCount.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    HashMap<String, Integer> units = unitHolder.getUnits();
                    for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                        String key = unitEntry.getKey();
                        Integer count = unitCount.get(key);
                        if (count == null) {
                            count = 0;
                        }
                        if (key.contains("gf") || key.contains("ff")) {
                            count++;
                        } else {
                            count += unitEntry.getValue();
                        }
                        unitCount.put(key, count);
                    }
                }
            }
        }

        String playerColor = player.getColor();
        for (String unitID : Mapper.getUnitIDList()) {
            String unitColorID = Mapper.getUnitID(unitID, playerColor);
            if (unitID.equals("cff")) {
                unitColorID = Mapper.getUnitID("ff", playerColor);
            }
            if (unitID.equals("cgf")) {
                unitColorID = Mapper.getUnitID("gf", playerColor);
            }

            Integer count = unitCount.get(unitColorID);
            if (unitID.equals("csd")) {
                if (!player.getFaction().equals("cabal")) {
                    continue;
                }
                unitColorID = Mapper.getUnitID("sd", playerColor);
            }
            if (player.getFaction().equals("cabal") && unitID.equals("sd")) {
                continue;
            }

            if (count == null) {
                count = 0;
            }
            UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(unitID);
            if (reinforcementsPosition != null) {
                int positionCount = reinforcementsPosition.getPositionCount(unitID);
                int remainingReinforcements = positionCount - count;
                if (remainingReinforcements > 0) {
                    for (int i = 0; i < remainingReinforcements; i++) {
                        try {
                            String unitPath = ResourceHelper.getInstance().getUnitFile(unitColorID);
                            BufferedImage image = ImageIO.read(new File(unitPath));
                            Point position = reinforcementsPosition.getPosition(unitID);
                            graphics.drawImage(image, x + position.x, y + position.y, null);
                        } catch (Exception e) {
                            BotLogger.log("Could not parse unit file for reinforcements: " + unitID);
                        }
                    }
                }
                if (-5 <= remainingReinforcements) paintNumber(unitID, x, y, remainingReinforcements, playerColor);
            }
        }

        int ccCount = Helper.getCCCount(map, playerColor);
        String CC_TAG = "cc";
        if (playerColor == null) {
            return;
        }
        UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(CC_TAG);
        if (reinforcementsPosition != null) {
            int positionCount = reinforcementsPosition.getPositionCount(CC_TAG);
            int remainingReinforcements = positionCount - ccCount;
            if (remainingReinforcements > 0) {
                for (int i = 0; i < remainingReinforcements; i++) {
                    try {
                        String ccID = Mapper.getCCID(playerColor);
                        String ccPath = Mapper.getCCPath(ccID);
                        BufferedImage image = ImageIO.read(new File(ccPath));
                        Point position = reinforcementsPosition.getPosition(CC_TAG);
                        graphics.drawImage(image, x + position.x, y + position.y, null);
                    } catch (Exception e) {
                        BotLogger.log("Could not parse file for CC: " + playerColor);
                    }
                }
            }
            if (-5 <= remainingReinforcements) paintNumber(CC_TAG, x, y, remainingReinforcements, playerColor);
        }

    }

    private void paintNumber(String unitID, int x, int y, int reinforcementsCount, String color) {
        String id = "number_" + unitID;
        UnitTokenPosition textPosition = PositionMapper.getReinforcementsPosition(id);
        String text = "pa_reinforcements_numbers_" + reinforcementsCount;
        String colorID = Mapper.getColorID(color);
        if (colorID.startsWith("ylw") || colorID.startsWith("gry") || colorID.startsWith("org") || colorID.startsWith("pnk")) {
            text += "_blk.png";
        } else {
            text += "_wht.png";

        }
        if (textPosition == null) {
            return;
        }
        Point position = textPosition.getPosition(id);
        drawPAImage(x + position.x, y + position.y, text);
    }

    private int planetInfo(Player player, Map map, int x, int y) {
        HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
        List<String> planets = player.getPlanets();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        int deltaX = 0;
        //RESOURCE/INFLUENCE TOTALS
        int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, map);
        int totalPlayerResources = Helper.getPlayerResourcesTotal(player, map);
        int availablePlayerResourcesOptimal = Helper.getPlayerOptimalResourcesAvailable(player, map);
        int totalPlayerResourcesOptimal = Helper.getPlayerOptimalResourcesTotal(player, map);
        int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, map);
        int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, map);
        int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, map);
        int totalPlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceTotal(player, map);
        drawPAImage(x + deltaX, y - 2, "pa_resinf_info.png");
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(availablePlayerResources), new Rectangle(x + deltaX + 34, y + 7, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(totalPlayerResources), new Rectangle(x + deltaX + 34, y + 41, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(availablePlayerResourcesOptimal), new Rectangle(x + deltaX + 34, y + 75, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(totalPlayerResourcesOptimal), new Rectangle(x + deltaX + 34, y + 109, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(availablePlayerInfluence), new Rectangle(x + deltaX + 185, y + 7, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(totalPlayerInfluence), new Rectangle(x + deltaX + 185, y + 41, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(availablePlayerInfluenceOptimal), new Rectangle(x + deltaX + 185, y + 75, 32, 32), Storage.getFont32());
        drawCenteredString(graphics, String.valueOf(totalPlayerInfluenceOptimal), new Rectangle(x + deltaX + 185, y + 109, 32, 32), Storage.getFont32());
        deltaX += 254;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        boolean randomizeList = player != fowPlayer && isFoWPrivate != null && isFoWPrivate;
        if (randomizeList) {
            Collections.shuffle(planets);
        }
        for (String planet : planets) {
            try {
                UnitHolder unitHolder = planetsInfo.get(planet);
                if (!(unitHolder instanceof Planet planetHolder)) {
                    BotLogger.log(map.getName() + ": Planet unitHolder not found: " + planet);
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

                if (unitHolder.getTokenList().contains(Constants.ATTACHMENT_TITANSPN_PNG)) {
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

                boolean hasAbility = planetHolder.isHasAbility() || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge"));
                if (hasAbility) {
                    String statusOfAbility = exhaustedPlanetsAbilities.contains(planet) ? "_exh" : "_rdy";
                    String planetTypeName = "pc_legendary" + statusOfAbility + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 60, planetTypeName);
                }
                String originalTechSpeciality = planetHolder.getOriginalTechSpeciality();
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
                    }
                }


                drawPlanetImage(x + deltaX + 26, y + 103, resFileName);
                drawPlanetImage(x + deltaX + 26, y + 125, infFileName);
                drawPlanetImage(x + deltaX, y, planetFileName);


                deltaX += 56;
            } catch (Exception e) {
                BotLogger.log("could not print out planet: " + planet.toLowerCase());
            }
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
            if (techList == null) {
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
                        return (t1 < t2) ? -1 : ((t1 == t2) ? (tech1Info[0].compareTo(tech2Info[0])) : 1);
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


        deltaX = techField(x, y, techsFiltered.get(Constants.PROPULSION), exhaustedTechs, techInfo, deltaX);
        deltaX = techField(x, y, techsFiltered.get(Constants.WARFARE), exhaustedTechs, techInfo, deltaX);
        deltaX = techField(x, y, techsFiltered.get(Constants.CYBERNETIC), exhaustedTechs, techInfo, deltaX);
        deltaX = techField(x, y, techsFiltered.get(Constants.BIOTIC), exhaustedTechs, techInfo, deltaX);
        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), exhaustedTechs, techInfo, deltaX, player, map);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, HashMap<String, String[]> techInfo, int deltaX) {
        if (techs == null) {
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

            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + techStatus;
                drawPAImage(x + deltaX, y, techSpec);
            }

            if (techInformation.length >= 4 && !techInformation[3].isEmpty()) {
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
        if ("nomad".equals(player.getFaction())) {
            outline = "pa_tech_unitsnew_outlines_nomad.png";
        }
        if ("nekro".equals(player.getFaction())) {
            for (Player player_ : map.getPlayers().values()) {
                if ("nomad".equals(player_.getFaction())) {
                    outline = "pa_tech_unitsnew_outlines_nomad.png";
                    break;
                }
            }
        }
        drawPAImage(x + deltaX, y, outline);
        if (techs == null) {
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x + deltaX - 2, y - 2, 224, 152);
            deltaX += 228;
            return deltaX;
        }
        for (String tech : techs) {
            String[] techInformation = techInfo.get(tech);

            String unit = "pa_tech_unitsnew_" + Mapper.getColorID(player.getColor()) + "_";
            if (techInformation.length >= 5) {
                unit += techInformation[4] + ".png";
            } else {
                if (tech.equals("dt2")) {
                    unit += "sd2.png";
                } else {
                    unit += tech + ".png";
                }
            }
            drawPAImage(x + deltaX, y, unit);
            if (techInformation.length >= 4 && !techInformation[3].isEmpty()) {
                String factionIcon = "pa_tech_unitsnew_" + techInformation[3] + "_" + tech + ".png";
                drawPAImage(x + deltaX, y, factionIcon);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 224, 152);
        deltaX += 228;
        return deltaX;
    }

    private void drawGeneralImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getGeneralFile(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display General image: " + resourceName);
        }
    }

    private void drawPlanetImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display planet: " + resourceName);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName);
        }
    }

    private int scoreTrack(Map map, int y) {

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(5));
        graphics.setFont(Storage.getFont50());
        int height = 140;
        int width = 150;
        if (14 < map.getVp()) {
            width = 120;
        }
        for (int i = 0; i <= map.getVp(); i++) {
            graphics.setColor(Color.WHITE);
            graphics.drawString(Integer.toString(i), i * width + 55, y + (height / 2) + 25);
            g2.setColor(Color.RED);
            g2.drawRect(i * width, y, width, height);
        }

        List<Player> players = new ArrayList<>(map.getPlayers().values());
        int tempCounter = 0;
        int tempX = 0;
        int tempWidth = 0;
        BufferedImage factionImage = null;

        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }
        for (Player player : players) {
            if (!player.isActivePlayer()) continue;
            try {
                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
                String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());
                String faction = null;
                if (player.getColor() != null && player.getFaction() != null) {
                    String playerControlMarker = Mapper.getControlID(player.getColor());
                    if (controlID.equals(playerControlMarker)) {
                        faction = player.getFaction();
                    }
                }

                factionImage = null;
                float scale = 0.7f;
                if (!convertToGeneric) {
                    if (faction != null) {
                        String factionImagePath = Mapper.getCCPath("control_faction_" + faction + ".png");
                        if (factionImagePath != null) {
                            factionImage = resizeImage(ImageIO.read(new File(factionImagePath)), scale);
                        }
                    }
                }


                BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), scale);
                tempWidth = bufferedImage.getWidth();
                Integer vpCount = userVPs.get(player);
                if (vpCount == null) {
                    vpCount = 0;
                }
                int x = vpCount * width + 5 + tempX;
                graphics.drawImage(bufferedImage, x, y + (tempCounter * bufferedImage.getHeight()), null);
                if (!convertToGeneric) {
                    graphics.drawImage(factionImage, x, y + (tempCounter * bufferedImage.getHeight()), null);
                }

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
            Collection<Player> activePlayers = map.getPlayers().values().stream().filter(player_ -> player_.isActivePlayer()).toList();
            for (Player player_ : activePlayers) {
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
        boolean convertToGenericSC = isFoWPrivate != null && isFoWPrivate;
        y += 80;
        LinkedHashMap<Integer, Integer> scTradeGoods = map.getScTradeGoods();
        Collection<Player> players = map.getPlayers().values();
        Set<Integer> scPicked = players.stream().map(Player::getSC).collect(Collectors.toSet());
        HashMap<Integer, Boolean> scPlayed = map.getScPlayed();
        int x = 20;
        for (java.util.Map.Entry<Integer, Integer> scTGs : scTradeGoods.entrySet()) {
            Integer sc = scTGs.getKey();
            if (sc == 0) {
                continue;
            }
            if (!convertToGenericSC && !scPicked.contains(sc)) {
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
            if (convertToGenericSC && scPlayed.getOrDefault(sc, false)) {
                graphics.setColor(Color.GRAY);
                graphics.setFont(Storage.getFont64());
                graphics.drawString(Integer.toString(sc), x, y);
            }
            x += 80;
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        graphics.drawString("ROUND: " + map.getRound(), x + 100, y);

        return y + 40;
    }

    private void playerInfo(Map map) {
        int playerPosition = 1;
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        Player speaker = map.getPlayer(map.getSpeaker());
        List<Player> players = new ArrayList<>(map.getPlayers().values());
        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }

        for (Player player : players) {
            ArrayList<Point> points = PositionMapper.getPlayerPosition(playerPosition, map);
            if (points.isEmpty()) {
                continue;
            }
            String userName = player.getUserName();

            boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
            if (convertToGeneric) {
                continue;
            }
            graphics.drawString(userName.substring(0, Math.min(userName.length(), 11)), points.get(0).x, points.get(0).y);
            Integer vpCount = userVPs.get(player);
            vpCount = vpCount == null ? 0 : vpCount;
            graphics.drawString("VP - " + vpCount, points.get(1).x, points.get(1).y);

            int totalSecrets = player.getSecrets().keySet().size();
            Set<String> soSet = player.getSecretsScored().keySet();
            int soOffset = 0;

            String soHand = "pa_so-icon_hand.png";
            String soScored = "pa_so-icon_scored.png";
            for (int i = 0; i < totalSecrets; i++) {
                drawPAImage((points.get(6).x + soOffset), points.get(6).y, soHand);
                soOffset += 25;
            }
            ArrayList<String> soToPoList = map.getSoToPoList();
            for (String soID : soSet) {
                if (!soToPoList.contains(soID)) {
                    drawPAImage((points.get(6).x + soOffset), points.get(6).y, soScored);
                    soOffset += 25;
                }
            }


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
            drawCCOfPlayer(ccID, x, y, player.getTacticalCC(), false, null, map);
//            drawCCOfPlayer(fleetCCID, x, y + 65, player.getFleetCC(), "letnev".equals(player.getFaction()));
            drawCCOfPlayer(fleetCCID, x, y + 65, player.getFleetCC(), false, player, map);
            drawCCOfPlayer(ccID, x, y + 130, player.getStrategicCC(), false, null, map);

            if (player == speaker) {
                String speakerID = Mapper.getTokenID(Constants.SPEAKER);
                String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
                if (speakerFile != null) {
                    BufferedImage bufferedImage = null;
                    try {
                        bufferedImage = ImageIO.read(new File(speakerFile));
                    } catch (IOException e) {
                        BotLogger.log("Could not read speaker file");
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

    private boolean canSeeStatsOfFaction(Map map, String faction, Player viewingPlayer) {
        for (Player player : map.getPlayers().values()) {
            if (faction.equals(player.getFaction())) {
                return canSeeStatsOfPlayer(player, viewingPlayer);
            }
        }
        return false;
    }

    private boolean canSeeStatsOfPlayer(Player player, Player viewingPlayer) {
        if (player == viewingPlayer) {
            return true;
        }

        return viewingPlayer != null && player != null &&
                (hasHomeSystemInView(player)
                        || hasPlayersPromInPlayArea(player, viewingPlayer)
                        || hasMahactCCInFleet(player, viewingPlayer));
    }

    private boolean hasHomeSystemInView(@NotNull Player player) {
        String faction = player.getFaction();
        if (faction == null) {
            return false;
        }

        List<String> hsIDs = new ArrayList<>();
        if ("keleres".equals(faction)) {
            hsIDs.add("92");
            hsIDs.add("93");
            hsIDs.add("94");
        } else if ("ghost".equals(faction)) {
            hsIDs.add("51");
        } else {
            String playerSetup = Mapper.getPlayerSetup(faction);
            if (playerSetup != null) {
                String[] setupInfo = playerSetup.split(";");
                hsIDs.add(setupInfo[1]);
            }
        }

        for (Tile tile : tilesToDisplay.values()) {
            if (hsIDs.contains(tile.getTileID()) && !tile.hasFog()) {
                return true;
            }
        }

        return false;
    }

    private boolean hasPlayersPromInPlayArea(@NotNull Player player, @NotNull Player viewingPlayer) {
        boolean hasPromInPA = false;
        String playerColor = player.getColor();
        String playerFaction = player.getFaction();
        List<String> promissoriesInPlayArea = viewingPlayer.getPromissoryNotesInPlayArea();
        for (String prom_ : promissoriesInPlayArea) {
            String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(prom_);
            if (playerColor != null && playerColor.equals(promissoryNoteOwner) || playerFaction != null && playerFaction.equals(promissoryNoteOwner)) {
                hasPromInPA = true;
                break;
            }
        }
        return hasPromInPA;
    }

    private boolean hasMahactCCInFleet(@NotNull Player player, @NotNull Player viewingPlayer) {
        List<String> mahactCCs = viewingPlayer.getMahactCC();
        return mahactCCs.contains(player.getColor());
    }

    private void drawCCOfPlayer(String ccID, int x, int y, int ccCount, boolean isLetnev, Player player, Map map) {
        String ccPath = Mapper.getCCPath(ccID);
        try {
            String faction = getFactionByControlMarker(map.getPlayers().values(), ccID);
            BufferedImage factionImage = null;
            if (faction != null) {
                String factionImagePath = Mapper.getCCPath("control_faction_" + faction + ".png");
                if (factionImagePath != null) {
                    factionImage = ImageIO.read(new File(factionImagePath));
                }
            }

            BufferedImage ccImage = ImageIO.read(new File(ccPath));
            int delta = 20;
            if (isLetnev) {
                for (int i = 0; i < 2; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                    if (factionImage != null) {
                        graphics.drawImage(factionImage, x + (delta * i) + DELTA_X, y + DELTA_Y, null);
                    }
                }
                x += 20;
                for (int i = 2; i < ccCount + 2; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                    if (factionImage != null) {
                        graphics.drawImage(factionImage, x + (delta * i) + DELTA_X, y + DELTA_Y, null);
                    }
                }
            } else {
                int lastCCPosition = -1;
                for (int i = 0; i < ccCount; i++) {
                    graphics.drawImage(ccImage, x + (delta * i), y, null);
                    graphics.drawImage(factionImage, x + (delta * i) + DELTA_X, y + DELTA_Y, null);
                    lastCCPosition = i;
                }
                List<String> mahactCC = player.getMahactCC();
                if (!mahactCC.isEmpty()) {
                    for (String ccColor : mahactCC) {
                        lastCCPosition++;
                        String fleetCCID = Mapper.getCCPath(Mapper.getFleeCCID(ccColor));

                        faction = getFactionByControlMarker(map.getPlayers().values(), fleetCCID);
                        factionImage = null;
                        if (faction != null) {
                            boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
                            if (!convertToGeneric || fowPlayer != null && fowPlayer.getFaction().equals(faction)) {
                                String factionImagePath = Mapper.getCCPath("control_faction_" + faction + ".png");
                                if (factionImagePath != null) {
                                    factionImage = ImageIO.read(new File(factionImagePath));
                                }
                            }
                        }

                        BufferedImage ccImageExtra = resizeImage(ImageIO.read(new File(fleetCCID)), 1.0f);
                        graphics.drawImage(ccImageExtra, x + (delta * lastCCPosition), y, null);
                        if (factionImage != null) {
                            graphics.drawImage(factionImage, x + (delta * lastCCPosition) + DELTA_X, y + DELTA_Y, null);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    public int objectives(Map map, int y, Graphics graphics, HashMap<Player, Integer> userVPs, boolean justCalculate) {
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
        LinkedHashMap<String, String> customPublics = customPublicVP.keySet().stream().collect(Collectors.toMap(key -> key, name -> {
            String nameOfPO = Mapper.getSecretObjectivesJustNames().get(name);
            return nameOfPO != null ? nameOfPO : name;
        }, (key1, key2) -> key1, LinkedHashMap::new));
        Set<String> po1 = publicObjectivesState1.keySet();
        Set<String> po2 = publicObjectivesState2.keySet();
        Set<String> customVP = customPublicVP.keySet();
        Set<String> secret = secretObjectives.keySet();

        graphics.setFont(Storage.getFont26());
        graphics.setColor(new Color(230, 126, 34));
        Integer[] column = new Integer[1];
        column[0] = 0;
        x = 5;
        int y1 = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState1, po1, 1, column, null, justCalculate, false, graphics, userVPs);

        column[0] = 1;
        x = 801;
        graphics.setColor(new Color(93, 173, 226));
        int y2 = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState2, po2, 2, column, null, justCalculate, false, graphics, userVPs);

        column[0] = 2;
        x = 1598;
        graphics.setColor(Color.WHITE);
        int y3 = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, customPublics, customVP, null, column, customPublicVP, justCalculate, false, graphics, userVPs);

        revealedPublicObjectives = new LinkedHashMap<>();
        scoredPublicObjectives = new LinkedHashMap<>();
        for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
            Player player = playerEntry.getValue();
            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
            for (String id : map.getSoToPoList()) {
                secretsScored.remove(id);
            }
            revealedPublicObjectives.putAll(secretsScored);
            for (String id : secretsScored.keySet()) {
                scoredPublicObjectives.put(id, List.of(player.getUserID()));
            }
        }

        graphics.setColor(Color.RED);
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, secretObjectives, secret, 1, column, customPublicVP, true, false, graphics, userVPs);
        if (column[0] != 0) {
            y += 40;
        }

        graphics.setColor(Color.green);
        displaySftT(y, x, players, column, graphics);

        return Math.max(y3, Math.max(y1, y2)) + 15;
    }

    private int laws(Map map, int y) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));


        LinkedHashMap<String, Integer> laws = map.getLaws();
        LinkedHashMap<String, String> lawsInfo = map.getLawsInfo();
        boolean secondColumn = false;
        for (java.util.Map.Entry<String, Integer> lawEntry : laws.entrySet()) {
            String lawID = lawEntry.getKey();
            String lawNumberID = " (" + lawEntry.getValue() + ")";
            String optionalText = lawsInfo.get(lawID);
            graphics.setFont(Storage.getFont35());
            graphics.setColor(new Color(228, 255, 0));

            graphics.drawRect(x, y, 1178, 110);
            String agendaTitle = Mapper.getAgendaTitle(lawID);
            if (agendaTitle == null) {
                agendaTitle = Mapper.getAgendaJustNames().get(lawID);
            }
            graphics.drawString(agendaTitle, x + 95, y + 30);
            graphics.setFont(Storage.getFont26());
            graphics.setColor(Color.WHITE);

            String agendaText = Mapper.getAgendaText(lawID);
            if (agendaText == null) {
                agendaText = Mapper.getAgendaForOnly(lawID);
            }
            agendaText += lawNumberID;
            int width = g2.getFontMetrics().stringWidth(agendaText);

            int index = 0;
            int agendaTextLength = agendaText.length();
            while (width > 1076) {
                index++;
                String substringText = agendaText.substring(0, agendaTextLength - index);
                width = g2.getFontMetrics().stringWidth(substringText);
            }
            if (index > 0) {
                graphics.drawString(agendaText.substring(0, agendaTextLength - index), x + 95, y + 70);
                graphics.drawString(agendaText.substring(agendaTextLength - index), x + 95, y + 96);
            } else {
                graphics.drawString(agendaText, x + 95, y + 70);
            }
            try {
                String agendaType = Mapper.getAgendaType(lawID);

                if (agendaType.equals("1") || optionalText == null || optionalText.isEmpty()) {
                    paintAgendaIcon(y, x);
                } else if (agendaType.equals("0")) {
                    String faction = null;
                    boolean convertToGeneric = false;
                    for (Player player : map.getPlayers().values()) {
                        if (optionalText.equals(player.getFaction()) || optionalText.equals(player.getColor())) {
                            if (isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer)) {
                                convertToGeneric = true;
                            }
                            faction = player.getFaction();
                            break;
                        }
                    }
                    if (faction == null) {
                        paintAgendaIcon(y, x);
                    } else {
                        String factionPath = convertToGeneric ? Mapper.getCCPath(Mapper.getControlID("gray")) : getFactionPath(faction);
                        if (factionPath != null) {
                            BufferedImage bufferedImage = ImageIO.read(new File(factionPath));
                            graphics.drawImage(bufferedImage, x + 2, y + 2, null);
                        }
                    }
                }

            } catch (Exception e) {
                BotLogger.log("Could not paint agenda icon");
            }

            if (!secondColumn) {
                secondColumn = true;
                x += 1178 + 8;
            } else {
                secondColumn = false;
                y += 112;
                x = 5;
            }
        }
        return secondColumn ? y + 115 : y + 3;
    }

    private void paintAgendaIcon(int y, int x) throws IOException {
        String factionFile = ResourceHelper.getInstance().getFactionFile("agenda.png");
        if (factionFile != null) {
            BufferedImage bufferedImage = ImageIO.read(new File(factionFile));
            graphics.drawImage(bufferedImage, x + 2, y + 2, null);
        }
    }

    private int objectivesSO(Map map, int y, Player player) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));
        userVPs = new HashMap<>();

        LinkedHashMap<String, Player> players = map.getPlayers();
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        LinkedHashMap<String, Integer> customPublicVP = map.getCustomPublicVP();
        Set<String> secret = secretObjectives.keySet();
        graphics.setFont(Storage.getFont26());
        graphics.setColor(new Color(230, 126, 34));
        Integer[] column = new Integer[1];
        column[0] = 0;

        LinkedHashMap<String, Integer> revealedPublicObjectives = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> secrets = new LinkedHashMap<>(player.getSecrets());


        for (String id : secrets.keySet()) {
            scoredPublicObjectives.put(id, List.of(player.getUserID()));
        }
        if (player.isSearchWarrant()) {
            LinkedHashMap<String, Integer> revealedSecrets = new LinkedHashMap<>();
            graphics.setColor(Color.LIGHT_GRAY);
            revealedSecrets.putAll(secrets);
            y = displayObjectives(y, x, new LinkedHashMap<>(), revealedSecrets, players, secretObjectives, secret, 0, column, customPublicVP, false, true, graphics, userVPs);
        }
        LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : map.getSoToPoList()) {
            secretsScored.remove(id);
        }
        revealedPublicObjectives.putAll(secretsScored);
        for (String id : secretsScored.keySet()) {
            scoredPublicObjectives.put(id, List.of(player.getUserID()));
        }
        graphics.setColor(Color.RED);
        y = displayObjectives(y, x, scoredPublicObjectives, revealedPublicObjectives, players, secretObjectives, secret, 1, column, customPublicVP, false, true, graphics, userVPs);
        if (player.isSearchWarrant()) {
            return secretsScored.keySet().size() + player.getSecrets().keySet().size();
        } else {
            return secretsScored.keySet().size();
        }
    }

    private int displaySftT(int y, int x, LinkedHashMap<String, Player> players, Integer[] column, Graphics graphics) {
        for (Player player : players.values()) {
            List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
            for (String id : promissoryNotesInPlayArea) {
                if (id.endsWith("_sftt")) {
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
                    boolean multiScoring = false;
                    drawScoreControlMarkers(x + 515, y, players, Collections.singletonList(player.getUserID()), multiScoring, 1, true, graphics, userVPs);
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

    private int displayObjectives(
            int y,
            int x,
            LinkedHashMap<String, List<String>> scoredPublicObjectives,
            LinkedHashMap<String, Integer> revealedPublicObjectives,
            LinkedHashMap<String, Player> players,
            HashMap<String, String> publicObjectivesState,
            Set<String> po,
            Integer objectiveWorth,
            Integer[] column,
            LinkedHashMap<String, Integer> customPublicVP,
            boolean justCalculate,
            boolean fixedColumn,
            Graphics graphics,
            HashMap<Player, Integer> userVPs
    ) {
        Set<String> keysToRemove = new HashSet<>();
        for (java.util.Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            if (fixedColumn) {
                x = 50;
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
            if (!justCalculate) {
                if (fixedColumn) {
                    graphics.drawString("(" + index + ") " + name, x, y + 23);
                } else {
                    graphics.drawString("(" + index + ") " + name + " - " + objectiveWorth + " VP", x, y + 23);
                }
            }
            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            boolean multiScoring = Constants.CUSTODIAN.equals(key) || (isFoWPrivate != null && isFoWPrivate);
            if (scoredPlayerID != null) {
                if (fixedColumn) {
                    drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, false, objectiveWorth, justCalculate, true, graphics, userVPs);
                } else {
                    drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, multiScoring, objectiveWorth, justCalculate, graphics, userVPs);
                }
            }
            if (!justCalculate) {
                if (fixedColumn) {
                    graphics.drawRect(x - 4, y - 5, 600, 38);

                } else {
                    graphics.drawRect(x - 4, y - 5, 785, 38);
                }
                y += 43;
            }
        }
        keysToRemove.forEach(revealedPublicObjectives::remove);

        return y;
    }

    private void drawScoreControlMarkers(
            int x,
            int y,
            LinkedHashMap<String, Player> players,
            List<String> scoredPlayerID,
            boolean multiScoring,
            Integer objectiveWorth,
            boolean justCalculate,
            Graphics graphics,
            HashMap<Player, Integer> userVPs
    ) {
        drawScoreControlMarkers(x, y, players, scoredPlayerID, multiScoring, objectiveWorth, justCalculate, false, graphics, userVPs);
    }

    private void drawScoreControlMarkers(
            int x,
            int y,
            LinkedHashMap<String, Player> players,
            List<String> scoredPlayerID,
            boolean multiScoring,
            Integer objectiveWorth,
            boolean justCalculate,
            boolean fixedColumn,
            Graphics graphics,
            HashMap<Player, Integer> userVPs
    ) {
        try {
            int tempX = 0;
            BufferedImage factionImage = null;
            for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
                Player player = playerEntry.getValue();
                String userID = player.getUserID();

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
                if (scoredPlayerID.contains(userID)) {
                    String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());
                    if (controlID.contains("null")) {
                        continue;
                    }
                    factionImage = null;
                    float scale = 0.55f;
                    if (!convertToGeneric) {
                        String faction = getFactionByControlMarker(players.values(), controlID);
                        if (faction != null) {
                            String factionImagePath = Mapper.getCCPath("control_faction_" + faction + ".png");
                            if (factionImagePath != null) {
                                factionImage = resizeImage(ImageIO.read(new File(factionImagePath)), scale);
                            }
                        }
                    }

                    BufferedImage bufferedImage = resizeImage(ImageIO.read(new File(Mapper.getCCPath(controlID))), scale);
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
                                if (!convertToGeneric) {
                                    graphics.drawImage(factionImage, x + tempX, y, null);
                                }
                            }
                            tempX += scoreTokenWidth;
                        }
                    } else {
                        vpCount += objectiveWorth;
                        if (!justCalculate) {
                            graphics.drawImage(bufferedImage, x + tempX, y, null);
                            if (!convertToGeneric) {
                                graphics.drawImage(factionImage, x + tempX, y, null);
                            }
                        }
                    }
                    userVPs.put(player, vpCount);
                }
                if (!multiScoring && !fixedColumn) {
                    tempX += scoreTokenWidth;
                }
            }
        } catch (Exception e) {
            BotLogger.log("Could not parse custodian CV token file");
        }
    }

    private static String getFactionByControlMarker(Collection<Player> players, String controlID) {
        String faction = "";
        for (Player player_ : players) {
            if (player_.getColor() != null && player_.getFaction() != null) {
                String playerControlMarker = Mapper.getControlID(player_.getColor());
                String playerCC = Mapper.getCCID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC)) {
                    faction = player_.getFaction();
                    break;
                }
            }
        }
        return faction;
    }

    private static Player getPlayerByControlMarker(Collection<Player> players, String controlID) {
        Player player = null;
        for (Player player_ : players) {
            if (player_.getColor() != null && player_.getFaction() != null) {
                String playerControlMarker = Mapper.getControlID(player_.getColor());
                String playerCC = Mapper.getCCID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
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

    enum TileStep {
        Setup, Tile, Extras, Units
    }

    private void addTile(Tile tile, Map map, TileStep step) {
        try {
            BufferedImage image = ImageIO.read(new File(tile.getTilePath()));
            BufferedImage fogOfWar = ImageIO.read(new File(tile.getFowTilePath(fowPlayer)));
            boolean tileIsFoggy = isFoWPrivate != null && isFoWPrivate && tile.hasFog();

            Point positionPoint = PositionMapper.getTilePosition(tile.getPosition(), map);
            if (positionPoint == null) {
                throw new Exception("Could not map tile to a position on the map");
            }
            int tileX = positionPoint.x + extraWidth;
            int tileY = positionPoint.y;

            switch (step) {
                case Setup -> {
                } //do nothing
                case Tile -> {
                    graphics.drawImage(image, tileX, tileY, null);

                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(Color.WHITE);
                    if (tileIsFoggy) {
                        graphics.drawImage(fogOfWar, tileX, tileY, null);
                        graphics.drawString(tile.getFogLabel(), tileX + labelPositionPoint.x, tileY + labelPositionPoint.y);
                    }
                    graphics.drawString(tile.getPosition(), tileX + tilePositionPoint.x, tileY + tilePositionPoint.y);
                }
                case Extras -> {
                    if (tileIsFoggy) return;
                    String primaryTile = tile.getPosition();
                    List<String> adj = map.getAdjacentTileOverrides(primaryTile);
                    int direction = 0;
                    for (String secondaryTile : adj) {
                        if (secondaryTile != null) {
                            image = addAdjacencyArrow(tile, direction, secondaryTile, image, tileX, tileY, map);
                        }
                        direction++;
                    }
                }
                case Units -> {
                    if (tileIsFoggy) return;
                    ArrayList<Rectangle> rectangles = new ArrayList<>();

                    Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
                    UnitHolder spaceUnitHolder = unitHolders.stream().filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);
                    if (spaceUnitHolder != null) {
                        image = addSleeperToken(tile, image, tileX, tileY, spaceUnitHolder, GenerateMap::isValidCustodianToken);
                        image = addToken(tile, image, tileX, tileY, spaceUnitHolder);
                        unitHolders.remove(spaceUnitHolder);
                        unitHolders.add(spaceUnitHolder);
                    }
                    for (UnitHolder unitHolder : unitHolders) {
                        image = addSleeperToken(tile, image, tileX, tileY, unitHolder, GenerateMap::isValidToken);
                        image = addControl(tile, image, tileX, tileY, unitHolder, rectangles, map);
                    }
                    if (spaceUnitHolder != null) {
                        image = addCC(tile, image, tileX, tileY, spaceUnitHolder, map);
                    }
                    int degree = 180;
                    int degreeChange = 5;
                    for (UnitHolder unitHolder : unitHolders) {
                        int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                        if (unitHolder != spaceUnitHolder) {
                            image = addPlanetToken(tile, image, tileX, tileY, unitHolder, rectangles);
                        }
                        image = addUnits(tile, image, tileX, tileY, rectangles, degree, degreeChange, unitHolder, radius, map);
                    }
                }
            }
        } catch (IOException e) {
            BotLogger.log("Error drawing tile: " + tile.getTileID());
        } catch (Exception exception) {
            BotLogger.log("Tile Error, when building map: " + tile.getTileID());
        }
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, float percent) throws IOException {
        int scaledWidth = (int) (originalImage.getWidth() * percent);
        int scaledHeight = (int) (originalImage.getHeight() * percent);
        Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT);
        BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    private BufferedImage addAdjacencyArrow(Tile tile, int direction, String secondaryTile, BufferedImage image, int tileX, int tileY, Map map) {
        int deltaX = 0;
        int deltaY = 0;
        int textOffsetX = 0;
        int textOffsetY = 0;
        BufferedImage arrowImage = null;
        switch (direction) {
            case 0 -> {
                deltaX = 128;
                deltaY = -18;
                textOffsetX = 5;
                textOffsetY = 30;
            }
            case 1 -> {
                deltaX = 267;
                deltaY = 36;
                textOffsetX = 6;
                textOffsetY = 21;
            }
            case 2 -> {
                deltaX = 293;
                deltaY = 177;
                textOffsetX = 7;
                textOffsetY = 32;
            }
            case 3 -> {
                deltaX = 177;
                deltaY = 283;
                textOffsetX = 5;
                textOffsetY = 20;
            }
            case 4 -> {
                deltaX = 38;
                deltaY = 220;
                textOffsetX = 5;
                textOffsetY = 30;
            }
            case 5 -> {
                deltaX = 40;
                deltaY = 34;
                textOffsetX = 5;
                textOffsetY = 25;
            }
        }
        try {
            BufferedImage outputImage = ImageIO.read(new File(Helper.getAdjacencyOverridePath(direction)));
            arrowImage = outputImage;

            Graphics arrow = arrowImage.getGraphics();
            arrow.setFont(Storage.getFont20());
            arrow.setColor(Color.BLACK);
            arrow.drawString(secondaryTile, textOffsetX, textOffsetY);
        } catch (Exception e) {
        }
        graphics.drawImage(arrowImage, tileX + deltaX, tileY + deltaY, null);

        return image;
    }

    private BufferedImage addCC(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder, Map map) {
        HashSet<String> ccList = unitHolder.getCCList();
        int deltaX = 0;
        int deltaY = 0;
        for (String ccID : ccList) {
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
//                LoggerHandler.log("Could not parse cc file for: " + ccID);
                continue;
            }
            try {
                image = ImageIO.read(new File(ccPath));

                Point centerPosition = unitHolder.getHolderCenterPosition();

                String faction = getFactionByControlMarker(map.getPlayers().values(), ccID);
                Player player = getPlayerByControlMarker(map.getPlayers().values(), ccID);
                BufferedImage factionImage = null;
                if (faction != null) {
                    boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
                    if (!convertToGeneric || fowPlayer != null && fowPlayer.getFaction().equals(faction)) {
                        String factionImagePath = Mapper.getCCPath("control_faction_" + faction + ".png");
                        if (factionImagePath != null) {
                            factionImage = ImageIO.read(new File(factionImagePath));
                        }
                    }
                }
                graphics.drawImage(image, tileX + 10 + deltaX, tileY + centerPosition.y - 40 + deltaY, null);
                if (factionImage != null) {
                    graphics.drawImage(factionImage, tileX + 10 + deltaX + DELTA_X, tileY + centerPosition.y - 40 + deltaY + DELTA_Y, null);
                }
            } catch (Exception e) {
//                LoggerHandler.log("Could not parse cc file for: " + ccID, e);
            }


            deltaX += image.getWidth() / 5;
            deltaY += image.getHeight() / 4;
        }
        return image;
    }

    private BufferedImage addControl(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder, ArrayList<Rectangle> rectangles, Map map) {
        ArrayList<String> controlList = new ArrayList<>(unitHolder.getControlList());
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        BufferedImage factionImage = null;
        if (unitTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String controlID : controlList) {
                if (controlID.contains(Constants.SLEEPER)) {
                    continue;
                }
                String faction = getFactionByControlMarker(map.getPlayers().values(), controlID);
                Player player = getPlayerByControlMarker(map.getPlayers().values(), controlID);

                String controlPath = tile.getCCPath(controlID);
                if (controlPath == null) {
                    BotLogger.log("Could not parse control token file for: " + controlID);
                    continue;
                }
                try {
                    factionImage = null;
                    if (faction != null) {
                        boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !canSeeStatsOfPlayer(player, fowPlayer);
                        if (!convertToGeneric || fowPlayer != null && fowPlayer.getFaction().equals(faction)) {
                            String factionImagePath = tile.getCCPath("control_faction_" + faction + ".png");
                            if (factionImagePath != null) {
                                factionImage = ImageIO.read(new File(factionImagePath));
                            }
                        }
                    }
                    image = ImageIO.read(new File(controlPath));
                } catch (Exception e) {
                    BotLogger.log("Could not parse control token file for: " + controlID);
                }
                boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
                if (isMirage) {
                    tileX += Constants.MIRAGE_POSITION.x;
                    tileY += Constants.MIRAGE_POSITION.y;
                }
                Point position = unitTokenPosition.getPosition(controlID);
                if (position != null) {
                    graphics.drawImage(image, tileX + position.x, tileY + position.y, null);
                    if (factionImage != null) {
                        graphics.drawImage(factionImage, tileX + position.x, tileY + position.y, null);
                    }
                    rectangles.add(new Rectangle(tileX + position.x, tileY + position.y, image.getWidth(), image.getHeight()));
                } else {
                    graphics.drawImage(image, tileX + centerPosition.x + xDelta, tileY + centerPosition.y, null);
                    if (factionImage != null) {
                        graphics.drawImage(factionImage, tileX + centerPosition.x + xDelta, tileY + centerPosition.y, null);
                    }
                    rectangles.add(new Rectangle(tileX + centerPosition.x + xDelta, tileY + centerPosition.y, image.getWidth(), image.getHeight()));
                    xDelta += 10;
                }
            }
            return image;
        } else {
            return oldFormatPlanetTokenAdd(tile, image, tileX, tileY, unitHolder, controlList);
        }
    }

    private BufferedImage addSleeperToken(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder, Function<String, Boolean> isValid) {
        Point centerPosition = unitHolder.getHolderCenterPosition();
        ArrayList<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.remove(null);
        tokenList.sort((o1, o2) -> {
            if ((o1.contains(Constants.SLEEPER) || o2.contains(Constants.SLEEPER))) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
        for (String tokenID : tokenList) {
            if (isValid.apply(tokenID)) {
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.log("Could not find token file for: " + tokenID);
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
                    BotLogger.log("Could not parse sleeper token file for: " + tokenID);
                }
                Point position = new Point(centerPosition.x - (image.getWidth() / 2), centerPosition.y - (image.getHeight() / 2));
                if (tokenID.contains(Constants.CUSTODIAN_TOKEN)) {
                    position = new Point(70, 45);
                } else if (tokenID.contains(Constants.SLEEPER) && containsDMZ) {
                    position = new Point(position.x + 10, position.y + 10);
                }
                graphics.drawImage(image, tileX + position.x, tileY + position.y - 10, null);
            }
        }
        return image;
    }

    private static boolean isValidToken(String tokenID) {
        return tokenID.contains(Constants.SLEEPER) ||
                tokenID.contains(Constants.DMZ_LARGE) ||
                tokenID.contains(Constants.WORLD_DESTROYED) ||
                tokenID.contains(Constants.CUSTODIAN_TOKEN) ||
                tokenID.contains(Constants.CONSULATE_TOKEN);
    }

    private static boolean isValidCustodianToken(String tokenID) {
        return tokenID.contains(Constants.CUSTODIAN_TOKEN);
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
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition != null) {
            Point centerPosition = unitHolder.getHolderCenterPosition();
            int xDelta = 0;
            for (String tokenID : tokenList) {
                if (isValidToken(tokenID) || isValidCustodianToken(tokenID)) {
                    continue;
                }
                String tokenPath = tile.getTokenPath(tokenID);
                if (tokenPath == null) {
                    BotLogger.log("Could not parse token file for: " + tokenID);
                    continue;
                }
                float scale = 1.00f;
                try {
                    image = resizeImage(ImageIO.read(new File(tokenPath)), scale);
                } catch (Exception e) {
                    BotLogger.log("Could not parse control token file for: " + tokenID);
                }
                if (tokenPath.contains(Constants.DMZ_LARGE) || tokenPath.contains(Constants.WORLD_DESTROYED) || tokenPath.contains(Constants.CONSULATE_TOKEN)) {
                    graphics.drawImage(image, tileX + centerPosition.x - (image.getWidth() / 2), tileY + centerPosition.y - (image.getHeight() / 2), null);
                } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                    graphics.drawImage(image, tileX + 70, tileY + 45, null);
                } else {
                    Point position = unitTokenPosition.getPosition(tokenID);
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
                BotLogger.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(tokenPath)), 0.85f);
            } catch (Exception e) {
                BotLogger.log("Could not parse control token file for: " + tokenID);
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
                BotLogger.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                float scale = tokenPath.contains(Constants.MIRAGE) ? 1.0f : 0.80f;
                image = resizeImage(ImageIO.read(new File(tokenPath)), scale);
            } catch (Exception e) {
                BotLogger.log("Could not parse control token file for: " + tokenID);
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

    private BufferedImage addUnits(Tile tile, BufferedImage image, int tileX, int tileY, ArrayList<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius, Map map) {
        HashMap<String, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        LinkedHashMap<String, Integer> units = new LinkedHashMap<>();
        HashMap<String, Point> unitOffset = new HashMap<>();
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);

        boolean isCabalJail = "s11".equals(tile.getTileID());
        boolean isNekroJail = "s12".equals(tile.getTileID());
        boolean isYssarilJail = "s13".equals(tile.getTileID());

        boolean isJail = isCabalJail || isNekroJail || isYssarilJail;
        boolean showJail = false;
        if (fowPlayer == null
                || (isCabalJail && canSeeStatsOfFaction(map, "cabal", fowPlayer))
                || (isNekroJail && canSeeStatsOfFaction(map, "nekro", fowPlayer))
                || (isYssarilJail && canSeeStatsOfFaction(map, "yssaril", fowPlayer))
        ) {
            showJail = true;
        }

        Point unitOffsetValue = map.isAllianceMode() ? PositionMapper.getAllianceUnitOffset() : PositionMapper.getUnitOffset();
        int spaceX = unitOffsetValue != null ? unitOffsetValue.x : 10;
        int spaceY = unitOffsetValue != null ? unitOffsetValue.y : -7;
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
        UnitTokenPosition unitTokenPosition = PositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            unitTokenPosition = PositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
        }
        BufferedImage dmgImage = null;
        try {
            BufferedImage read = ImageIO.read(new File(Helper.getDamagePath()));
            dmgImage = resizeImage(read, 0.8f);
        } catch (IOException e) {
            BotLogger.log("Could not parse damage token file.");
        }

        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);

        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
            String unitID = unitEntry.getKey();
            Integer unitCount = unitEntry.getValue();

            if (isJail && fowPlayer != null) {
                String colorID = Mapper.getColorID(fowPlayer.getColor());
                if (!showJail && fowPlayer != null && !unitID.startsWith(colorID)) {
                    continue;
                }
            }

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
                String unitPath = Tile.getUnitPath(unitID);
                image = resizeImage(ImageIO.read(new File(unitPath)), scaleOfUnit);
            } catch (Exception e) {
                BotLogger.log("Could not parse unit file for: " + unitID);
            }
            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;
            }


            Point centerPosition = unitHolder.getHolderCenterPosition();

            for (int i = 0; i < unitCount; i++) {
                Point position = unitTokenPosition != null ? unitTokenPosition.getPosition(unitID) : null;
                boolean fighterOrInfantry = false;
                if (unitID.contains("_tkn_ff.png") || unitID.contains("_tkn_gf.png")) {
                    fighterOrInfantry = true;
                }
                if (isSpace && position != null && !fighterOrInfantry) {
                    String id = unitIDToID.get(unitID);
                    if (id == null) {
                        id = unitID.substring(unitID.indexOf("_"));
                    }
                    Point point = unitOffset.get(id);
                    if (point == null) {
                        point = new Point(0, 0);
                    }
                    position.x = position.x + point.x;
                    position.y = position.y + point.y;
                    point.x += spaceX;
                    point.y += spaceY;
                    unitOffset.put(id, point);
                }
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
                    if (isSpace && position != null) {
                        position.x = position.x - 7;
                    }
                    int imageDmgX = position != null ? tileX + position.x + (image.getWidth() / 2) - (dmgImage.getWidth() / 2) : xOriginal - (dmgImage.getWidth() / 2);
                    int imageDmgY = position != null ? tileY + position.y + (image.getHeight() / 2) - (dmgImage.getHeight() / 2) : yOriginal - (dmgImage.getHeight() / 2);
                    if (isMirage) {
                        imageDmgX = imageX;
                        imageDmgY = imageY;
                    } else if (unitID.contains("_mf")) {
                        imageDmgX = position != null ? tileX + position.x : xOriginal - (dmgImage.getWidth());
                        imageDmgY = position != null ? tileY + position.y : yOriginal - (dmgImage.getHeight());

                    }
                    graphics.drawImage(dmgImage, imageDmgX, imageDmgY, null);
                    unitDamageCount--;
                }
            }
        }
        return image;
    }

    /**
     * Draw a String centered in the middle of a Rectangle.
     *
     * @param g    The Graphics instance.
     * @param text The String to draw.
     * @param rect The Rectangle to center the text in.
     */
    public void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        // Get the FontMetrics
        FontMetrics metrics = g.getFontMetrics(font);
        // Determine the X coordinate for the text
        int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        // Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        // Set the font
        g.setFont(font);
        // Draw the String
        g.drawString(text, x, y);
    }
}
