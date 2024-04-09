package ti4.commands.milty;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.ImageHelper;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.MapFileDeleter;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.model.TileModel;
import ti4.model.WormholeModel;
import ti4.model.Source.ComponentSource;

public class MiltyDraftHelper {

    public static void generateAndPostSlices(Game game) {
        MessageChannel mainGameChannel = game.getMainGameChannel();
        FileUpload fileUpload = generateImage(game);

        if (fileUpload == null) {
            MessageHelper.sendMessageToChannel(mainGameChannel, "There was an error building the slices image.");
        } else {
            MessageHelper.sendFileUploadToChannel(mainGameChannel, fileUpload, true);
        }
    }

    private static FileUpload generateImage(Game game) {
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        List<MiltyDraftSlice> slices = draftManager.getSlices();

        int sliceCount = slices.size();
        float scale = 1.0f;
        int scaled = (int) (900 * scale);
        int width = scaled * 5;
        int height = scaled * (sliceCount > 5 ? sliceCount > 10 ? 3 : 2 : 1);
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        File file = Storage.getMapImageStorage("temp_slice.png");
        if (file == null) return null;
        try {
            int index = 0;
            int deltaX = 0;
            int deltaY = 0;
            for (MiltyDraftSlice slice : slices) {
                BufferedImage sliceImage = generateSliceImage(slice);
                BufferedImage resizedSlice = ImageHelper.scale(sliceImage, scale);
                graphicsMain.drawImage(resizedSlice, deltaX, deltaY, null);
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
                defaultWriteParam.setCompressionQuality(1f);
            }

            imageWriter.write(null, new IIOImage(mainImage, null, null), defaultWriteParam);
        } catch (IOException e) {
            BotLogger.log("Could not save generated slice image", e);
        }

        String absolutePath = file.getParent() + "/" + game.getName() + "_slices.jpg";
        try (FileInputStream fileInputStream = new FileInputStream(file);
            FileOutputStream fileOutputStream = new FileOutputStream(absolutePath)) {

            BufferedImage image = ImageIO.read(fileInputStream);
            fileInputStream.close();

            BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(image, 0, 0, Color.black, null);

            ImageIO.write(convertedImage, "jpg", fileOutputStream);
        } catch (IOException e) {
            BotLogger.log("Could not save jpg file", e);
        }

        file.delete();
        File jpgFile = new File(absolutePath);
        FileUpload fileUpload = FileUpload.fromData(jpgFile, jpgFile.getName());
        MapFileDeleter.addFileToDelete(jpgFile);
        return fileUpload;
    }

    private static BufferedImage generateSliceImage(MiltyDraftSlice slice) throws IOException {
        Point equidistant = new Point(0, 150);
        Point left = new Point(0, 450);
        Point farFront = new Point(260, 0);
        Point front = new Point(260, 300);
        Point hs = new Point(260, 600);
        Point right = new Point(520, 450);

        System.out.println("Generating slice: " + slice.ttsString());

        BufferedImage sliceImage = new BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = sliceImage.getGraphics();

        BufferedImage image = ImageIO.read(new File(slice.getLeft().getTile().getTilePath()));
        graphics.drawImage(image, left.x, left.y, null);

        image = ImageIO.read(new File(slice.getFront().getTile().getTilePath()));
        graphics.drawImage(image, front.x, front.y, null);

        image = ImageIO.read(new File(slice.getRight().getTile().getTilePath()));
        graphics.drawImage(image, right.x, right.y, null);

        image = ImageIO.read(new File(slice.getEquidistant().getTile().getTilePath()));
        graphics.drawImage(image, equidistant.x, equidistant.y, null);

        image = ImageIO.read(new File(slice.getFarFront().getTile().getTilePath()));
        graphics.drawImage(image, farFront.x, farFront.y, null);

        String tileFile = ResourceHelper.getInstance().getTileFile("00_green.png");
        if (tileFile != null) {
            image = ImageIO.read(new File(tileFile));
            graphics.drawImage(image, hs.x, hs.y, null);
        }

        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont64());
        graphics.drawString(slice.getName(), hs.x + 150, hs.y + 60);

        graphics.setFont(Storage.getFont50());
        int resources = slice.getTotalRes();
        int influence = slice.getTotalInf();
        String totalsString = resources + "/" + influence;

        int resourcesMilty = slice.getOptimalRes();
        int influenceMilty = slice.getOptimalInf();
        int flexMilty = slice.getOptimalFlex();
        String optimalString = "(" + resourcesMilty + "/" + influenceMilty + " +" + flexMilty + ")";

