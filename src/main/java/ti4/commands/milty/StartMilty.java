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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.ImageHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.MapFileDeleter;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.model.WormholeModel;

public class StartMilty extends MiltySubcommandData {

    public static final int SLICE_GENERATION_CYCLES = 100;

    private boolean anomalies_can_touch;

    public StartMilty() {
        super(Constants.START, "Start Milty Draft");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FACTION_COUNT, "Faction Count").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ANOMALIES_CAN_TOUCH, "Anomalies can touch"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        if (!activeGame.isTestBetaFeaturesMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Milty Draft in this bot is incomplete.\nEnable access by running `/game setup beta_test_mode: true`\nMost folks use [this website](https://milty.shenanigans.be/) to do the Milty Draft and import the TTPG string with `/map add_tile_list`");
            return;
        }

        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int sliceCount = activeGame.getPlayerCountForMap() + 2;
        if (sliceOption != null) {
            sliceCount = sliceOption.getAsInt();
        }
        if (sliceCount > 9) {
            sliceCount = 9;
        }
        int factionCount = activeGame.getPlayerCountForMap() + 2;
        OptionMapping factionOption = event.getOption(Constants.FACTION_COUNT);
        if (factionOption != null) {
            factionCount = factionOption.getAsInt();
        }
        if (factionCount > 25) {
            factionCount = 25;
        }

        List<String> factions = new ArrayList<>(Mapper.getFactions().stream()
            .filter(f -> f.getSource().isPok())
            .map(f -> f.getAlias()).toList());
        List<String> factionDraft = createFactionDraft(factionCount, factions);

        OptionMapping anomaliesCanTouchOption = event.getOption(Constants.ANOMALIES_CAN_TOUCH);
        if (anomaliesCanTouchOption != null) {
            anomalies_can_touch = anomaliesCanTouchOption.getAsBoolean();
        }

        MiltyDraftManager draftManager = activeGame.getMiltyDraftManager();
        draftManager.setFactionDraft(factionDraft);
        draftManager.clear();
        initDraftTiles(draftManager);
        initDraftOrder(draftManager, activeGame);

        boolean slicesCreated = generateSlices(sliceCount, draftManager);
        if (!slicesCreated) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Did not find correct slices, check settings");
        } else {
            FileUpload fileUpload = generateImage(draftManager);
            MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload);

            String message = "Slices:\n\n";
            MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(message);
            List<ActionRow> actionRow = null;
            List<Button> sliceButtons = new ArrayList<>(getMiltySliceButtons(activeGame));
            if (!sliceButtons.isEmpty()) actionRow = makeActionRows(sliceButtons);
            if (actionRow != null) baseMessageObject.addComponents(actionRow);

            MessageChannel eventChannel = event.getChannel();
            MessageChannel mainGameChannel = activeGame.getMainGameChannel() == null ? eventChannel : activeGame.getMainGameChannel();
            mainGameChannel.sendMessage(baseMessageObject.build()).queue();

            message = "Factions:\n\n";
            baseMessageObject = new MessageCreateBuilder().addContent(message);
            List<Button> factionButtons = new ArrayList<>(getMiltyFactionButtons(activeGame));
            if (!factionButtons.isEmpty()) actionRow = makeActionRows(factionButtons);
            if (actionRow != null) baseMessageObject.addComponents(actionRow);
            mainGameChannel.sendMessage(baseMessageObject.build()).queue();

            message = "Order:\n\n";
            baseMessageObject = new MessageCreateBuilder().addContent(message);
            List<Button> orderButtons = new ArrayList<>(getMiltyOrderButtons(activeGame));
            if (!orderButtons.isEmpty()) actionRow = makeActionRows(orderButtons);
            if (actionRow != null) baseMessageObject.addComponents(actionRow);
            mainGameChannel.sendMessage(baseMessageObject.build()).queue();

