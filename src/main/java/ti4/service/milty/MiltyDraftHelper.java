package ti4.service.milty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.MapTemplateHelper;
import ti4.image.draft.SliceSheet;
import ti4.image.io.ImageUploads;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.model.WormholeModel;

@UtilityClass
public class MiltyDraftHelper {

    private static final Map<String, MiltyDraftTile> tiles = new HashMap<>();

    public static void generateAndPostSlices(Game game) {
        MessageChannel mainGameChannel = game.getMainGameChannel();
        FileUpload fileUpload = generateImage(game);
        MessageHelper.sendFileUploadToChannel(mainGameChannel, fileUpload);
    }

    public static FileUpload generateImage(Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        List<MiltyDraftSlice> slices = manager.getSlices();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(manager.getMapTemplate());

        List<SliceSheet.Card> cards =
                slices.stream().map(slice -> card(game, manager, slice)).toList();

        FileUpload fileUpload =
                ImageUploads.createFileUpload(SliceSheet.render(mapTemplate, cards), game.getName() + "_miltydraft");
        fileUpload.setDescription(
                slices.stream().map(MiltyDraftSlice::ttsString).collect(Collectors.joining(";\n")));
        return fileUpload;
    }

    private static SliceSheet.Card card(Game game, MiltyDraftManager manager, MiltyDraftSlice slice) {
        Player player = game.getPlayers().values().stream()
                .filter(candidate -> manager.getPlayerDraft(candidate) != null)
                .filter(candidate ->
                        slice.equals(manager.getPlayerDraft(candidate).getSlice()))
                .findFirst()
                .orElse(null);
        boolean taken = player != null;
        if (game.isFowMode()) {
            return SliceSheet.Card.hidden(slice, taken);
        }
        FactionModel faction = null;
        if (taken && manager.getPlayerDraft(player).getFaction() != null) {
            faction = Mapper.getFaction(manager.getPlayerDraft(player).getFaction());
        }
        return SliceSheet.Card.revealed(slice, taken, taken ? player.getUserName() : null, faction);
    }

    public static void initDraftTiles(MiltyDraftManager manager, Game game) {
        List<ComponentSource> sources = new ArrayList<>(Arrays.asList(
                ComponentSource.base,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4,
                ComponentSource.pok));
        if (game.isDiscordantStarsMode()) {
            sources.add(ComponentSource.ds);
        }
        if (game.isUnchartedSpaceStuff()) {
            sources.add(ComponentSource.uncharted_space);
        }
        if (game.isLostLegaciesTiles()) {
            sources.add(ComponentSource.theodisi);
        }
        if ((!game.isBaseGameMode() && game.getStoredValue("useOldPok").isEmpty()) || game.isTwilightsFallMode()) {
            sources.add(ComponentSource.thunders_edge);
        }
        initDraftTiles(manager, sources);
        EchoesOfYggdrasilService.addTiles(game, manager);
    }

    private static MiltyDraftTile getDraftTileFromModel(TileModel tileModel) {
        String tileID = tileModel.getId();
        if (tiles.containsKey(tileID)) return tiles.get(tileID);

        Set<WormholeModel.Wormhole> wormholes = tileModel.getWormholes();
        MiltyDraftTile draftTile = new MiltyDraftTile();
        if (wormholes != null) {
            for (WormholeModel.Wormhole wormhole : wormholes) {
                if (wormhole == WormholeModel.Wormhole.ALPHA) {
                    draftTile.setHasAlphaWH(true);
                } else if (wormhole == WormholeModel.Wormhole.BETA) {
                    draftTile.setHasBetaWH(true);
                } else {
                    draftTile.setHasOtherWH(true);
                }
            }
        }

        Tile tile = new Tile(tileID, "none");
        draftTile.setTile(tile);

        if (tile.isScar()) {
            draftTile.setHasScar(true);
        }

        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet) {
                draftTile.addPlanet(planet);
            }
        }

        if (tile.isAnomaly()) {
            draftTile.setTierList(TierList.anomaly);
        } else if (tile.getPlanetUnitHolders().isEmpty()) {
            draftTile.setTierList(TierList.red);
        } else {
            draftTile.setTierList(TierList.high);
        }

        tiles.put(tileID, draftTile);
        return draftTile;
    }

    public static void initDraftTiles(MiltyDraftManager draftManager, List<ComponentSource> sources) {
        List<TileModel> allTiles = new ArrayList<>(TileHelper.getAllTileModels());
        for (TileModel tileModel : allTiles) {

            if (isInvalid(tileModel)) continue;

            if (!sources.contains(tileModel.getSource())) continue;
            if (tileModel.getSource() == ComponentSource.theodisi && !TileHelper.isLostLegaciesTile(tileModel)) {
                continue;
            }
            if (tileModel.getTileBack() == TileBack.GREEN || tileModel.isHyperlane()) continue;

            MiltyDraftTile draftTile = getDraftTileFromModel(tileModel);
            draftManager.addDraftTile(draftTile);
        }
    }

    public static void addDraftTile(MiltyDraftManager manager, String tileID) {
        TileModel model = TileHelper.getTileById(tileID);
        if (model == null) return;
        MiltyDraftTile draftTile = getDraftTileFromModel(model);
        manager.addDraftTile(draftTile);
    }

    private static boolean isInvalid(TileModel tileModel) {
        TileModel.TileBack back = tileModel.getTileBack();
        if (back != TileBack.RED && back != TileBack.BLUE) {
            return true;
        }

        String id = tileModel.getId().toLowerCase();
        String path =
                tileModel.getImagePath() == null ? "" : tileModel.getImagePath().toLowerCase();
        List<String> disallowedTerms = List.of(
                "corner",
                "lane",
                "mecatol",
                "blank",
                "border",
                "fow",
                "anomaly",
                "deltawh",
                "seed",
                "sorrowwh",
                "fracture",
                "mr",
                "mrte",
                "mallice",
                "ethan",
                "prison",
                "kwon",
                "home",
                "hs",
                "red",
                "blue",
                "green",
                "gray",
                "gate",
                "setup");
        return disallowedTerms.stream().anyMatch(term -> id.contains(term) || path.contains(term));
    }

    public static boolean buildPartialMap(Game game) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();

        String mapTemplate = manager.getMapTemplate();
        if (mapTemplate == null) {
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(
                    manager.getPlayers().size());
            if (defaultTemplate == null) {
                throw new Exception("idk how to build this map yet: " + game.getName() + ", players: "
                        + manager.getPlayers().size());
            }
            mapTemplate = defaultTemplate.getAlias();
        }

        return MapTemplateHelper.buildPartialMapFromMiltyData(game, mapTemplate);
    }

    public static void buildMap(Game game) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();

        String mapTemplate = manager.getMapTemplate();
        if (mapTemplate == null) {
            MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(
                    manager.getPlayers().size());
            if (defaultTemplate == null) {
                throw new Exception("idk how to build this map yet");
            }
            mapTemplate = defaultTemplate.getAlias();
        }

        MapTemplateHelper.buildMapFromMiltyData(game, mapTemplate);
    }

    // TODO (Jazz): add map template
    public static List<MiltyDraftSlice> parseSlices(String input, List<ComponentSource> sources) throws Exception {
        input = input.replace("|", ";");
        MiltyDraftManager manager = new MiltyDraftManager();
        manager.init(sources);
        manager.loadSlicesFromString(input);
        return manager.getSlices();
    }
}
