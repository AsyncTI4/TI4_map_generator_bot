package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.map.*;
import ti4.map.Map;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

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
import java.util.*;
import java.util.List;

public class StartMilty extends MiltySubcommandData {

    public static final int SLICE_GENERATION_CYCLES = 100;
    private static List<String> sliceNames = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I");

    private boolean anomalies_can_touch = false;

    public StartMilty() {
        super(Constants.START, "Start Milty Draft");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FACTION_COUNT, "Faction Count").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ANOMALIES_CAN_TOUCH, "Anomalies can touch"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int sliceCount = activeMap.getPlayerCountForMap() + 2;
        if (sliceOption != null) {
            sliceCount = sliceOption.getAsInt();
        }
        if (sliceCount > 9) {
            sliceCount = 9;
        }
        int factionCount = activeMap.getPlayerCountForMap() + 2;
        OptionMapping factionOption = event.getOption(Constants.FACTION_COUNT);
        if (factionOption != null) {
            factionCount = factionOption.getAsInt();
        }
        if (factionCount > 25) {
            factionCount = 25;
        }

        List<String> factions = new ArrayList<>(Mapper.getFactions());
        List<String> factionDraft = createFactionDraft(factionCount, factions);

        OptionMapping anomaliesCanTouchOption = event.getOption(Constants.ANOMALIES_CAN_TOUCH);
        if (anomaliesCanTouchOption != null) {
            anomalies_can_touch = anomaliesCanTouchOption.getAsBoolean();
        }

        HashMap<String, String> miltyDraftTiles = Mapper.getMiltyDraftTiles();
        MiltyDraftManager draftManager = activeMap.getMiltyDraftManager();
        draftManager.clear();
        initDraftTiles(miltyDraftTiles, draftManager);