            message = "\n\nDraftOrder:\n\n";
            StringBuilder draftOrder = new StringBuilder();
            List<Player> draftRandomOrder = draftManager.getDraftRandomOrder();
            for (int index = 0; index < draftRandomOrder.size(); index++) {
                Player player = draftRandomOrder.get(index);
                //String playerPing = Helper.getPlayerPing(player);
                String userName = player.getUserName();
                draftOrder.append(index + 1).append(". ").append(userName).append("\n");
            }

            message += draftOrder.toString();
            baseMessageObject = new MessageCreateBuilder().addContent(message);
            mainGameChannel.sendMessage(baseMessageObject.build()).queue();

            //String playerPing = Helper.getPlayerPing(draftManager.getDraftOrderPlayer());
            mainGameChannel.sendMessage(draftManager.getDraftOrderPlayer().getUserName() + " is up!").queue();

        }
    }

    private void initDraftOrder(MiltyDraftManager draftManager, Game activeGame) {
        List<Player> players = activeGame.getPlayers().values().stream().filter(Player::isRealPlayer).collect(Collectors.toList());
        Collections.shuffle(players);
        Collections.shuffle(players);

        List<Player> playersReversed = new ArrayList<>(players);
        Collections.reverse(playersReversed);

        List<Player> draftOrder = new ArrayList<>(players);
        draftOrder.addAll(playersReversed);
        draftOrder.addAll(players);

        draftManager.setDraftOrder(draftOrder);
        draftManager.setDraftRandomOrder(players);
    }

    private List<ActionRow> makeActionRows(List<Button> buttons) {
        List<ActionRow> actionRow = new ArrayList<>();
        List<Button> tempActionRow = new ArrayList<>();
        int index = 0;
        for (Button button : buttons) {
            if (index > 4) {
                actionRow.add(ActionRow.of(tempActionRow));
                tempActionRow = new ArrayList<>();
                index = 0;
            }
            tempActionRow.add(button);
            index++;
        }
        actionRow.add(ActionRow.of(tempActionRow));
        return actionRow;
    }

    private List<Button> getMiltySliceButtons(Game activeGame) {
        MiltyDraftManager miltyDraftManager = activeGame.getMiltyDraftManager();
        List<Button> sliceButtons = new ArrayList<>();
        for (MiltyDraftSlice slice : miltyDraftManager.getSlices()) {
            Button sliceButton = Button.success("milty_slice_" + slice.getName(), "Slice No.: " + slice.getName());
            sliceButtons.add(sliceButton);
        }
        return sliceButtons;
    }

    private List<Button> getMiltyOrderButtons(Game activeGame) {
        List<Button> orderButtons = new ArrayList<>();
        for (int i = 0; i < activeGame.getPlayerCountForMap(); i++) {
            int order = i + 1;
            Button sliceButton = Button.success("milty_order_" + order, "Order No.: " + order);
            orderButtons.add(sliceButton);
        }
        return orderButtons;
    }

    private List<net.dv8tion.jda.api.interactions.components.buttons.Button> getMiltyFactionButtons(Game activeGame) {
        List<net.dv8tion.jda.api.interactions.components.buttons.Button> factionChoose = new ArrayList<>();
        for (String faction : activeGame.getMiltyDraftManager().getFactionDraft()) {
            if (faction != null && Mapper.isValidFaction(faction)) {
                net.dv8tion.jda.api.interactions.components.buttons.Button button = Button.secondary("milty_faction_" + faction, " ");
                String factionEmojiString = Emojis.getFactionIconFromDiscord(faction);
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                factionChoose.add(button);
            }
        }
        return factionChoose;
    }

    private List<String> createFactionDraft(int factionCount, List<String> factions) {
        factions.remove("lazax");
        Collections.shuffle(factions);
        List<String> factionDraft = new ArrayList<>();
        for (int i = 0; i < factionCount; i++) {
            factionDraft.add(factions.get(i));
        }
        return factionDraft;
    }

    private FileUpload generateImage(MiltyDraftManager draftManager) {
        List<MiltyDraftSlice> slices = draftManager.getSlices();
        int sliceCount = slices.size();
        float scale = 1.0f;
        int scaled = (int) (900 * scale);
        int width = scaled * 5;
        int height = scaled * (sliceCount > 5 ? sliceCount > 10 ? 3 : 2 : 1);
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

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
                BufferedImage sliceImage = new BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB);
                Graphics graphics = sliceImage.getGraphics();

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
                graphics.drawString(slice.getName(), hs.x + 150, hs.y + 60);

                graphics.setFont(Storage.getFont50());
                int resources = leftSlice.getResources() + rightSlice.getResources() + equadistantSlice.getResources() + farFrontSlice.getResources() + frontSlice.getResources();
                int influence = leftSlice.getInfluence() + rightSlice.getInfluence() + equadistantSlice.getInfluence() + farFrontSlice.getInfluence() + frontSlice.getInfluence();
                double resourcesMilty = leftSlice.getMilty_resources() + rightSlice.getMilty_resources() + equadistantSlice.getMilty_resources() + farFrontSlice.getMilty_resources()
                    + frontSlice.getMilty_resources();
                double influenceMilty = leftSlice.getMilty_influence() + rightSlice.getMilty_influence() + equadistantSlice.getMilty_influence() + farFrontSlice.getMilty_influence()
                    + frontSlice.getMilty_influence();

                graphics.drawString(resources + "/" + influence, hs.x + 130, hs.y + 130);
                graphics.drawString("(" + resourcesMilty + "/" + influenceMilty + ")", hs.x + 70, hs.y + 190);

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
                defaultWriteParam.setCompressionQuality(0.01f);
            }

            imageWriter.write(null, new IIOImage(mainImage, null, null), defaultWriteParam);
        } catch (IOException e) {
            BotLogger.log("Could not save generated slice image", e);
        }
        Game activeGame = getActiveGame();
        String absolutePath = file.getParent() + "/" + activeGame.getName() + "_slices.jpg";
        try (FileInputStream fileInputStream = new FileInputStream(file);
            FileOutputStream fileOutputStream = new FileOutputStream(absolutePath)) {

            BufferedImage image = ImageIO.read(fileInputStream);
            fileInputStream.close();

            BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(image, 0, 0, Color.black, null);

            boolean canWrite = ImageIO.write(convertedImage, "jpg", fileOutputStream);

        } catch (IOException e) {
            BotLogger.log("Could not save jpg file", e);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        File jpgFile = new File(absolutePath);
        FileUpload fileUpload = FileUpload.fromData(jpgFile, jpgFile.getName());
        MapFileDeleter.addFileToDelete(jpgFile);
        return fileUpload;
    }

    private boolean generateSlices(int sliceCount, MiltyDraftManager draftManager) {
        boolean slicesCreated = false;
        int i = 0;
        while (!slicesCreated && i < 100) {
            draftManager.clearSlices();

            List<MiltyDraftTile> blue = draftManager.getBlue();
            List<MiltyDraftTile> red = draftManager.getRed();

            Collections.shuffle(blue);
            Collections.shuffle(red);

            for (int j = 0; j < sliceCount; j++) {

                MiltyDraftSlice miltyDraftSlice = new MiltyDraftSlice();
                List<MiltyDraftTile> tiles = new ArrayList<>();
                tiles.add(blue.remove(0));
                tiles.add(blue.remove(0));
                tiles.add(blue.remove(0));
                MiltyDraftTile red1 = red.remove(0);
                MiltyDraftTile red2 = red.remove(0);
                tiles.add(red1);
                tiles.add(red2);
                boolean needToCheckAnomalies = red1.getTierList() == TierList.anomaly && red2.getTierList() == TierList.anomaly;
                Collections.shuffle(tiles);

                if (!anomalies_can_touch && needToCheckAnomalies) {
                    int emergencyIndex = 0;
                    while (emergencyIndex < 100) {

                        boolean left = tiles.get(0).getTile().isAnomaly();
                        boolean front = tiles.get(1).getTile().isAnomaly();
                        boolean right = tiles.get(2).getTile().isAnomaly();
                        boolean equi = tiles.get(3).getTile().isAnomaly();
                        boolean meca = tiles.get(4).getTile().isAnomaly();
                        if (!((front && (left || right || equi || meca)) || (equi && (meca || left)))) {
                            break;
                        }
                        Collections.shuffle(tiles);
                        emergencyIndex++;
                    }
                }
                miltyDraftSlice.setLeft(tiles.get(0));
                miltyDraftSlice.setFront(tiles.get(1));
                miltyDraftSlice.setRight(tiles.get(2));
                miltyDraftSlice.setEquadistant(tiles.get(3));
                miltyDraftSlice.setFarFront(tiles.get(4));

                //CHECK IF SLICES ARE OK HERE -------------------------------
                if (miltyDraftSlice.getOptimalTotalValue() < 8 || miltyDraftSlice.getOptimalTotalValue() > 14) {
                    break;
                }
                miltyDraftSlice.setName(Integer.toString(j));
                draftManager.addSlice(miltyDraftSlice);
            }

            if (draftManager.getSlices().size() == sliceCount) {
                slicesCreated = true;
            }
            i++;
        }
        return slicesCreated;
    }

    public void initDraftTiles(MiltyDraftManager draftManager) {
        Map<String, TileModel> allTiles = TileHelper.getAllTiles();
        for (TileModel tileModel : new ArrayList<>(allTiles.values())) {
            String tileID = tileModel.getId();
            if (isValid(tileModel, tileID)) {
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
            if (tileID.length() > 2) {
                continue;
            }

            if (tile.isHomeSystem() || tile.getRepresentation().contains("Hyperlane") || tile.getRepresentation().contains("Keleres")) {
                continue;
            }

            draftTile.setTile(tile);
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet planet) {
                    int resources = planet.getResources();
                    int influence = planet.getInfluence();

                    draftTile.addResources(resources);
                    draftTile.addInfluence(influence);
                    if (resources > influence) {
                        draftTile.addMilty_resources(resources);
                    } else if (influence > resources) {
                        draftTile.addMilty_influence(influence);
                    } else if (resources == influence) {
                        draftTile.addMilty_resources((double) resources / 2);
                        draftTile.addMilty_influence((double) influence / 2);
                    }

                    if (planet.isHasAbility()) {
                        draftTile.setLegendary(true);
                    }
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

    private static boolean isValid(TileModel tileModel, String tileID) {
        String id = tileID.toLowerCase();
        String path = tileModel.getTilePath().toLowerCase();
        return id.contains("corner") || path.contains("corner") ||
            id.contains("lane") || path.contains("lane") ||
            id.contains("mecatol") || path.toLowerCase().contains("mecatol") ||
            id.contains("blank") || path.contains("blank") ||
            id.contains("border") || path.contains("border") ||
            id.contains("FOW") || path.contains("FOW") ||
            id.contains("anomaly") || path.contains("anomaly") ||
            id.contains("DeltaWH") || path.contains("DeltaWH") ||
            id.contains("Seed") || path.contains("Seed") ||
            id.contains("MR") || path.contains("MR") ||
            id.contains("Mallice") || path.contains("Mallice") ||
            id.contains("Ethan") || path.contains("Ethan") ||
            id.contains("prison") || path.contains("prison") ||
            id.contains("Kwon") || path.contains("Kwon") ||
            id.contains("home") || path.contains("home") ||
            id.contains("hs") || path.contains("hs") ||
            id.contains("red") || path.contains("red") ||
            id.contains("blue") || path.contains("blue") ||
            id.contains("green") || path.contains("green") ||
            id.contains("gray") || path.contains("gray") ||
            id.contains("gate") || path.contains("gate") ||
            id.contains("setup") || path.contains("setup");
    }
}