        graphics.drawString(totalsString, hs.x + 130, hs.y + 130);
        graphics.drawString(optimalString, hs.x + 70, hs.y + 190);

        return sliceImage;
    }
    
    public static void initDraftTiles(MiltyDraftManager draftManager) {
        List<ComponentSource> defaultSources = Arrays.asList(
            ComponentSource.base, 
            ComponentSource.codex1,
            ComponentSource.codex2,
            ComponentSource.codex3,
            ComponentSource.pok, // TODO: JAZZ
            ComponentSource.ds); // temporarily include DS here
        initDraftTiles(draftManager, defaultSources);
    }

    public static void initDraftTiles(MiltyDraftManager draftManager, List<ComponentSource> sources) {
        List<TileModel> allTiles = new ArrayList<>(TileHelper.getAllTiles().values());
        for (TileModel tileModel : allTiles) {
            String tileID = tileModel.getId();
            if (isInvalid(tileModel, tileID)) {
                continue;
            }
            Set<WormholeModel.Wormhole> wormholes = tileModel.getWormholes();
            MiltyDraftTile draftTile = new MiltyDraftTile();
            if (wormholes != null) {
                for (WormholeModel.Wormhole wormhole : wormholes) {
                    if (WormholeModel.Wormhole.ALPHA == wormhole) {
                        draftTile.setHasAlphaWH(true);
                    } else if (WormholeModel.Wormhole.BETA == wormhole) {
                        draftTile.setHasBetaWH(true);
                    } else {
                        draftTile.setHasOtherWH(true);
                    }
                }
            }

            Tile tile = new Tile(tileID, "none");
            boolean sourceAllowed = false;
            if (sources.contains(tile.getTileModel().getSource())) sourceAllowed = true;

            // leaving these as a stop-gap for now until I can verify all sources are setup
            if (tileID.length() <= 2) sourceAllowed = true; 
            if (tileID.matches("d\\d{1,3}") && sources.contains(ComponentSource.ds)) sourceAllowed = true; 

            if (!sourceAllowed) continue;

            if (tile.isHomeSystem() || tile.getRepresentation().contains("Hyperlane") || tile.getRepresentation().contains("Keleres")) {
                continue;
            }

            draftTile.setTile(tile);
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet planet) {
                    draftTile.addPlanet(planet);
                }
            }

            if (tile.isAnomaly()) {
                draftTile.setTierList(TierList.anomaly);
            } else if (tile.getPlanetUnitHolders().size() == 0) {
                draftTile.setTierList(TierList.red);
            } else {
                draftTile.setTierList(TierList.high);
            }

            draftManager.addDraftTile(draftTile);
        }
    }

    private static boolean isInvalid(TileModel tileModel, String tileID) {
        if (tileModel.getTileBackOption().isPresent()) {
            String back = tileModel.getTileBackOption().orElse("");
            if (back.equals("red") || back.equals("blue")) {
                //good
            } else {
                return true;
            }
        }

        String id = tileID.toLowerCase();
        String path = tileModel.getTilePath() == null ? "" : tileModel.getTilePath().toLowerCase();
        List<String> disallowedTerms = List.of("corner", "lane", "mecatol", "blank", "border", "fow", "anomaly", "deltawh",
            "seed", "mr", "mallice", "ethan", "prison", "kwon", "home", "hs", "red", "blue", "green", "gray", "gate", "setup");
        return disallowedTerms.stream().anyMatch(term -> id.contains(term) || path.contains(term));
    }

    public static void buildPartialMap(Game game, GenericInteractionCreateEvent event) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        
        String mapTemplate = manager.getMapTemplate();
        if (mapTemplate == null) {
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(manager.getPlayers().size());
            if (defaultTemplate == null) {
                throw new Exception("idk how to build this map yet");
            }
            mapTemplate = defaultTemplate.getAlias();
        }

        MapTemplateHelper.buildPartialMapFromMiltyData(game, event, mapTemplate);
    }

    public static void buildMap(Game game) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        
        String mapTemplate = manager.getMapTemplate();
        if (mapTemplate == null) {
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(manager.getPlayers().size());
            if (defaultTemplate == null) {
                throw new Exception("idk how to build this map yet");
            }
            mapTemplate = defaultTemplate.getAlias();
        }

        MapTemplateHelper.buildMapFromMiltyData(game, mapTemplate);
    }
}