        boolean slicesCreated = generateSlices(sliceCount, draftManager);
        if (!slicesCreated) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Did not find correct slices, check settings");
        } else {
            File file = generateImage(draftManager);

            MessageHelper.sendFileToChannel(event.getChannel(), file);

            StringBuilder factionMsg = new StringBuilder();
            for (String faction : factionDraft) {
                factionMsg.append(Helper.getFactionIconFromDiscord(faction)).append(" ");
            }

            MessageHelper.sendMessageToChannel(event.getChannel(), factionMsg.toString());
        }
    }

    private List<String> createFactionDraft(int factionCount, List<String> factions) {
        factions.remove("lazax");
        Collections.shuffle(factions);
        Collections.shuffle(factions);
        Collections.shuffle(factions);
        List<String> factionDraft = new ArrayList<>();
        for (int i = 0; i < factionCount; i++) {
            factionDraft.add(factions.get(i));
        }
        return factionDraft;
    }

    private File generateImage(MiltyDraftManager draftManager) {
        List<MiltyDraftSlice> slices = draftManager.getSlices();
        int sliceCount = slices.size();
        float scale = 1.0f;
        int scaled = (int) (900 * scale);
        int width = scaled * 5;
        int height = scaled * (sliceCount > 5 ? sliceCount > 10 ? 3 : 2 : 1);
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();
        BufferedImage sliceImage = new BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = sliceImage.getGraphics();

        Point equadistant = new Point(0, 150);
        Point left = new Point(0, 450);
        Point farFront = new Point(260, 0);
        Point front = new Point(260, 300);
        Point hs = new Point(260, 600);
        Point right = new Point(520, 450);


        File file = Storage.getMapImageStorage("temp_slice.png");
        try {
            int index = 0;
            int deltaX = 0;
            int deltaY = 0;
            for (MiltyDraftSlice slice : slices) {
                MiltyDraftTile leftSlice = slice.getLeft();
                BufferedImage image = ImageIO.read(new File(leftSlice.getTile().getTilePath()));
                graphics.drawImage(image, left.x, left.y, null);

                MiltyDraftTile equadistantSlice = slice.getEquadistant();
                image = ImageIO.read(new File(equadistantSlice.getTile().getTilePath()));
                graphics.drawImage(image, equadistant.x, equadistant.y, null);

                MiltyDraftTile farFrontSlice = slice.getFarFront();
                image = ImageIO.read(new File(farFrontSlice.getTile().getTilePath()));
                graphics.drawImage(image, farFront.x, farFront.y, null);

                MiltyDraftTile frontSlice = slice.getFront();
                image = ImageIO.read(new File(frontSlice.getTile().getTilePath()));
                graphics.drawImage(image, front.x, front.y, null);

                MiltyDraftTile rightSlice = slice.getRight();
                image = ImageIO.read(new File(rightSlice.getTile().getTilePath()));
                graphics.drawImage(image, right.x, right.y, null);
                String tileFile = ResourceHelper.getInstance().getTileFile("00_green.png");
                if (tileFile != null) {
                    image = ImageIO.read(new File(tileFile));
                    graphics.drawImage(image, hs.x, hs.y, null);
                }

                graphics.setColor(Color.WHITE);
                graphics.setFont(Storage.getFont64());
                graphics.drawString(slice.getName(), hs.x + 150 , hs.y + 60);

                graphics.setFont(Storage.getFont50());
                int resources = leftSlice.getResources() + rightSlice.getResources() + equadistantSlice.getResources() + farFrontSlice.getResources() + frontSlice.getResources();
                int influence = leftSlice.getInfluence() + rightSlice.getInfluence() + equadistantSlice.getInfluence() + farFrontSlice.getInfluence() + frontSlice.getInfluence();
                double resourcesMilty = leftSlice.getMilty_resources() + rightSlice.getMilty_resources() + equadistantSlice.getMilty_resources() + farFrontSlice.getMilty_resources() + frontSlice.getMilty_resources();
                double influenceMilty = leftSlice.getMilty_influence() + rightSlice.getMilty_influence() + equadistantSlice.getMilty_influence() + farFrontSlice.getMilty_influence() + frontSlice.getMilty_influence();

                graphics.drawString(resources + "/" +influence, hs.x + 130 , hs.y + 130);
                graphics.drawString("("+resourcesMilty + "/" +influenceMilty+")", hs.x + 70 , hs.y + 190);



                BufferedImage resizedSlice = GenerateMap.resizeImage(sliceImage, scale);


                graphicsMain.drawImage(resizedSlice, deltaX, deltaY, null);

//                graphics.setFont(Storage.getFont32());
//                graphics.setColor(Color.WHITE);
//                String timeStamp = getTimeStamp();
//                graphics.drawString(map.getName() + " " + timeStamp, 0, 34);
                index++;

                int heightSlice = resizedSlice.getHeight();
                int widthSlice = resizedSlice.getWidth();

                deltaX += widthSlice;
                if (index % 5 == 0) {
                    deltaY += heightSlice;
                    deltaX = 0;
                }
            }
            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("png").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(file));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(0.01f);
            }

            imageWriter.write(null, new IIOImage(mainImage, null, null), defaultWriteParam);
        } catch (IOException e) {
            BotLogger.log("Could not save generated slice image", e);
        }
        Map map = getActiveMap();
        String absolutePath = file.getParent() + "/" + map.getName() + "_slices.jpg";
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
            BotLogger.log("Could not save jpg file", e);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        File jpgFile = new File(absolutePath);
        MapFileDeleter.addFileToDelete(jpgFile);
        return jpgFile;
    }


    private boolean generateSlices(int sliceCount, MiltyDraftManager draftManager) {
        boolean slicesCreated = false;
        int i = 0;
        while (!slicesCreated && i < 100) {

            draftManager.clearSlices();

            List<MiltyDraftTile> high = draftManager.getHigh();
            List<MiltyDraftTile> mid = draftManager.getMid();
            List<MiltyDraftTile> low = draftManager.getLow();
            List<MiltyDraftTile> red = draftManager.getRed();

            Collections.shuffle(high);
            Collections.shuffle(mid);
            Collections.shuffle(low);
            Collections.shuffle(red);

            for (int j = 0; j < sliceCount; j++) {

                MiltyDraftSlice miltyDraftSlice = new MiltyDraftSlice();
                List<MiltyDraftTile> tiles = new ArrayList<>();
                tiles.add(high.remove(0));
                tiles.add(mid.remove(0));
                tiles.add(low.remove(0));
                MiltyDraftTile red1 = red.remove(0);
                MiltyDraftTile red2 = red.remove(0);
                tiles.add(red1);
                tiles.add(red2);
                boolean needToCheckAnomalies = red1.getTierList() == TierList.anomaly && red2.getTierList() == TierList.anomaly;
                Collections.shuffle(tiles);
                Collections.shuffle(tiles);
                Collections.shuffle(tiles);
                if (!anomalies_can_touch && needToCheckAnomalies) {
                    int emergencyIndex = 0;
                    while (emergencyIndex < 100) {

                        MiltyDraftTile draftLeft = tiles.get(0);
                        MiltyDraftTile draftFront = tiles.get(1);
                        MiltyDraftTile draftRight = tiles.get(2);
                        MiltyDraftTile draftEquadistant = tiles.get(3);
                        MiltyDraftTile draftFarFront = tiles.get(4);
                        if (draftLeft.getTierList() == TierList.anomaly && (draftFarFront.getTierList() == TierList.anomaly || draftRight.getTierList() == TierList.anomaly) ||
                                draftRight.getTierList() == TierList.anomaly && (draftFarFront.getTierList() == TierList.anomaly || draftEquadistant.getTierList() == TierList.anomaly)) {
                            break;
                        }
                        Collections.shuffle(tiles);
                        emergencyIndex++;
                    }
                }
                miltyDraftSlice.setLeft(tiles.remove(0));
                miltyDraftSlice.setFront(tiles.remove(0));
                miltyDraftSlice.setRight(tiles.remove(0));
                miltyDraftSlice.setEquadistant(tiles.remove(0));
                miltyDraftSlice.setFarFront(tiles.remove(0));

                //CHECK IF SLICES ARE OK HERE -------------------------------
                miltyDraftSlice.setName(sliceNames.get(j));
                draftManager.addSlice(miltyDraftSlice);
            }


            if (draftManager.getSlices().size() == sliceCount) {
                slicesCreated = true;
            }
            i++;
        }
        return slicesCreated;
    }

    private void initDraftTiles(HashMap<String, String> miltyDraftTiles, MiltyDraftManager draftManager) {
        for (java.util.Map.Entry<String, String> entry : miltyDraftTiles.entrySet()) {
            String tileID = entry.getKey();
            String[] values = entry.getValue().split(",");
            String tierList = values[0];


            MiltyDraftTile draftTile = new MiltyDraftTile();
            draftTile.setTierList(TierList.valueOf(tierList));

            if (values.length > 1) {
                String additionalInfo = values[1];
                if ("alpha".equals(additionalInfo)) {
                    draftTile.setHasAlphaWH(true);
                } else if ("beta".equals(additionalInfo)) {
                    draftTile.setHasBetaWH(true);
                } else if ("legendary".equals(additionalInfo)){
                    draftTile.setLegendary(true);
                }
            }

            Tile tile = new Tile(tileID, "none");
            draftTile.setTile(tile);

            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet planet) {
                    int resources = planet.getResources();
                    int influence = planet.getInfluence();
                    draftTile.addResources(resources);
                    draftTile.addInfluence(influence);
                    if (resources > influence) {
                        draftTile.addMilty_resources(resources);
                    }
                    if (influence > resources) {
                        draftTile.addMilty_influence(influence);
                    }
                    if (resources == influence) {
                        draftTile.addMilty_resources((double)resources / 2);
                        draftTile.addMilty_influence((double)influence / 2);
                    }
                }
            }
            draftManager.addDraftTile(draftTile);
        }
    }
}
