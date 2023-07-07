package ti4.generator;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.ImageProxy;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.MapGenerator;
import ti4.ResourceHelper;
import ti4.helpers.*;
import ti4.map.Map;
import ti4.map.*;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    public static final int RING_MAX_COUNT = 8;
    public static final int PLAYER_STATS_HEIGHT = 650;
    public static final int TILE_PADDING = 100;

    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;
    private int heightStorage;
    private int heightStats;
    private int mapHeight;
    private int mapWidth;
    private int heightForGameInfo;
    private static int scoreTokenWidth = 0;
    private int extraX = 300;
    private int extraY = 200;
    private static Point tilePositionPoint = new Point(230, 295);
    private static Point labelPositionPoint = new Point(90, 295);
    private static Point numberPositionPoint = new Point(40, 27);
    private static HashMap<Player, Integer> userVPs = new HashMap<>();

    private final int width8 = 2500;
    private final int height8 = 3350;
    private final int playerHeight = 340;

    private int minX = -1;
    private int minY = -1;
    private int maxX = -1;
    private int maxY = -1;
    private boolean extraRow = false;

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
            BotLogger.log("Could read file data for setup file", e);
        }
        init(null);
        resetImage();
    }

    private void init(Map activeMap) {
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();

        mapHeight = width8;
        mapWidth = height8;
        heightStats = height8 / 2;
        if (activeMap != null) {
            int stage1 = activeMap.getRevealedPublicObjectives().keySet().stream().filter(obj -> publicObjectivesState1.containsKey(obj)).toList().size();
            int stage2 = activeMap.getRevealedPublicObjectives().keySet().stream().filter(obj -> publicObjectivesState2.containsKey(obj)).toList().size();
            int other = activeMap.getRevealedPublicObjectives().size() - stage1 - stage2;
            int mostObjs = Math.max(Math.max(stage1, stage2), other);
            int objectivesY = Math.max((mostObjs - 5) * 43, 0);
            
            int playerCountForMap = activeMap.getPlayerCountForMap();
            int playerY = playerCountForMap * playerHeight;

            int lawsY = (activeMap.getLaws().keySet().size() / 2 + 1) * 115;
            heightStats = playerY + lawsY + objectivesY + 600;
            
            mapHeight = (activeMap.getRingCount() + 1) * 600 + extraY * 2;
            mapWidth = (activeMap.getRingCount() + 1) * 520 + extraX * 2;
            extraRow = false;
            if ((mapHeight - extraY) < (playerCountForMap / 2 * PLAYER_STATS_HEIGHT + extraY)) {
                mapWidth += extraX;
                extraRow = true;
            }

            if (mapWidth < 2500) {
                mapWidth = 2500;
            }
        }
        width = mapWidth;
        height = mapHeight + heightStats;
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

    public File saveImage(Map activeMap, @Nullable SlashCommandInteractionEvent event) {
        if (activeMap.getDisplayTypeForced() != null) {
            return saveImage(activeMap, activeMap.getDisplayTypeForced(), event);
        }
        return saveImage(activeMap, DisplayType.all, event);
    }

    public File saveImage(Map activeMap, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event) {
        // long startup = System.currentTimeMillis();
        activeMap.incrementMapImageGenerationCount();
        init(activeMap);
        if (activeMap.getDisplayTypeForced() != null) {
            displayType = activeMap.getDisplayTypeForced();
        } else if (displayType == null) {
            displayType = activeMap.getDisplayTypeForced();
            if (displayType == null) {
                displayType = DisplayType.all;
            }
        }
        if (displayType == DisplayType.stats) {
            heightForGameInfo = 40;
            height = heightStats;
        } else if (displayType == DisplayType.map) {
            heightForGameInfo = mapHeight - 400;
            height = mapHeight + 600;
        } else {
            heightForGameInfo = mapHeight;
            height = heightStorage;
        }
        resetImage();

        isFoWPrivate = null;
        fowPlayer = null;
        tilesToDisplay = new HashMap<>(activeMap.getTileMap());
        if (activeMap.isFoWMode() && event != null) {
            isFoWPrivate = false;
            if (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)) {
                isFoWPrivate = true;
                Player player = getFowPlayer(activeMap, event);

                // IMPORTANT NOTE : This method used to be local and was refactored to extract
                // any references to tilesToDisplay
                fowPlayer = Helper.getGamePlayer(activeMap, player, event, null);

                Set<String> tilesToShow = FoWHelper.fowFilter(activeMap, fowPlayer);
                Set<String> keys = new HashSet<>(tilesToDisplay.keySet());
                keys.removeAll(tilesToShow);
                for (String key : keys) {
                    tilesToDisplay.remove(key);
                    tilesToDisplay.put(key, fowPlayer.buildFogTile(key, fowPlayer));
                }
            }
        }
        try {
            if (displayType == DisplayType.all || displayType == DisplayType.map) {
                HashMap<String, Tile> tileMap = new HashMap<>(tilesToDisplay);
                String setup = tileMap.keySet().stream()
                        .filter(key -> key != null && key.equals("0"))
                        .findFirst()
                        .orElse(null);
                if (setup != null) {
                    if (tileMap.get(setup).getTileID().equals("setup")) {
                        int ringCount = activeMap.getRingCount();
                        minX = 10000;
                        minY = 10000;
                        maxX = -1;
                        maxY = -1;
                        Set<String> filledPositions = new HashSet<>();
                        for (String position : PositionMapper.get8RingTiles()) {
                            String tileRing = "0";
                            if (position.length() == 3) {
                                tileRing = position.substring(0, 1);
                            } else if (position.length() == 4) {
                                tileRing = position.substring(0, 2);
                            }
                            int tileRingNumber = -1;
                            try {
                                tileRingNumber = Integer.parseInt(tileRing);
                            } catch (Exception e) {
                                //Do nothing
                            }

                            if (tileRingNumber > -1 && tileRingNumber <= ringCount && !tileMap.containsKey(position)) {
                                addTile(new Tile("0gray", position), activeMap, TileStep.Tile);
                                filledPositions.add(position);
                            }
                        }
                        for (String position : PositionMapper.get8RingTiles()) {
                            if (!tileMap.containsKey(position) || !filledPositions.contains(position)) {
                                addTile(new Tile("0border", position), activeMap, TileStep.Tile, true);
                            }
                        }


                    } else {
                        addTile(tileMap.get(setup), activeMap, TileStep.Tile);
                    }
                    tileMap.remove(setup);
                }

                tileMap.remove(null);
                Set<String> tiles = tileMap.keySet();
                Set<String> tilesWithExtra = new HashSet<String>(activeMap.getAdjacentTileOverrides().values());
                tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), activeMap, TileStep.Tile));
                tilesWithExtra.stream().forEach(key -> addTile(tileMap.get(key), activeMap, TileStep.Extras));
                tiles.stream().sorted().forEach(key -> addTile(tileMap.get(key), activeMap, TileStep.Units));
            }
            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            String timeStamp = getTimeStamp();
            graphics.drawString(activeMap.getName() + " " + timeStamp, 0, 34);
            gameInfo(activeMap, displayType);

            String testing = System.getenv("TESTING");
            if (testing == null && displayType == DisplayType.all && (isFoWPrivate == null || !isFoWPrivate)) {
                new Thread(() -> {
                    WebHelper.putMap(activeMap.getName(), mainImage);
                    WebHelper.putData(activeMap.getName(), activeMap.copy());
                }).start();
            } else if (!(isFoWPrivate==null) && isFoWPrivate) {
                Player player = getFowPlayer(activeMap, event);
                new Thread(() -> {
                        WebHelper.putMap(activeMap.getName(), mainImage, true, player);
                }).start();
            }
        } catch (IOException e) {
            BotLogger.log(activeMap.getName() + ": Could not save generated map");
        }

        String timeStamp = getTimeStamp();
        String absolutePath = Storage.getMapImageDirectory() + "/" + activeMap.getName() + "_" + timeStamp + ".jpg";
        try (
                FileOutputStream fileOutputStream = new FileOutputStream(absolutePath)
        ) {
            final BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(mainImage, 0, 0, Color.black, null);
            final boolean canWrite = ImageIO.write(convertedImage, "jpg", fileOutputStream);
            if (!canWrite) {
                throw new IllegalStateException("Failed to write image.");
            }
        } catch (IOException e) {
            BotLogger.log("Could not save jpg file", e);
        }
        File jpgFile = new File(absolutePath);
        MapFileDeleter.addFileToDelete(jpgFile);
        // long time = System.currentTimeMillis() - startup;
        // BotLogger.log("Image for game: " + map.getName() + " Generation took: " + time + " ms");
        return jpgFile;
    }

    private Player getFowPlayer(Map activeMap, @Nullable GenericInteractionCreateEvent event) {
        if (event == null) return null;
        String user = event.getUser().getId();
        return activeMap.getPlayer(user);
    }

    @NotNull
    public static String getTimeStamp() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd - HH.mm.ss");
        String output = ZonedDateTime.now(ZoneOffset.UTC).format(fmt);
        return output == null ? "" : output;
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

    private Image getPlayerDiscordAvatar(Player player) {
        String userID = player.getUserID();
        Member member = MapGenerator.guildPrimary.getMemberById(userID);
        if (member == null) return null;
        BufferedImage resourceBufferedImage = null;
        Image image = null;
        try {
            ImageProxy avatarProxy = member.getEffectiveAvatar();
            InputStream inputStream = avatarProxy.download().get();
            resourceBufferedImage = ImageIO.read(inputStream);
            image = resourceBufferedImage.getScaledInstance(32, 32, Image.SCALE_FAST);
        } catch (Exception e) {
            BotLogger.log("Could not get Avatar", e);
        }
        return image;
    }

    private void gameInfo(Map activeMap, DisplayType displayType) throws IOException {
        int widthOfLine = width - 50;
        int y = heightForGameInfo + 60;
        int x = 10;
        List<Player> players = new ArrayList<>(activeMap.getPlayers().values());
        int deltaY = 35;
        int yDelta = 0;

        graphics.setFont(Storage.getFont50());
        graphics.setColor(Color.WHITE);
        graphics.drawString(activeMap.getCustomName(), 0, y);

        y = strategyCards(activeMap, y);

        int tempY = y;
        userVPs = new HashMap<>();
        y = objectives(activeMap, y + 180, graphics, userVPs, false);
        y = laws(activeMap, y);
        tempY = scoreTrack(activeMap, tempY + 20);
        if (displayType != DisplayType.stats) {
            playerInfo(activeMap);
        }

        if (displayType == DisplayType.all || displayType == DisplayType.stats) {
            graphics.setFont(Storage.getFont32());
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(new BasicStroke(5));
            int realX = x;
            HashMap<String, Integer> unitCount = new HashMap<>();
            for (Player player : players) {
                int baseY = y;
                x = realX;

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, fowPlayer);
                if (convertToGeneric) {
                    continue;
                }

                graphics.drawImage(getPlayerDiscordAvatar(player), x, y + 5, null);
                y += 34;
                graphics.setFont(Storage.getFont32());
                Color color = getColor(player.getColor());
                graphics.setColor(Color.WHITE);
                String userName = player.getUserName() + ("null".equals(player.getColor()) ? "" : " (" + player.getColor() + ")");
                graphics.drawString(userName, x + 34, y);
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

                //PAINT SCs
                Set<Integer> playerSCs = player.getSCs();
                if (playerSCs.size() == 1) {
                    int sc = playerSCs.stream().findFirst().get();
                    String scText = sc == 0 ? " " : Integer.toString(sc);
                    if (sc != 0) {
                        scText = getSCNumberIfNaaluInPlay(player, activeMap, scText);
                    }
                    graphics.setColor(getSCColor(sc, activeMap));
                    graphics.setFont(Storage.getFont64());

                    if (scText.contains("0/")) {
                        graphics.drawString("0", x + 90, y + 70 + yDelta);
                        graphics.setFont(Storage.getFont32());
                        graphics.setColor(Color.WHITE);
                        graphics.drawString(Integer.toString(sc), x + 120, y + 80 + yDelta);
                    } else {
                        graphics.drawString(scText, x + 90, y + 70 + yDelta);
                    }
                } else { //if (playerSCs.size() <= 4) {
                    int count = 1;
                    int row = 0;
                    int col = 0;
                    for (int sc : playerSCs) {
                        if (count == 5) break;
                        switch (count) {
                            case 2 -> {
                                col = 1;
                            }
                            case 3 -> {
                                row = 1;
                                col = 0;
                            }
                            case 4 -> {
                                row = 1;
                                col = 1;
                            }
                        }
                        String scText = sc == 0 ? " " : Integer.toString(sc);
                        if (sc != 0) {
                            scText = getSCNumberIfNaaluInPlay(player, activeMap, scText);
                        }
                        graphics.setColor(getSCColor(sc, activeMap));

                        if (scText.contains("0/")) {
                            graphics.setFont(Storage.getFont64());
                            graphics.drawString("0", x + 90, y + 70 + yDelta);
                            graphics.setFont(Storage.getFont32());
                            graphics.setColor(Color.WHITE);
                            graphics.drawString(Integer.toString(sc), x + 120, y + 80 + yDelta);
                        } else {
                            drawCenteredString(graphics, scText, new Rectangle(x + 90 + 32 * col, y + 70 - 64 + 32 * row, 32, 32), Storage.getFont32());
                        }
                        count++;
                    }
                }


                String activePlayerID = activeMap.getActivePlayer();
                String phase = activeMap.getCurrentPhase();
                if (player.isPassed()) {
                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(new Color(238, 58, 80));
                    graphics.drawString("PASSED", x + 5, y + 95 + yDelta);
                } else if (player.getUserID().equals(activePlayerID) && phase.equals("action")) {
                    graphics.setFont(Storage.getFont20());
                    graphics.setColor(new Color(50, 230, 80));
                    graphics.drawString("ACTIVE", x + 9, y + 95 + yDelta);
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

                xDelta = x + 600;
                // xDelta = x + 550 + Math.max(xDelta, xDelta2); DISABLE AUTO-SCALE BASED ON AMOUNT OF FRAGS - ALIGNS PLAYERS' LEADERS/PLANETS
                int yPlayArea = y - 30;
                y += 85;
                y += 200;

                int soCount = objectivesSO(activeMap, yPlayArea + 150, player);

                nombox(player, width - 450, yPlayArea);

                int xDeltaSecondRow = xDelta;
                int yPlayAreaSecondRow = yPlayArea + 160;
                if (!player.getPlanets().isEmpty()) {
                    xDeltaSecondRow = planetInfo(player, activeMap, xDeltaSecondRow, yPlayAreaSecondRow);
                }

                reinforcements(player, activeMap, width - 450, yPlayAreaSecondRow, unitCount);

                if (player.hasAbility("ancient_blueprints")) {
                    xDelta = bentorBluePrintInfo(player, xDelta, yPlayArea, activeMap);
                }

                if (!player.getLeaders().isEmpty()) {
                    xDelta = leaderInfo(player, xDelta, yPlayArea, activeMap);
                }

                if (!player.getAbilities().isEmpty()) {
                    xDelta = abilityInfo(player, xDelta, yPlayArea, activeMap);
                }

                if (!player.getRelics().isEmpty()) {
                    xDelta = relicInfo(player, xDelta, yPlayArea);
                }

                if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
                    xDelta = pnInfo(player, xDelta, yPlayArea, activeMap);
                }

                if (!player.getTechs().isEmpty()) {
                    xDelta = techInfo(player, xDelta, yPlayArea, activeMap);
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

    private int bentorBluePrintInfo(Player player, int x, int y, Map activeMap) {
        int deltaX = 0;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        graphics.setColor(Color.WHITE);
        String bluePrintFileNamePrefix = "pa_ds_bent_blueprint_";
        boolean hasFoundAny = false;
        if (player.hasFoundCulFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "crf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.hasFoundHazFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "hrf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.hasFoundIndFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "irf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.hasFoundUnkFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "urf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        return x + deltaX + (hasFoundAny ? 20 : 0);
    }

    private int pnInfo(Player player, int x, int y, Map activeMap) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        Collection<Player> players = activeMap.getPlayers().values();
        for (String pn : player.getPromissoryNotesInPlayArea()) {
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);  

            boolean commanderUnlocked = false;
            Player promissoryNoteOwner = activeMap.getPNOwner(pn);
            if (promissoryNoteOwner == null) { //nobody owns this note - possibly eliminated player
                BotLogger.log(activeMap.getName() + " " + player.getUserName() + "  `GenerateMap.pnInfo` is trying to display a Promissory Note without an owner - possibly an eliminated player: " + pn);
                continue; 
            }
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    String playerFaction = player_.getFaction();
                    if (playerColor != null && playerColor.equals(promissoryNoteOwner.getColor()) || playerFaction != null && playerFaction.equals(promissoryNoteOwner.getFaction())) {
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
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pn);
            if (promissoryNote != null && promissoryNote.getAttachment() != null && !promissoryNote.getAttachment().isBlank()) {
                String tokenID = promissoryNote.getAttachment();
                found:
                for (Tile tile : activeMap.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(tokenID))) {
                            drawPlanetImage(x + deltaX + 17, y, "pc_planetname_" + unitHolder.getName() + "_rdy.png", unitHolder.getName());
                            break found;
                        }
                    }
                }    
            }
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

    private int leaderInfo(Player player, int x, int y, Map activeMap) {
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
                switch (leader.getType()) {
                    case Constants.AGENT -> pipID = "i";
                    case Constants.COMMANDER -> pipID = "ii";
                    case Constants.HERO -> pipID = "iii";
                    case Constants.ENVOY -> pipID = "agenda";
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

            String leaderInfoFileName = "pa_leaders_" + leader.getId() + status + ".png";
            if (Constants.ENVOY.equals(leader.getType())) leaderInfoFileName = "pa_leaders_envoy" + status + ".png";
            drawPAImage(x + deltaX, y, leaderInfoFileName);
            deltaX += 48;
            if (Constants.COMMANDER.equals(leader.getType()) && player.hasAbility("imperia")) {
                List<String> mahactCCs = player.getMahactCC();

                Collection<Player> players = activeMap.getPlayers().values();
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

    private int abilityInfo(Player player, int x, int y, Map activeMap) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));
        for (String abilityID : player.getAbilities()) {
            
            String abilityFileName = null;
            switch (abilityID) {
                case "grace" -> abilityFileName = "pa_ds_edyn_grace";
                // add additional displayed abilities here
            }
            if (abilityFileName == null) continue;

            boolean isExhaustedLocked = player.getExhaustedAbilities().contains(abilityID);
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            String status = isExhaustedLocked ? "_exh" : "_rdy";
            abilityFileName = abilityFileName + status + ".png";
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, abilityFileName);

            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private void reinforcements(Player player, Map activeMap, int x, int y, HashMap<String, Integer> unitCount) {
        HashMap<String, Tile> tileMap = activeMap.getTileMap();
        drawPAImage(x, y, "pa_reinforcements.png");
        if (unitCount.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    fillUnits(unitCount, unitHolder, false);
                }
            }
            for (Player player_ : activeMap.getPlayers().values()) {
                UnitHolder unitHolder = player_.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                if (unitHolder == null){
                    continue;
                }
                fillUnits(unitCount, unitHolder, true);
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
                int positionCount = player.getUnitCap(unitID);
                boolean aboveCap = true;
                if (positionCount == 0) {
                    positionCount = reinforcementsPosition.getPositionCount(unitID);
                    aboveCap = false;
                }
                int remainingReinforcements = positionCount - count;
                if (remainingReinforcements > 0) {
                    for (int i = 0; i < remainingReinforcements; i++) {
                        try {
                            String unitPath = ResourceHelper.getInstance().getUnitFile(unitColorID);
                            BufferedImage image = ImageIO.read(new File(unitPath));
                            Point position = reinforcementsPosition.getPosition(unitID);
                            graphics.drawImage(image, x + position.x, y + position.y, null);
                        } catch (Exception e) {
                            BotLogger.log("Could not parse unit file for reinforcements: " + unitID, e);
                        }
                        if (aboveCap) {
                            i = remainingReinforcements;
                        }
                    }
                } else {
                    if (remainingReinforcements < 0 && !activeMap.isDiscordantStarsMode()  && activeMap.getCCNPlasticLimit()) {
                        String warningMessage = playerColor + " is exceeding unit plastic or cardboard limits";
                        if (activeMap.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), warningMessage);
                        } else {
                            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), warningMessage);
                        }
                    }
                }
                if (-5 <= remainingReinforcements) paintNumber(unitID, x, y, remainingReinforcements, playerColor);
            }
        }

        int ccCount = Helper.getCCCount(activeMap, playerColor);
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
                        BotLogger.log("Could not parse file for CC: " + playerColor, e);
                    }
                }
            }
            if (-5 <= remainingReinforcements) paintNumber(CC_TAG, x, y, remainingReinforcements, playerColor);
        }

    }

    private static void fillUnits(HashMap<String, Integer> unitCount, UnitHolder unitHolder, boolean ignoreInfantryFighters) {
        HashMap<String, Integer> units = unitHolder.getUnits();
        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
            String key = unitEntry.getKey();
            Integer count = unitCount.get(key);
            if (count == null) {
                count = 0;
            }
            if (key.contains("gf") || key.contains("ff")) {
                if (ignoreInfantryFighters){
                    continue;
                }
                count++;
            } else {
                count += unitEntry.getValue();
            }
            unitCount.put(key, count);
        }
    }

    private void nombox(Player player, int x, int y) {

        UnitHolder unitHolder = player.getNomboxTile().getUnitHolders().get(Constants.SPACE);
        if (unitHolder == null || unitHolder.getUnits().isEmpty()) {
            return;
        }

        Point infPoint = new Point(50, 75);
        Point fighterPoint = new Point(50, 125);
        Point mechPoint = new Point(100, 63);
        Point destroyerPoint = new Point(144, 63);
        Point cruiserPoint = new Point(185, 55);
        Point carrierPoint = new Point(235, 58);
        Point dreadnoughtPoint = new Point(284, 54);
        Point flagshipPoint = new Point(335, 47);
        Point warSunPoint = new Point(393, 56);

        String faction = player.getFaction();
        if (faction != null) {
            String factionPath = getFactionPath(faction);
            if (factionPath != null) {
                try {
                    BufferedImage bufferedImage = ImageIO.read(new File(factionPath));
                    graphics.drawImage(bufferedImage, x + 178, y + 33, null);
                } catch (IOException e) {
                    BotLogger.log("Could not paint faction: " + faction, e);
                }
            }
        }

        drawPAImage(x, y, "pa_nombox.png");

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

        BufferedImage image = null;

        List<String> order = List.of("_mf.png", "_dd.png", "_ca.png", "_cv.png", "_dn.png", "_fs.png", "_ws.png", "_ff.png", "_gf.png");

        java.util.Map<String, List<java.util.Map.Entry<String, Integer>>> collect = units.entrySet().stream().collect(Collectors.groupingBy(key -> key.getKey().substring(key.getKey().lastIndexOf("_"))));
        for (String orderKey : order) {
            List<java.util.Map.Entry<String, Integer>> entry = collect.get(orderKey);
            if (entry == null){
                continue;
            }

            int countOfUnits = 0;
            for (java.util.Map.Entry<String, Integer> entrySet : entry) {
                countOfUnits += entrySet.getValue();
            }
            int deltaY = 0;
            for (java.util.Map.Entry<String, Integer> unitEntry : entry) {
                String unitID = unitEntry.getKey();
                Integer unitCount = unitEntry.getValue();
                Integer bulkUnitCount = null;

                if (unitID.endsWith(Constants.COLOR_FF)) {
                    unitID = unitID.replace(Constants.COLOR_FF, Constants.BULK_FF);
                    bulkUnitCount = unitCount;
                } else if (unitID.endsWith(Constants.COLOR_GF)) {
                    unitID = unitID.replace(Constants.COLOR_GF, Constants.BULK_GF);
                    bulkUnitCount = unitCount;
                }
                try {
                    float scaleOfUnit = 1.0f;
                    String unitPath = Tile.getUnitPath(unitID);
                    image = resizeImage(ImageIO.read(new File(unitPath)), scaleOfUnit);
                } catch (Exception e) {
                    BotLogger.log("Could not parse unit file for: " + unitID, e);
                }
                if (bulkUnitCount != null && bulkUnitCount > 0) {
                    unitCount = 1;
                }
                if (image == null) {
                    BotLogger.log("Could not find unit image for: " + unitID);
                    continue;
                }


                if (unitCount == null) {
                    unitCount = 0;
                }

                Point position = new Point(x, y);
                boolean justNumber = false;
                if (unitID.contains("_tkn_ff.png")) {
                    position.x += fighterPoint.x;
                    position.y += fighterPoint.y;
                    justNumber = true;
                } else if (unitID.contains("_tkn_gf.png")) {
                    position.x += infPoint.x;
                    position.y += infPoint.y;
                    justNumber = true;
                } else if (unitID.contains("_ca.png")) {
                    position.x += cruiserPoint.x;
                    position.y += cruiserPoint.y;
                } else if (unitID.contains("_cv.png")) {
                    position.x += carrierPoint.x;
                    position.y += carrierPoint.y;
                } else if (unitID.contains("_dd.png")) {
                    position.x += destroyerPoint.x;
                    position.y += destroyerPoint.y;
                } else if (unitID.contains("_mf.png")) {
                    position.x += mechPoint.x;
                    position.y += mechPoint.y;
                } else if (unitID.contains("_dn.png")) {
                    position.x += dreadnoughtPoint.x;
                    position.y += dreadnoughtPoint.y;
                } else if (unitID.contains("_fs.png")) {
                    position.x += flagshipPoint.x;
                    position.y += flagshipPoint.y;
                } else if (unitID.contains("_ws.png")) {
                    position.x += warSunPoint.x;
                    position.y += warSunPoint.y;
                }

                if (justNumber){
                    graphics.setFont(Storage.getFont40());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(Integer.toString(countOfUnits), position.x, position.y);
                    break;
                }
                position.y -= (countOfUnits * 7);
                for (int i = 0; i < unitCount; i++) {
                    graphics.drawImage(image, position.x, position.y + deltaY, null);
                    deltaY += 14;
                }
            }
        }
    }

    private void paintNumber(String unitID, int x, int y, int reinforcementsCount, String color) {
        String id = "number_" + unitID;
        UnitTokenPosition textPosition = PositionMapper.getReinforcementsPosition(id);
        String text = "pa_reinforcements_numbers_" + reinforcementsCount;
        String colorID = Mapper.getColorID(color);
        if (colorID.startsWith("ylw") || colorID.startsWith("org") || colorID.startsWith("pnk")
                || colorID.startsWith("tan") || colorID.startsWith("crm") || colorID.startsWith("sns") || colorID.startsWith("tqs")
                || colorID.startsWith("gld") || colorID.startsWith("lme") || colorID.startsWith("lvn") || colorID.startsWith("rse")
                || colorID.startsWith("spr") || colorID.startsWith("tea") || colorID.startsWith("lgy")) {
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

    private int planetInfo(Player player, Map activeMap, int x, int y) {
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        List<String> planets = player.getPlanets();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(2));

        //RESOURCE/INFLUENCE TOTALS
        drawPAImage(x + deltaX - 2, y - 2, "pa_resinf_info.png");
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 152, 152);
        if (player.hasLeaderUnlocked("xxchahero")) { //XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, activeMap);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, activeMap);
            if (player.getUserID().equals("586504147746947090")) {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha_gedsdead.png", 0.9f);
            } else {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha.png", 0.9f);
            }
            drawFactionIconImage(x + deltaX + 75 - 94/2, y + 75 - 94/2, "xxcha.png", 1f, 0.15f);
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerResources), new Rectangle(x + deltaX, y + 75 - 35 + 5, 150, 35), Storage.getFont35());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerResources), new Rectangle(x + deltaX, y + 75 + 5, 150, 24), Storage.getFont24());
        } else { //NOT XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, activeMap);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, activeMap);
            int availablePlayerResourcesOptimal = Helper.getPlayerOptimalResourcesAvailable(player, activeMap);
            // int totalPlayerResourcesOptimal = Helper.getPlayerOptimalResourcesTotal(player, map);
            int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, activeMap);
            int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, activeMap);
            int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, activeMap);
            // int totalPlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceTotal(player, map);
            int availablePlayerFlex = Helper.getPlayerFlexResourcesInfluenceAvailable(player, activeMap);
            // int totalPlayerFlex = Helper.getPlayerFlexResourcesInfluenceTotal(player, map);

            //  RESOURCES
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerResources), new Rectangle(x + deltaX + 30, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerResources), new Rectangle(x + deltaX + 30, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#d5bd4f")); //greyish-yellow
            drawCenteredString(graphics, String.valueOf(availablePlayerResourcesOptimal), new Rectangle(x + deltaX + 30, y + 90, 32, 32), Storage.getFont18());
            // drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 30, y + 100, 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // drawCenteredString(graphics, String.valueOf(totalPlayerResourcesOptimal), new Rectangle(x + deltaX + 34, y + 109, 32, 32), Storage.getFont32());

            //  INFLUENCE
            graphics.setColor(Color.WHITE);
            drawCenteredString(graphics, String.valueOf(availablePlayerInfluence), new Rectangle(x + deltaX + 90, y + 30, 32, 32), Storage.getFont32());
            graphics.setColor(Color.GRAY);
            drawCenteredString(graphics, String.valueOf(totalPlayerInfluence), new Rectangle(x + deltaX + 90, y + 55, 32, 32), Storage.getFont20());
            graphics.setColor(Color.decode("#57b9d9")); //greyish-blue
            drawCenteredString(graphics, String.valueOf(availablePlayerInfluenceOptimal), new Rectangle(x + deltaX + 90, y + 90, 32, 32), Storage.getFont18());
            // drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 90, y + 100, 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // drawCenteredString(graphics, String.valueOf(totalPlayerInfluenceOptimal), new Rectangle(x + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

            //  FLEX
            graphics.setColor(Color.WHITE);
            // drawCenteredString(graphics, "FLEX", new Rectangle(x + deltaX, y + 130, 150, 8), Storage.getFont8());
            if (player.getUserID().equals("203608548440014848")) graphics.setColor(Color.decode("#f616ce"));
            drawCenteredString(graphics, String.valueOf(availablePlayerFlex), new Rectangle(x + deltaX, y + 115, 150, 20), Storage.getFont18());
            // drawCenteredString(graphics, String.valueOf(totalPlayerFlex), new Rectangle(x + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

        }

        deltaX += 156;

        boolean randomizeList = player != fowPlayer && isFoWPrivate != null && isFoWPrivate;
        if (randomizeList) {
            Collections.shuffle(planets);
        }
        for (String planet : planets) {
            try {
                UnitHolder unitHolder = planetsInfo.get(planet);
                if (!(unitHolder instanceof Planet planetHolder)) {
                    BotLogger.log(activeMap.getName() + ": Planet unitHolder not found: " + planet);
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
                    drawPlanetImage(x + deltaX + 2, y + 2, planetTypeName, planet);
                } else {
                    String originalPlanetType = planetHolder.getOriginalPlanetType();
                    if (originalPlanetType.equals("none") && planet.equals("mr"))
                        originalPlanetType = "mr";
                    if (originalPlanetType.equals("none"))
                        originalPlanetType=TileHelper.getAllPlanets().get(planet).getFactionHomeworld();
                    if (Optional.ofNullable(originalPlanetType).isEmpty()) {
                        originalPlanetType = "none";
                    }
                    if (originalPlanetType.equals("none"))
                        originalPlanetType=player.getFaction();
                    
                    if (!originalPlanetType.isEmpty()) {
                        if ("keleres".equals(player.getFaction()) && ("mentak".equals(originalPlanetType) ||
                                "xxcha".equals(originalPlanetType) ||
                                "argent".equals(originalPlanetType))) {
                            originalPlanetType = "keleres";
                        }

                        String planetTypeName = "pc_attribute_" + originalPlanetType + ".png";
                        drawPlanetImage(x + deltaX + 2, y + 2, planetTypeName, planet);
                    }
                }

                // GLEDGE CORE
                if (unitHolder.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                    String tokenPath = ResourceHelper.getInstance().getTokenFile(Constants.GLEDGE_CORE_PNG);
                    try {
                        BufferedImage image = resizeImage(ImageIO.read(new File(tokenPath)), 0.25f);
                        graphics.drawImage(image, x + deltaX + 15, y + 112, null);
                    } catch (Exception e) {
                        BotLogger.log("Could not parse control token file for: " + Constants.GLEDGE_CORE_PNG, e);
                    }
                }

                boolean hasAttachment = planetHolder.hasAttachment();
                if (hasAttachment) {
                    String planetTypeName = "pc_upgrade.png";
                    drawPlanetImage(x + deltaX + 26, y + 40, planetTypeName, planet);
                }

                if (planetHolder.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                    String khraskGardenWorlds = "pc_ds_khraskbonus.png";
                    drawPlanetImage(x + deltaX, y, khraskGardenWorlds, planet);
                }

                boolean hasAbility = planetHolder.isHasAbility() || planetHolder.getTokenList().stream().anyMatch(token -> token.contains("nanoforge") || token.contains("legendary"));
                if (hasAbility) {
                    String statusOfAbility = exhaustedPlanetsAbilities.contains(planet) ? "_exh" : "_rdy";
                    String planetTypeName = "pc_legendary" + statusOfAbility + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 60, planetTypeName, planet);
                }
                String originalTechSpeciality = planetHolder.getOriginalTechSpeciality();
                if (!originalTechSpeciality.isEmpty()) {
                    String planetTypeName = "pc_tech_" + originalTechSpeciality + statusOfPlanet + ".png";
                    drawPlanetImage(x + deltaX + 26, y + 82, planetTypeName, planet);
                } else {
                    ArrayList<String> techSpeciality = planetHolder.getTechSpeciality();
                    for (String techSpec : techSpeciality) {
                        if (techSpec.isEmpty()) {
                            continue;
                        }
                        String planetTypeName = "pc_tech_" + techSpec + statusOfPlanet + ".png";
                        drawPlanetImage(x + deltaX + 26, y + 82, planetTypeName, planet);
                    }
                }

                drawPlanetImage(x + deltaX + 26, y + 103, resFileName, planet);
                drawPlanetImage(x + deltaX + 26, y + 125, infFileName, planet);
                drawPlanetImage(x + deltaX, y, planetFileName, planet);

                deltaX += 56;
            } catch (Exception e) {
                BotLogger.log("could not print out planet: " + planet.toLowerCase(), e);
            }
        }

        return x + deltaX + 20;
    }

    private int techInfo(Player player, int x, int y, Map activeMap) {
        List<String> techs = player.getTechs();
        List<String> exhaustedTechs = player.getExhaustedTechs();
        if (techs.isEmpty()) {
            return y;
        }


        HashMap<String, TechnologyModel> techInfo = Mapper.getTechs();
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
                    TechnologyModel tech1Info = techInfo.get(tech1);
                    TechnologyModel tech2Info = techInfo.get(tech2);
                    try {
                        int t1 = tech1Info.getRequirements().length();
                        int t2 = tech2Info.getRequirements().length();
                        return (t1 < t2) ? -1 : ((t1 == t2) ? (tech1Info.getName().compareTo(tech2Info.getName())) : 1);
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
        deltaX = techStasisCapsule(x, y, deltaX, player, techsFiltered.get(Constants.UNIT_UPGRADE), techInfo);
        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), exhaustedTechs, techInfo, deltaX, player, activeMap);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, HashMap<String, TechnologyModel> techInfo, int deltaX) {
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

            TechnologyModel techInformation = techInfo.get(tech);

            String techIcon;
            switch (techInformation.getType()) {
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

            if (!techInformation.getFaction().isEmpty()) {
                String techSpec = "pa_tech_factionicon_" + techInformation.getFaction() + "_rdy.png";
                drawPAImage(x + deltaX, y, techSpec);
            }

            String techName = "pa_tech_techname_" + tech + techStatus;
            drawPAImage(x + deltaX, y, techName);


            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }

    private int techStasisCapsule(int x, int y, int deltaX, Player player, List<String> techs, HashMap<String, TechnologyModel> techInfo) {
        int stasisInfantry = player.getStasisInfantry();
        if ((techs == null && stasisInfantry == 0) || !hasInfantryII(techs, techInfo) && stasisInfantry == 0) {
            return deltaX;
        }
        String techSpec = "pa_tech_techname_stasiscapsule.png";
        drawPAImage(x + deltaX, y, techSpec);
        if (stasisInfantry < 20) {
            graphics.setFont(Storage.getFont35());
        } else {
            graphics.setFont(Storage.getFont30());
        }
        int centerX = 0;
        if (stasisInfantry < 10) {
            centerX += 5;
        }
        graphics.drawString(String.valueOf(stasisInfantry), x + deltaX + 3 + centerX, y + 148);
        graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
        deltaX += 48;
        return deltaX;
    }

    private boolean hasInfantryII(List<String> techs, HashMap<String, TechnologyModel> techInfo) {
        if (techs == null) {
            return false;
        }
        for (String tech : techs) {
            TechnologyModel techInformation = techInfo.get(tech);
            if (techInformation.getBaseUpgrade().equals("inf2") || tech.equals("inf2")) {
                return true;
            }
        }
        return false;
    }

    private int techFieldUnit(int x, int y, List<String> techs, List<String> exhaustedTechs, HashMap<String, TechnologyModel> techInfo, int deltaX, Player player, Map activeMap) {
        String outline = "pa_tech_unitsnew_outlines_generic.png";
        if ("nomad".equals(player.getFaction())) {
            outline = "pa_tech_unitsnew_outlines_nomad.png";
        }
        if ("nekro".equals(player.getFaction())) {
            for (Player player_ : activeMap.getPlayers().values()) {
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
            TechnologyModel techInformation = techInfo.get(tech);

            String unit = "pa_tech_unitsnew_" + Mapper.getColorID(player.getColor()) + "_";
            if (techInformation.getBaseUpgrade().isEmpty()) {
                if (tech.equals("dt2")) {
                    unit += "sd2.png";
                } else {
                    unit += tech + ".png";
                }
            } else {
                unit += techInformation.getBaseUpgrade() + ".png";
            }
            drawPAImage(x + deltaX, y, unit);
            if (!techInformation.getFaction().isEmpty()) {
                String factionIcon = "pa_tech_unitsnew_" + techInformation.getFaction() + "_" + tech + ".png";
                drawPAImage(x + deltaX, y, factionIcon);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 224, 152);
        deltaX += 228;
        return deltaX;
    }

    private void drawFactionIconImage(int x, int y, String resourceName, float scale, float opacity) {
        try {
            String resourcePath = ResourceHelper.getInstance().getFactionFile(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            resourceBufferedImage = resizeImage(resourceBufferedImage, scale);
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2.drawImage(resourceBufferedImage, x, y, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        } catch (Exception e) {
            BotLogger.log("Could not display planet: " + resourceName, e);
        }
    }

    private void drawPlanetImage(int x, int y, String resourceName, String planetName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
            if(Optional.ofNullable(resourcePath).isPresent()) {
                @SuppressWarnings("ConstantConditions")
                BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
                graphics.drawImage(resourceBufferedImage, x, y, null);
            }
            else {
                Graphics2D g2 = (Graphics2D) graphics;
                AffineTransform originalTransform = g2.getTransform();
                g2.rotate(Math.toRadians(-90));
                g2.setFont(Storage.getFont20());
                String name = Mapper.getPlanet(planetName).getName().toUpperCase();
                g2.drawString(name.substring(0, Math.min(name.length(), 7)),
                        (y+146)*-1, //See https://www.codejava.net/java-se/graphics/how-to-draw-text-vertically-with-graphics2d
                        x + 6 + g2.getFontMetrics().getHeight()/2);
                g2.setTransform(originalTransform);
            }

        } catch (Exception e) {
            BotLogger.log("Could not display planet: " + resourceName, e);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAImageOpaque(int x, int y, String resourceName, float opacity) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            @SuppressWarnings("ConstantConditions")
            BufferedImage resourceBufferedImage = ImageIO.read(new File(resourcePath));
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2.drawImage(resourceBufferedImage, x, y, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        } catch (Exception e) {
            BotLogger.log("Could not display play area: " + resourceName, e);
        }
    }

    private int scoreTrack(Map activeMap, int y) {

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(5));
        graphics.setFont(Storage.getFont50());
        int height = 140;
        int width = 150;
        if (14 < activeMap.getVp()) {
            width = 120;
        }
        for (int i = 0; i <= activeMap.getVp(); i++) {
            graphics.setColor(Color.WHITE);
            graphics.drawString(Integer.toString(i), i * width + 55, y + (height / 2) + 25);
            g2.setColor(Color.RED);
            g2.drawRect(i * width, y, width, height);
        }

        List<Player> players = new ArrayList<>(activeMap.getPlayers().values());
        int tempCounter = 0;
        int tempX = 0;
        int tempWidth = 0;
        BufferedImage factionImage = null;

        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }
        for (Player player : players) {
            if (!player.isRealPlayer()) continue;
            try {
                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, fowPlayer);
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

    public static String getSCNumberIfNaaluInPlay(Player player, Map activeMap, String scText) {
        if (player.hasAbility("telepathic")) { //naalu 0 token ability
            boolean giftPlayed = false;
            Collection<Player> activePlayers = activeMap.getPlayers().values().stream().filter(player_ -> player_.isRealPlayer()).toList();
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

    private int strategyCards(Map activeMap, int y) {
        boolean convertToGenericSC = isFoWPrivate != null && isFoWPrivate;
        y += 80;
        LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
        Collection<Player> players = activeMap.getPlayers().values();
        Set<Integer> scPicked = new HashSet<>();
        for (Player player : players) {
            scPicked.addAll(player.getSCs());
        }
        HashMap<Integer, Boolean> scPlayed = activeMap.getScPlayed();
        int x = 20;
        int horizontalSpacingIncrement = 70;
        for (java.util.Map.Entry<Integer, Integer> scTGs : scTradeGoods.entrySet()) {
            Integer sc = scTGs.getKey();
            if (sc == 0) {
                continue;
            }
            if (sc > 9) horizontalSpacingIncrement = 80;
            if (sc > 19) horizontalSpacingIncrement = 100;
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
            x += horizontalSpacingIncrement;
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        graphics.drawString("ROUND: " + activeMap.getRound(), x + 100, y);

        return y + 40;
    }

    private void playerInfo(Map activeMap) {
        int playerRow = 1;
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        Player speaker = activeMap.getPlayer(activeMap.getSpeaker());
        List<Player> players = new ArrayList<>(activeMap.getPlayers().values());
        if (isFoWPrivate != null && isFoWPrivate) {
            Collections.shuffle(players);
        }


        int deltaX = mapWidth - extraX - (extraRow ? extraX : 0);
        int deltaY = extraY;

        int playerCount = activeMap.getPlayerCountForMap();
        int ringCount = activeMap.getRingCount();

        boolean inverted = false;
        int index = 0;
        for (Player player : players) {

            if (player.getFaction() == null || !player.isRealPlayer()) {
                continue;
            }
            if ((deltaY + PLAYER_STATS_HEIGHT) > (mapHeight - extraY) || (deltaY + PLAYER_STATS_HEIGHT) < extraY) {
                playerRow++;
                if (playerRow == 2) {
                    deltaY = deltaY - PLAYER_STATS_HEIGHT;
                    deltaX = 10;
                    inverted = true;
                } else if (playerRow == 3) {
                    deltaX = mapWidth - extraX + 10;
                    deltaY = extraY;
                    inverted = false;
                } else if (playerRow == 4) {
                    deltaX = mapWidth + 10;
                    deltaY = deltaY - PLAYER_STATS_HEIGHT;
                    inverted = true;
                }
            }
            int deltaSplitX = 0;
            int deltaSplitY = 0;
            boolean specialCase = false;
            if (playerCount == 6 && ringCount == 3) {
                String tileID = Constants.setup6p.get(index);
                Point tilePosition = PositionMapper.getTilePosition(tileID);
                if (tilePosition != null) {
                    tilePosition = getTilePosition(activeMap, "", tilePosition.x, tilePosition.y);
                    if (index == 0) {
                        deltaX = tilePosition.x + extraX + 80;
                        deltaY = tilePosition.y - 80;
                        deltaSplitX = 200;
                    } else if (index == 1) {
                        deltaX = tilePosition.x + 360 + extraX;
                        deltaY = tilePosition.y + extraY;
                    } else if (index == 2) {
                        deltaX = tilePosition.x + 360 + extraX;
                        deltaY = tilePosition.y + extraY;
                    } else if (index == 3) {
                        deltaX = tilePosition.x + extraX;
                        deltaY = tilePosition.y + 360  + extraY;
                        deltaSplitX = 200;
                    } else if (index == 4) {
                        deltaX = 10;
                        deltaY = tilePosition.y + extraY;
                    } else if (index == 5) {
                        deltaX = 10;
                        deltaY = tilePosition.y + extraY;
                    }
                    index++;
                }
            } else if (playerCount == 8 && ringCount == 4) {
                String tileID = Constants.setup8p.get(index);
                Point tilePosition = PositionMapper.getTilePosition(tileID);
                if (tilePosition != null) {
                    tilePosition = getTilePosition(activeMap, "", tilePosition.x, tilePosition.y);
                    if (index == 0) {
                        deltaX = tilePosition.x + extraX + 80;
                        deltaY = tilePosition.y - 80;
                        deltaSplitX = 200;
                    } else if (index == 1) {
                        deltaX = tilePosition.x + extraX + 80;
                        deltaY = tilePosition.y - 80;
                        deltaSplitX = 200;
                    } else if (index == 2) {
                        deltaX = tilePosition.x + 360 + extraX;
                        deltaY = tilePosition.y + extraY;
                    } else if (index == 3) {
                        deltaX = tilePosition.x + extraX + 50;
                        deltaY = tilePosition.y + 360  + extraY;
                        deltaSplitX = 200;
                        specialCase = true;
                    } else if (index == 4) {
                        deltaX = tilePosition.x + extraX;
                        deltaY = tilePosition.y + 360  + extraY;
                        deltaSplitX = 200;
                    } else if (index == 5) {
                        deltaX = tilePosition.x + extraX/2;
                        deltaY = tilePosition.y + 360 + extraY;
                        deltaSplitX = 200;
                    }else if (index == 6) {
                        deltaX = 10;
                        deltaY = tilePosition.y + extraY;
                    }else if (index == 7) {
                        deltaX = tilePosition.x + 80;
                        deltaY = tilePosition.y - 80;
                        deltaSplitX = 200;
                        specialCase = true;
                    }
                    index++;
                }
            }

            String userName = player.getUserName();

            boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, fowPlayer);
            if (convertToGeneric) {
                continue;
            }
            Point point = PositionMapper.getPlayerStats(Constants.STATS_USERNAME);
            graphics.drawString(userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX, point.y + deltaY);
            Integer vpCount = userVPs.get(player);
            vpCount = vpCount == null ? 0 : vpCount;
            point = PositionMapper.getPlayerStats(Constants.STATS_VP);
            graphics.drawString(StringUtils.capitalize(player.getFaction()) + ", VP - " + vpCount, point.x + deltaX, point.y + deltaY);

            int totalSecrets = player.getSecrets().keySet().size();
            Set<String> soSet = player.getSecretsScored().keySet();
            int soOffset = 0;

            String soHand = "pa_so-icon_hand.png";
            String soScored = "pa_so-icon_scored.png";
            point = PositionMapper.getPlayerStats(Constants.STATS_SO);
            for (int i = 0; i < totalSecrets; i++) {
                drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soHand);
                soOffset += 25;
            }
            ArrayList<String> soToPoList = activeMap.getSoToPoList();
            for (String soID : soSet) {
                if (!soToPoList.contains(soID)) {
                    drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soScored);
                    soOffset += 25;
                }
            }

            //PAINT SC#
            List<Integer> playerSCs = new ArrayList<>(player.getSCs());
            Collections.sort(playerSCs);
            int count = 0;
            for (int sc : playerSCs) {
                String scText = sc == 0 ? " " : Integer.toString(sc);
                scText = getSCNumberIfNaaluInPlay(player, activeMap, scText);
                graphics.setColor(getSCColor(sc, activeMap));
                graphics.setFont(Storage.getFont64());
                point = PositionMapper.getPlayerStats(Constants.STATS_SC);
                if (sc != 0) {
                    graphics.drawString(scText, point.x + deltaX + 64 * count, point.y + deltaY);
                }
                count++;
            }


            graphics.setColor(Color.WHITE);
            graphics.setFont(Storage.getFont32());
            String ccID = Mapper.getCCID(player.getColor());
            String fleetCCID = Mapper.getFleetCCID(player.getColor());
            point = PositionMapper.getPlayerStats(Constants.STATS_CC);
            int x = point.x + deltaX;
            int y = point.y + deltaY;
            if (deltaSplitX != 0){
                deltaSplitY = point.y;
            }
            boolean hasArmadaAbility = player.hasAbility("armada");
            drawCCOfPlayer(ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), false, null, activeMap);
            drawCCOfPlayer(fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player.getFleetCC(), hasArmadaAbility, player, activeMap);
            drawCCOfPlayer(ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), false, null, activeMap);

            if (player == speaker) {
                String speakerID = Mapper.getTokenID(Constants.SPEAKER);
                String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
                if (speakerFile != null) {
                    BufferedImage bufferedImage = null;
                    try {
                        bufferedImage = ImageIO.read(new File(speakerFile));
                    } catch (IOException e) {
                        BotLogger.log("Could not read speaker file", e);
                    }
                    point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                    int negativeDelta = specialCase ? 200 : 0;
                    graphics.drawImage(bufferedImage, point.x + deltaX + deltaSplitX + negativeDelta, point.y + deltaY - deltaSplitY, null);
                    graphics.setColor(Color.WHITE);
                }
            }
            String activePlayerID = activeMap.getActivePlayer();
            String phase = activeMap.getCurrentPhase();
            if (player.isPassed()) {
                point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
                graphics.setColor(new Color(238, 58, 80));
                graphics.drawString("PASSED", point.x + deltaX, point.y + deltaY);
                graphics.setColor(Color.WHITE);
            } else if (player.getUserID().equals(activePlayerID) && phase.equals("action")) {
                point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
                graphics.setColor(new Color(50, 230, 80));
                graphics.drawString("ACTIVE", point.x + deltaX + 4, point.y + deltaY);
                graphics.setColor(Color.WHITE);
            }
            if (inverted) {
                deltaY -= PLAYER_STATS_HEIGHT;
            } else {
                deltaY += PLAYER_STATS_HEIGHT;
            }
        }
    }

    private void drawCCOfPlayer(String ccID, int x, int y, int ccCount, boolean isLetnev, Player player, Map activeMap) {
        String ccPath = Mapper.getCCPath(ccID);
        try {
            String faction = getFactionByControlMarker(activeMap.getPlayers().values(), ccID);
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
                String armadaCCID = Mapper.getCCID(player.getColor());
                String armadaCCPath = Mapper.getCCPath(armadaCCID);
                BufferedImage armadaCCImage = ImageIO.read(new File(armadaCCPath));
                String armadaFaction = player.getFaction();
                BufferedImage armadaFactionImage = null;
                if (armadaFaction != null) {
                    String armadaFactionImagePath = Mapper.getCCPath("control_faction_" + armadaFaction + ".png");
                    if (armadaFactionImagePath != null) {
                        armadaFactionImage = ImageIO.read(new File(armadaFactionImagePath));
                    }
                }

                for (int i = 0; i < 2; i++) {
                    graphics.drawImage(armadaCCImage, x + (delta * i), y, null);
                    if (armadaFactionImage != null) {
                        graphics.drawImage(armadaFactionImage, x + (delta * i) + DELTA_X, y + DELTA_Y, null);
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
                        String fleetCCID = Mapper.getCCPath(Mapper.getFleetCCID(ccColor));

                        faction = getFactionByControlMarker(activeMap.getPlayers().values(), fleetCCID);
                        factionImage = null;
                        if (faction != null) {
                            boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, fowPlayer);
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

    public int objectives(Map activeMap, int y, Graphics graphics, HashMap<Player, Integer> userVPs, boolean justCalculate) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));
        LinkedHashMap<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>(activeMap.getScoredPublicObjectives());
        LinkedHashMap<String, Integer> revealedPublicObjectives = new LinkedHashMap<>(activeMap.getRevealedPublicObjectives());
        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        LinkedHashMap<String, Integer> customPublicVP = activeMap.getCustomPublicVP();
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
        int y1 = displayObjectives(y, x, activeMap, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState1, po1, 1, column, null, justCalculate, false, graphics, userVPs);

        column[0] = 1;
        x = 801;
        graphics.setColor(new Color(93, 173, 226));
        int y2 = displayObjectives(y, x, activeMap, scoredPublicObjectives, revealedPublicObjectives, players, publicObjectivesState2, po2, 2, column, null, justCalculate, false, graphics, userVPs);

        column[0] = 2;
        x = 1598;
        graphics.setColor(Color.WHITE);
        int y3 = displayObjectives(y, x, activeMap, scoredPublicObjectives, revealedPublicObjectives, players, customPublics, customVP, null, column, customPublicVP, justCalculate, false, graphics, userVPs);

        revealedPublicObjectives = new LinkedHashMap<>();
        scoredPublicObjectives = new LinkedHashMap<>();
        for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
            Player player = playerEntry.getValue();
            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
            for (String id : activeMap.getSoToPoList()) {
                secretsScored.remove(id);
            }
            revealedPublicObjectives.putAll(secretsScored);
            for (String id : secretsScored.keySet()) {
                scoredPublicObjectives.put(id, List.of(player.getUserID()));
            }
        }

        graphics.setColor(Color.RED);
        y = displayObjectives(y, x, activeMap, scoredPublicObjectives, revealedPublicObjectives, players, secretObjectives, secret, 1, column, customPublicVP, true, false, graphics, userVPs);
        if (column[0] != 0) {
            y += 40;
        }

        graphics.setColor(Color.green);
        displaySftT(y, x, activeMap, players, column, graphics);

        return Math.max(y3, Math.max(y1, y2)) + 15;
    }

    private int laws(Map activeMap, int y) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));


        LinkedHashMap<String, Integer> laws = activeMap.getLaws();
        LinkedHashMap<String, String> lawsInfo = activeMap.getLawsInfo();
        boolean secondColumn = false;
        for (java.util.Map.Entry<String, Integer> lawEntry : laws.entrySet()) {
            String lawID = lawEntry.getKey();
            String lawNumberID = "(" + lawEntry.getValue() + ") ";
            String optionalText = lawsInfo.get(lawID);
            graphics.setFont(Storage.getFont35());
            graphics.setColor(new Color(228, 255, 0));

            graphics.drawRect(x, y, 1178, 110);
            String agendaTitle = Mapper.getAgendaTitle(lawID);
            if (agendaTitle == null) {
                agendaTitle = Mapper.getAgendaJustNames().get(lawID);
            }
            if (optionalText != null && !optionalText.isEmpty() && Helper.getPlayerFromColorOrFaction(activeMap, optionalText) == null) {
                agendaTitle += "   [" + optionalText + "]";
            }
            graphics.drawString(agendaTitle, x + 95, y + 30);
            graphics.setFont(Storage.getFont26());
            graphics.setColor(Color.WHITE);

            String agendaText = Mapper.getAgendaText(lawID);
            if (agendaText == null) {
                agendaText = Mapper.getAgendaForOnly(lawID);
            }
            agendaText = lawNumberID + agendaText;
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
                    for (Player player : activeMap.getPlayers().values()) {
                        if (optionalText.equals(player.getFaction()) || optionalText.equals(player.getColor())) {
                            if (isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, fowPlayer)) {
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
                BotLogger.log("Could not paint agenda icon", e);
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

    private int objectivesSO(Map activeMap, int y, Player player) {
        int x = 5;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(new BasicStroke(3));
        userVPs = new HashMap<>();

        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        LinkedHashMap<String, Integer> customPublicVP = activeMap.getCustomPublicVP();
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
            y = displayObjectives(y, x, activeMap, new LinkedHashMap<>(), revealedSecrets, players, secretObjectives, secret, 0, column, customPublicVP, false, true, graphics, userVPs);
        }
        LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : activeMap.getSoToPoList()) {
            secretsScored.remove(id);
        }
        revealedPublicObjectives.putAll(secretsScored);
        for (String id : secretsScored.keySet()) {
            scoredPublicObjectives.put(id, List.of(player.getUserID()));
        }
        graphics.setColor(Color.RED);
        y = displayObjectives(y, x, activeMap, scoredPublicObjectives, revealedPublicObjectives, players, secretObjectives, secret, 1, column, customPublicVP, false, true, graphics, userVPs);
        if (player.isSearchWarrant()) {
            return secretsScored.keySet().size() + player.getSecrets().keySet().size();
        } else {
            return secretsScored.keySet().size();
        }
    }

    private int displaySftT(int y, int x, Map activeMap, LinkedHashMap<String, Player> players, Integer[] column, Graphics graphics) {
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
                    Player promissoryNoteOwner = activeMap.getPNOwner(id);
                    if (promissoryNoteOwner == null) { //nobody owns this note - possibly eliminated player
                        BotLogger.log(activeMap.getName() + " " + player.getUserName() + "  `GenerateMap.displaySftT` is trying to display a **Support for the Throne** without an owner - possibly an eliminated player: " + id);
                        continue; 
                    }
                    StringBuilder name = new StringBuilder(pnSplit[0] + " - ");
                    for (Player player_ : players.values()) {
                        if (player_ != player) {
                            String playerColor = player_.getColor();
                            String playerFaction = player_.getFaction();
                            if (playerColor != null && playerColor.equals(promissoryNoteOwner.getColor()) ||
                                    playerFaction != null && playerFaction.equals(promissoryNoteOwner.getFaction())) {
                                name.append(playerFaction).append(" (").append(playerColor).append(")");
                            }
                        }
                    }
                    boolean multiScoring = false;
                    drawScoreControlMarkers(x + 515, y, activeMap, players, Collections.singletonList(player.getUserID()), multiScoring, 1, true, graphics, userVPs);
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
            Map activeMap,
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
                    drawScoreControlMarkers(x + 515, y, activeMap, players, scoredPlayerID, false, objectiveWorth, justCalculate, true, graphics, userVPs);
                } else {
                    drawScoreControlMarkers(x + 515, y, activeMap, players, scoredPlayerID, multiScoring, objectiveWorth, justCalculate, graphics, userVPs);
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
            Map activeMap,
            LinkedHashMap<String, Player> players,
            List<String> scoredPlayerID,
            boolean multiScoring,
            Integer objectiveWorth,
            boolean justCalculate,
            Graphics graphics,
            HashMap<Player, Integer> userVPs
    ) {
        drawScoreControlMarkers(x, y, activeMap, players, scoredPlayerID, multiScoring, objectiveWorth, justCalculate, false, graphics, userVPs);
    }

    private void drawScoreControlMarkers(
            int x,
            int y,
            Map activeMap,
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

                boolean convertToGeneric = isFoWPrivate != null && isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, fowPlayer);
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
            BotLogger.log("Could not parse custodian CV token file", e);
        }
    }

    private static String getFactionByControlMarker(Collection<Player> players, String controlID) {
        String faction = "";
        for (Player player_ : players) {
            if (player_.getColor() != null && player_.getFaction() != null) {
                String playerControlMarker = Mapper.getControlID(player_.getColor());
                String playerCC = Mapper.getCCID(player_.getColor());
                String playerSweep = Mapper.getSweepID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC) || controlID.equals(playerSweep)) {
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
                String playerSweep = Mapper.getSweepID(player_.getColor());
                if (controlID.equals(playerControlMarker) || controlID.equals(playerCC) || controlID.equals(playerSweep)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    private Color getSCColor(int sc, Map activeMap) {
        HashMap<Integer, Boolean> scPlayed = activeMap.getScPlayed();
        if (scPlayed.get(sc) != null) {
            if (scPlayed.get(sc)) {
                return Color.GRAY;
            }
        }
        return getSCColor(sc);
    }

    private Color getSCColor(Integer sc) {
        String scString = sc.toString();
        Integer scGroup = Integer.parseInt(StringUtils.left(scString, 1));
        return switch (scGroup) {
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
                return new Color(113, 126, 152);
            case "grey":
                return new Color(113, 126, 152);
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
            case "petrol":
                return new Color(62, 128, 133);
            case "brown":
                return new Color(112, 78, 42);
            case "tan":
                return new Color(180, 168, 121);
            case "forest":
                return new Color(93, 151, 102);
            case "chrome":
                return new Color(186, 193, 195);
            case "sunset":
                return new Color(173, 106, 248);
            case "torquoise":
                return new Color(37, 255, 232);
            case "gold":
                return new Color(215, 1, 247);
            case "lightgray":
                return new Color(213, 213, 213);
            case "bloodred":
                return Color.decode("#70001a");
            case "chocolate":
                return Color.decode("#3a1d19");
            case "teal":
                return Color.decode("#00deff");
            case "emerald":
                return Color.decode("#004018");
            case "navy":
                return Color.decode("#03004b");
            case "lime":
                return Color.decode("#ace3a0");
            case "lavender":
                return Color.decode("#9796df");
            case "rose":
                return Color.decode("#d59de2");
            case "spring":
                return Color.decode("#cedd8e");
            default:
                return Color.WHITE;
        }
    }

    enum TileStep {
        Setup, Tile, Extras, Units
    }

    private void addTile(Tile tile, Map activeMap, TileStep step) {
        addTile(tile, activeMap, step, false);
    }

    private void addTile(Tile tile, Map activeMap, TileStep step, boolean setupCheck) {
        if (tile == null || tile.getTileID() == null){
            return;
        }
        try {
            String position = tile.getPosition();
            Point positionPoint = PositionMapper.getTilePosition(position);
            if (positionPoint == null) {
                if(tile.getTileID().equalsIgnoreCase("-1")){
                    return;
                }
                throw new Exception("Could not map tile to a position on the map: " + activeMap.getName());
            }

            int x = positionPoint.x;
            int y = positionPoint.y;
            if (!setupCheck) {
                if (!position.equalsIgnoreCase("tl") &&
                    !position.equalsIgnoreCase("tr") &&
                    !position.equalsIgnoreCase("bl") &&
                    !position.equalsIgnoreCase("br")) 
                {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            } else if (x < minX || x > maxX || y < minY || y > maxY) {
                return;
            }

            positionPoint = getTilePosition(activeMap, position, x, y);
            int tileX = positionPoint.x + extraX - TILE_PADDING;
            int tileY = positionPoint.y + extraY - TILE_PADDING;

            BufferedImage tileImage = partialTileImage(tile, activeMap, step, fowPlayer, isFoWPrivate);
            graphics.drawImage(tileImage, tileX, tileY, null);
        } catch (IOException e) {
            BotLogger.log("Error drawing tile: " + tile.getTileID(), e);
        } catch (Exception exception) {
            BotLogger.log("Tile Error, when building map: " + tile.getTileID(), exception);
        }
    }


    public static BufferedImage partialTileImage(Tile tile, Map activeMap, TileStep step, Player frogPlayer, Boolean isFrogPrivate) throws IOException {
        BufferedImage tileOutput = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        

        BufferedImage image = ImageIO.read(new File(tile.getTilePath()));
        BufferedImage fogOfWar = ImageIO.read(new File(tile.getFowTilePath(frogPlayer)));
        boolean tileIsFoggy = isFrogPrivate != null && isFrogPrivate && tile.hasFog(frogPlayer);
        String position = tile.getPosition();

        Graphics tileGraphics = tileOutput.createGraphics();
        switch (step) {
            case Setup -> {} //do nothing
            case Tile -> {
                tileGraphics.drawImage(image, TILE_PADDING, TILE_PADDING, null);

                //ADD ANOMALY BORDER IF HAS ANOMALY PRODUCING TOKENS OR UNITS
                List<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
                for (UnitHolder unitHolder : unitHolders) {
                    boolean drawAnomaly = false;
                    Set<String> tokenList = unitHolder.getTokenList();
                    if (CollectionUtils.containsAny(tokenList, "token_gravityrift.png", "token_ds_wound.png", "token_ds_sigil.png", "token_anomalydummy.png")) {
                        drawAnomaly = true;
                    }
                    Set<String> unitList = unitHolder.getUnits().keySet();
                    for (String unit : unitList) {
                        if (unit.contains("csd.png")) drawAnomaly = true;
                    }
                    if (drawAnomaly) {
                        BufferedImage anomalyImage = ImageIO.read(new File(ResourceHelper.getInstance().getTileFile("tile_anomaly.png")));
                        tileGraphics.drawImage(anomalyImage, TILE_PADDING, TILE_PADDING, null);
                    }
                }

                int textOffset = 0;
                if (activeMap.getLargeText().equals("large")) {
                    tileGraphics.setFont(Storage.getFont50());
                    textOffset = 160;
                } else if (activeMap.getLargeText().equals("medium")) {
                    tileGraphics.setFont(Storage.getFont35());
                    textOffset = 40;
                } else {
                    tileGraphics.setFont(Storage.getFont20());
                    textOffset = 20;
                }
                tileGraphics.setColor(Color.WHITE);
                if (tileIsFoggy) {
                    tileGraphics.drawImage(fogOfWar, TILE_PADDING, TILE_PADDING, null);
                    tileGraphics.drawString(tile.getFogLabel(frogPlayer), TILE_PADDING + labelPositionPoint.x, TILE_PADDING + labelPositionPoint.y);
                }
                tileGraphics.drawString(position, TILE_PADDING + tilePositionPoint.x - textOffset, TILE_PADDING + tilePositionPoint.y);
            }
            case Extras -> {
                if (tileIsFoggy) return tileOutput;
                
                String primaryTile = position;
                List<String> adj = activeMap.getAdjacentTileOverrides(primaryTile);
                int direction = 0;
                for (String secondaryTile : adj) {
                    if (secondaryTile != null) {
                        addAdjacencyArrow(tile, direction, secondaryTile, tileGraphics, activeMap);
                    }
                    direction++;
                }
            }
            case Units -> {
                if (tileIsFoggy) return tileOutput;

                ArrayList<Rectangle> rectangles = new ArrayList<>();
                Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
                UnitHolder spaceUnitHolder = unitHolders.stream().filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);

                if (spaceUnitHolder != null) {
                    addSleeperToken(tile, tileGraphics, spaceUnitHolder, GenerateMap::isValidCustodianToken);
                    addToken(tile, tileGraphics, spaceUnitHolder);
                    unitHolders.remove(spaceUnitHolder);
                    unitHolders.add(spaceUnitHolder);
                }
                for (UnitHolder unitHolder : unitHolders) {
                    addSleeperToken(tile, tileGraphics, unitHolder, GenerateMap::isValidToken);
                    addControl(tile, tileGraphics, unitHolder, rectangles, activeMap, frogPlayer, isFrogPrivate);
                }
                if (spaceUnitHolder != null) {
                    addCC(tile, tileGraphics, spaceUnitHolder, activeMap, frogPlayer, isFrogPrivate);
                }
                int degree = 180;
                int degreeChange = 5;
                for (UnitHolder unitHolder : unitHolders) {
                    int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                    if (unitHolder != spaceUnitHolder) {
                        addPlanetToken(tile, tileGraphics, unitHolder, rectangles);
                    }
                    addUnits(tile, tileGraphics, rectangles, degree, degreeChange, unitHolder, radius, activeMap, frogPlayer);
                }
            }
        }
        return tileOutput;
    }

    private static Point getTilePosition(Map activeMap, String position, int x, int y) {
        int ringCount = activeMap.getRingCount();
        if (ringCount < RING_MAX_COUNT) {
            int lower = RING_MAX_COUNT - ringCount;

            if (position.equalsIgnoreCase("tl")) {
                y -= 150;
            } else if (position.equalsIgnoreCase("bl")) {
                y -= lower * 600 - 150;
            } else if (position.equalsIgnoreCase("tr")) {
                x -= lower * 520;
                y -= 150;
            } else if (position.equalsIgnoreCase("br")) {
                x -= lower * 520;
                y -= lower * 600 - 150;
            } else {
                x -= lower * 260;
                y -= lower * 300;
            }
            return new Point(x, y);
        }
        return new Point(x, y);
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, float percent) throws IOException {
        int scaledWidth = (int) (originalImage.getWidth() * percent);
        int scaledHeight = (int) (originalImage.getHeight() * percent);
        Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT);
        BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    private static void addAdjacencyArrow(Tile tile, int direction, String secondaryTile, Graphics tileGraphics, Map activeMap) {
        int deltaX = 0;
        int deltaY = 0;
        int textOffsetX = 12;
        int textOffsetY = 40;
        BufferedImage arrowImage = null;

        int degrees = 0;
        switch (direction) {
            case 0 -> {
                deltaX = 118;
                deltaY = -28;
                degrees = 0;
            }
            case 1 -> {
                deltaX = 257;
                deltaY = 26;
                degrees = 60;
            }
            case 2 -> {
                deltaX = 283;
                deltaY = 167;
                degrees = 300;
                textOffsetY = 25;
            }
            case 3 -> {
                deltaX = 167;
                deltaY = 273;
                degrees = 0;
                textOffsetY = 25;
            }
            case 4 -> {
                deltaX = 28;
                deltaY = 210;
                degrees = 60;
                textOffsetY = 25;
            }
            case 5 -> {
                deltaX = 0;
                deltaY = 69;
                degrees = 300;
            }
        }
        try {
            BufferedImage outputImage = ImageIO.read(new File(Helper.getAdjacencyOverridePath(direction)));
            arrowImage = outputImage;

            Graphics arrow = arrowImage.getGraphics();

            arrow.setFont(Storage.getFont16());
            if (secondaryTile.length() > 3) {
                arrow.setFont(Storage.getFont14());
            }
            arrow.setColor(Color.BLACK);
            arrow.drawString(secondaryTile, textOffsetX, textOffsetY);

            double rotation = Math.toRadians(degrees);
            double rotateX = arrowImage.getWidth() / 2;
            double rotateY = arrowImage.getHeight() / 2;
            AffineTransform tx = AffineTransform.getRotateInstance(rotation, rotateX, rotateY);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);

            tileGraphics.drawImage(op.filter(arrowImage, null), TILE_PADDING + deltaX, TILE_PADDING + deltaY, null);
        } catch (Exception e) {}
    }

    private static void addCC(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Map activeMap, Player frogPlayer, Boolean isFrogPrivate) {
        BufferedImage image = null;
        HashSet<String> ccList = unitHolder.getCCList();
        int deltaX = 0;
        int deltaY = 0;
        for (String ccID : ccList) {
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
                continue;
            }
            try {
                image = ImageIO.read(new File(ccPath));

                Point centerPosition = unitHolder.getHolderCenterPosition();

                String faction = getFactionByControlMarker(activeMap.getPlayers().values(), ccID);
                Player player = getPlayerByControlMarker(activeMap.getPlayers().values(), ccID);
                BufferedImage factionImage = null;
                if (faction != null) {
                    boolean convertToGeneric = isFrogPrivate != null && isFrogPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, frogPlayer);
                    if (!convertToGeneric || frogPlayer != null && frogPlayer.getFaction().equals(faction)) {
                        String factionImagePath = Mapper.getCCPath("control_faction_" + faction + ".png");
                        if (factionImagePath != null) {
                            factionImage = ImageIO.read(new File(factionImagePath));
                        }
                    }
                }

                boolean generateImage = true;
                if (ccID.startsWith("sweep")) {
                    factionImage = null;
                    if (player != frogPlayer) {
                        generateImage = false;
                    }
                }

                if (generateImage) {
                    int imgX = TILE_PADDING + 10 + deltaX;
                    int imgY = TILE_PADDING + centerPosition.y - 40 + deltaY;
                    tileGraphics.drawImage(image, imgX, imgY, null);
                    if (factionImage != null) {
                        tileGraphics.drawImage(factionImage, imgX + DELTA_X, imgY + DELTA_Y, null);
                    }
                }
            } catch (Exception e) {}

            if (image != null) {
                deltaX += image.getWidth() / 5;
                deltaY += image.getHeight() / 4;
            }
        }
    }

    private static void addControl(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, ArrayList<Rectangle> rectangles, Map activeMap, Player frogPlayer, Boolean isFrogPrivate) {
        BufferedImage controlToken = null;
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
                String faction = getFactionByControlMarker(activeMap.getPlayers().values(), controlID);
                Player player = getPlayerByControlMarker(activeMap.getPlayers().values(), controlID);

                String controlPath = tile.getCCPath(controlID);
                if (controlPath == null) {
                    BotLogger.log("Could not parse control token file for: " + controlID);
                    continue;
                }
                try {
                    factionImage = null;
                    if (faction != null) {
                        boolean convertToGeneric = isFrogPrivate != null && isFrogPrivate && !FoWHelper.canSeeStatsOfPlayer(activeMap, player, frogPlayer);
                        if (!convertToGeneric || frogPlayer != null && frogPlayer.getFaction().equals(faction)) {
                            String factionImagePath = tile.getCCPath("control_faction_" + faction + ".png");
                            if (factionImagePath != null) {
                                factionImage = ImageIO.read(new File(factionImagePath));
                            }
                        }
                    }
                    controlToken = ImageIO.read(new File(controlPath));
                } catch (Exception e) {
                    BotLogger.log("Could not parse control token file for: " + controlID, e);
                    return;
                }

                boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);
                Point position = unitTokenPosition.getPosition(controlID);
                if (isMirage) {
                    if (position == null) {
                        position = new Point(Constants.MIRAGE_POSITION.x, Constants.MIRAGE_POSITION.y);
                    } else {
                        position.x += Constants.MIRAGE_POSITION.x;
                        position.y += Constants.MIRAGE_POSITION.y;
                    }
                }

                if (position != null) {
                    int imgX = TILE_PADDING + position.x;
                    int imgY = TILE_PADDING + position.y;
                    tileGraphics.drawImage(controlToken, imgX, imgY, null);
                    if (factionImage != null) {
                        tileGraphics.drawImage(factionImage, imgX, imgY, null);
                    }
                    rectangles.add(new Rectangle(imgX, imgY, controlToken.getWidth(), controlToken.getHeight()));
                } else {
                    int imgX = TILE_PADDING + centerPosition.x + xDelta;
                    int imgY = TILE_PADDING + centerPosition.y;
                    tileGraphics.drawImage(controlToken, imgX, imgY, null);
                    if (factionImage != null) {
                        tileGraphics.drawImage(factionImage, imgX, imgY, null);
                    }
                    rectangles.add(new Rectangle(imgX, imgY, controlToken.getWidth(), controlToken.getHeight()));
                    xDelta += 10;
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, controlList);
        }
    }

    private static void addSleeperToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, Function<String, Boolean> isValid) {
        BufferedImage tokenImage = null;
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
                    tokenImage = resizeImage(ImageIO.read(new File(tokenPath)), scale);
                    Point position = new Point(centerPosition.x - (tokenImage.getWidth() / 2), centerPosition.y - (tokenImage.getHeight() / 2));
                    if (tokenID.contains(Constants.CUSTODIAN_TOKEN)) {
                        position = new Point(70, 45);
                    } else if (tokenID.contains(Constants.SLEEPER) && containsDMZ) {
                        position = new Point(position.x + 10, position.y + 10);
                    }
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y - 10, null);
                } catch (Exception e) {
                    BotLogger.log("Could not parse sleeper token file for: " + tokenID, e);
                }
            }
        }
    }

    private static boolean isValidToken(String tokenID) {
        return tokenID.contains(Constants.SLEEPER) ||
                tokenID.contains(Constants.DMZ_LARGE) ||
                tokenID.contains(Constants.WORLD_DESTROYED) ||
                tokenID.contains(Constants.GLEDGE_CORE) ||
                tokenID.contains(Constants.CUSTODIAN_TOKEN) ||
                tokenID.contains(Constants.CONSULATE_TOKEN);
    }

    private static boolean isValidCustodianToken(String tokenID) {
        return tokenID.contains(Constants.CUSTODIAN_TOKEN);
    }

    private static void addPlanetToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, ArrayList<Rectangle> rectangles) {
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
                    BotLogger.log("Could not parse token file for: " + tokenID + " on tile: " + tile.getRepresentationForAutoComplete());
                    continue;
                }

                try {
                    BufferedImage image = resizeImage(ImageIO.read(new File(tokenPath)), 1.0f);
                    if (tokenPath.contains(Constants.DMZ_LARGE) ||
                        tokenPath.contains(Constants.WORLD_DESTROYED) ||
                        tokenPath.contains(Constants.CONSULATE_TOKEN) ||
                        tokenPath.contains(Constants.GLEDGE_CORE))
                    {
                        tileGraphics.drawImage(image, TILE_PADDING + centerPosition.x - (image.getWidth() / 2), TILE_PADDING + centerPosition.y - (image.getHeight() / 2), null);
                    } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                        tileGraphics.drawImage(image, TILE_PADDING + 70, TILE_PADDING + 45, null);
                    } else {
                        Point position = unitTokenPosition.getPosition(tokenID);
                        if (position != null) {
                            tileGraphics.drawImage(image, TILE_PADDING + position.x, TILE_PADDING + position.y, null);
                            rectangles.add(new Rectangle(TILE_PADDING + position.x, TILE_PADDING + position.y, image.getWidth(), image.getHeight()));
                        } else {
                            tileGraphics.drawImage(image, TILE_PADDING + centerPosition.x + xDelta, TILE_PADDING + centerPosition.y, null);
                            rectangles.add(new Rectangle(TILE_PADDING + centerPosition.x + xDelta, TILE_PADDING + centerPosition.y, image.getWidth(), image.getHeight()));
                            xDelta += 10;
                        }
                    }
                } catch (Exception e) {
                    BotLogger.log("Could not parse control token file for: " + tokenID, e);
                }
            }
        } else {
            oldFormatPlanetTokenAdd(tile, tileGraphics, unitHolder, tokenList);
        }
    }

    private static void oldFormatPlanetTokenAdd(Tile tile, Graphics tileGraphics, UnitHolder unitHolder, ArrayList<String> tokenList) {
        int deltaY = 0;
        int offSet = 0;
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = centerPosition.x;
        int y = centerPosition.y - (tokenList.size() > 1 ? 35 : 0);
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                BotLogger.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                BufferedImage image = resizeImage(ImageIO.read(new File(tokenPath)), 0.85f);
                tileGraphics.drawImage(image, TILE_PADDING + x - (image.getWidth() / 2), TILE_PADDING + y + offSet + deltaY - (image.getHeight() / 2), null);
                y += image.getHeight();
            } catch (Exception e) {
                BotLogger.log("Could not parse control token file for: " + tokenID, e);
            }
        }
    }

    private static void addToken(Tile tile, Graphics tileGraphics, UnitHolder unitHolder) {
        HashSet<String> tokenList = unitHolder.getTokenList();
        Point centerPosition = unitHolder.getHolderCenterPosition();
        int x = 0;
        int y = 0;
        int deltaX = 80;
        int deltaY = 0;
        List<Point> spaceTokenPositions = PositionMapper.getSpaceTokenPositions(tile.getTileID());
        if (spaceTokenPositions.isEmpty()) {
            x = centerPosition.x;
            y = centerPosition.y;
        }
        int index = 0;
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                BotLogger.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                BufferedImage tokenImage = resizeImage(ImageIO.read(new File(tokenPath)), 1.0f);
                if (tokenPath.contains(Constants.MIRAGE)) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + Constants.MIRAGE_POSITION.x, TILE_PADDING + Constants.MIRAGE_POSITION.y, null);
                } else if (tokenPath.contains(Constants.SLEEPER)) {
                    tileGraphics.drawImage(tokenImage, TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2), TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2), null);
                } else {
                    if (spaceTokenPositions.size() > index) {
                        Point point = spaceTokenPositions.get(index);
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + x + point.x, TILE_PADDING + y + point.y, null);
                        index++;
                    } else {
                        tileGraphics.drawImage(tokenImage, TILE_PADDING + x + deltaX, TILE_PADDING + y + deltaY, null);
                        deltaX += 30;
                        deltaY += 30;
                    }
                }
            } catch (Exception e) {
                BotLogger.log("Could not parse control token file for: " + tokenID, e);
            }
        }
    }

    private static void addUnits(Tile tile, Graphics tileGraphics, ArrayList<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius, Map activeMap, Player frogPlayer) {
        BufferedImage image = null;
        HashMap<String, Integer> tempUnits = new HashMap<>(unitHolder.getUnits());
        LinkedHashMap<String, Integer> units = new LinkedHashMap<>();
        HashMap<String, Point> unitOffset = new HashMap<>();
        boolean isSpace = unitHolder.getName().equals(Constants.SPACE);

        boolean isCabalJail = "s11".equals(tile.getTileID());
        boolean isNekroJail = "s12".equals(tile.getTileID());
        boolean isYssarilJail = "s13".equals(tile.getTileID());

        boolean isJail = isCabalJail || isNekroJail || isYssarilJail;
        boolean showJail = false;
        if (frogPlayer == null
                || (isCabalJail && FoWHelper.canSeeStatsOfFaction(activeMap, "cabal", frogPlayer))
                || (isNekroJail && FoWHelper.canSeeStatsOfFaction(activeMap, "nekro", frogPlayer))
                || (isYssarilJail && FoWHelper.canSeeStatsOfFaction(activeMap, "yssaril", frogPlayer))
        ) {
            showJail = true;
        }

        Point unitOffsetValue = activeMap.isAllianceMode() ? PositionMapper.getAllianceUnitOffset() : PositionMapper.getUnitOffset();
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
            BotLogger.log("Could not parse damage token file.", e);
        }

        boolean isMirage = unitHolder.getName().equals(Constants.MIRAGE);

        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
            String unitID = unitEntry.getKey();
            Integer unitCount = unitEntry.getValue();

            if (isJail && frogPlayer != null) {
                String colorID = Mapper.getColorID(frogPlayer.getColor());
                if (!showJail && frogPlayer != null && !unitID.startsWith(colorID)) {
                    continue;
                }
            }

            Integer unitDamageCount = unitDamage.get(unitID);

            Color groupUnitColor = Color.WHITE;
            Integer bulkUnitCount = null;
            if (unitID.startsWith("ylw") || unitID.startsWith("org") || unitID.startsWith("pnk")
                    || unitID.startsWith("tan") || unitID.startsWith("crm") || unitID.startsWith("sns") || unitID.startsWith("tqs")
                    || unitID.startsWith("gld") || unitID.startsWith("lme") || unitID.startsWith("lvn") || unitID.startsWith("rse")
                    || unitID.startsWith("spr") || unitID.startsWith("tea") || unitID.startsWith("lgy")) {
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
                BotLogger.log("Could not parse unit file for: " + unitID, e);
                continue;
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
                    int possibleX = centerPosition.x + x - (image.getWidth() / 2);
                    int possibleY = centerPosition.y + y - (image.getHeight() / 2);
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
                int xOriginal = centerPosition.x + x;
                int yOriginal = centerPosition.y + y;
                int imageX = position != null ? position.x : xOriginal - (image.getWidth() / 2);
                int imageY = position != null ? position.y : yOriginal - (image.getHeight() / 2);
                if (isMirage) {
                    imageX += Constants.MIRAGE_POSITION.x;
                    imageY += Constants.MIRAGE_POSITION.y;
                }
                tileGraphics.drawImage(image, TILE_PADDING + imageX, TILE_PADDING + imageY, null);
                if (bulkUnitCount != null) {
                    tileGraphics.setFont(Storage.getFont24());
                    tileGraphics.setColor(groupUnitColor);
                    int scaledNumberPositionX = (int) (numberPositionPoint.x * scaleOfUnit);
                    int scaledNumberPositionY = (int) (numberPositionPoint.y * scaleOfUnit);
                    tileGraphics.drawString(Integer.toString(bulkUnitCount), TILE_PADDING + imageX + scaledNumberPositionX, TILE_PADDING + imageY + scaledNumberPositionY);
                }

                if (unitDamageCount != null && unitDamageCount > 0 && dmgImage != null) {
                    if (isSpace && position != null) {
                        position.x = position.x - 7;
                    }
                    int imageDmgX = position != null ? position.x + (image.getWidth() / 2) - (dmgImage.getWidth() / 2) : xOriginal - (dmgImage.getWidth() / 2);
                    int imageDmgY = position != null ? position.y + (image.getHeight() / 2) - (dmgImage.getHeight() / 2) : yOriginal - (dmgImage.getHeight() / 2);
                    if (isMirage) {
                        imageDmgX = imageX;
                        imageDmgY = imageY;
                    } else if (unitID.contains("_mf")) {
                        imageDmgX = position != null ? position.x : xOriginal - (dmgImage.getWidth());
                        imageDmgY = position != null ? position.y : yOriginal - (dmgImage.getHeight());

                    }
                    tileGraphics.drawImage(dmgImage, TILE_PADDING + imageDmgX, TILE_PADDING + imageDmgY, null);
                    unitDamageCount--;
                }
            }
        }
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
