package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.*;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class StartMilty extends MiltySubcommandData {

    public static final int SLICE_GENERATION_CYCLES = 100;

    public StartMilty() {
        super(Constants.START, "Start Milty Draft");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int sliceCount = activeMap.getPlayerCountForMap() + 2;
        if (sliceOption != null) {
             sliceCount = sliceOption.getAsInt();
        }

        HashMap<String, String> miltyDraftTiles = Mapper.getMiltyDraftTiles();
        MiltyDraftManager draftManager = activeMap.getMiltyDraftManager();
        initDraftTiles(miltyDraftTiles, draftManager);


        boolean slicesCreated = generateSlices(sliceCount, draftManager);
        if (!slicesCreated) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Did not find correct slices, check settings");
        } else {
            File file = generateImage(draftManager);

            MessageHelper.sendFileToChannel(event.getChannel(), file);
        }
    }

    private File generateImage(MiltyDraftManager draftManager) {
        List<MiltyDraftSlice> slices = draftManager.getSlices();
        int sliceCount = slices.size();
        float scale = 1.0f;
        int scaled = (int)(900 * scale);
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
            BotLogger.log("Could not save generated slice image");
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
            BotLogger.log("Could not save jpg file");
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
                tiles.add(red.remove(0));
                tiles.add(red.remove(0));
                Collections.shuffle(tiles);
                Collections.shuffle(tiles);
                Collections.shuffle(tiles);
                miltyDraftSlice.setLeft(tiles.remove(0));
                miltyDraftSlice.setFront(tiles.remove(0));
                miltyDraftSlice.setRight(tiles.remove(0));
                miltyDraftSlice.setEquadistant(tiles.remove(0));
                miltyDraftSlice.setFarFront(tiles.remove(0));

                //CHECK IF SLICES ARE OK HERE -------------------------------




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
                String wormhole = values[1];
                if ("alpha".equals(wormhole)) {
                    draftTile.setHasAlphaWH(true);
                } else if ("beta".equals(wormhole)) {
                    draftTile.setHasBetaWH(true);
                }
            }

            Tile tile = new Tile(tileID, "none");
            draftTile.setTile(tile);

            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet planet) {
                    int influence = planet.getInfluence();
                    int resources = planet.getResources();
                    if (resources > influence) {
                        draftTile.setResources(resources);
                    }
                    if (influence > resources) {
                        draftTile.setInfluence(influence);
                    }
                    if (resources == influence) {
                        draftTile.setResources(resources / 2);
                        draftTile.setInfluence(influence / 2);
                    }
                }
            }
            draftManager.addDraftTile(draftTile);
        }
    }
}
