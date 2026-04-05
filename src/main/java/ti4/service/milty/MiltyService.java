package ti4.service.milty;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.TIGLHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.rules.ThundersEdgeRulesService;

@UtilityClass
public class MiltyService {

    public static void offerKeleresSetupButtons(MiltyDraftManager manager, Player player) {
        List<String> flavors = List.of("mentak", "xxcha", "argent");
        List<Button> keleresPresets = new ArrayList<>();
        boolean warn = false;
        for (String f : flavors) {
            if (manager.isFactionTaken(f)) continue;

            FactionModel model = Mapper.getFaction(f);
            String id = "draftPresetKeleres_" + f;
            String label = StringUtils.capitalize(f);
            if (manager.getFactionDraft().contains(f)) {
                keleresPresets.add(Buttons.gray(id, label + " 🛑", model.getFactionEmoji()));
                warn = true;
            } else {
                keleresPresets.add(Buttons.green(id, label, model.getFactionEmoji()));
            }
        }

        String message = player.getPing()
                + " Pre-select which flavor of Keleres to play in this game by clicking one of these buttons!";
        message += " You can change your decision later by clicking a different button.";
        if (warn)
            message +=
                    "\n- 🛑 Some of these factions are in the draft! 🛑 If you preset them and they get chosen, then the preset will be canceled.";
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, keleresPresets);
    }

    public static String startFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();

        // Load the general game settings
        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success) return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        MiltyDraftSpec specs = MiltyDraftSpec.fromSettings(settings);

        return startFromSpecs(event, specs);
    }

    public static String startFromSpecs(GenericInteractionCreateEvent event, MiltyDraftSpec specs) {
        Game game = specs.game;

        if (specs.presetSlices != null) {
            if (specs.presetSlices.size() < specs.playerIDs.size())
                return "Not enough slices for the number of players. Please remove the preset slice string or include enough slices";
        }

        // Milty Draft Manager Setup --------------------------------------------------------------
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        List<ComponentSource> sources = new ArrayList<>(specs.tileSources);
        if (game.isDiscordantStarsMode() || game.isUnchartedSpaceStuff()) {
            sources.add(ComponentSource.ds);
            sources.add(ComponentSource.uncharted_space);
        }
        if ((!game.isBaseGameMode() && game.getStoredValue("useOldPok").isEmpty()) || game.isTwilightsFallMode()) {
            sources.add(ComponentSource.thunders_edge);
        }

        draftManager.init(sources);
        draftManager.setMapTemplate(specs.template.getAlias());
        game.setMapTemplateID(specs.template.getAlias());
        List<String> players = new ArrayList<>(specs.playerIDs);
        boolean staticOrder = specs.playerDraftOrder != null && !specs.playerDraftOrder.isEmpty();
        if (staticOrder) {
            players = new ArrayList<>(specs.playerDraftOrder)
                    .stream().filter(p -> specs.playerIDs.contains(p)).toList();
        }
        initDraftOrder(draftManager, players, staticOrder);

        // initialize factions
        List<String> unbannedFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> specs.factionSources.contains(f.getSource()))
                .filter(f -> !specs.bannedFactions.contains(f.getAlias()))
                .filter(f -> !f.getAlias().contains("obsidian"))
                .filter(f -> !f.getAlias().contains("neutral"))
                .filter(f -> !f.getAlias().contains("keleres")
                        || "keleresm".equals(f.getAlias())) // Limit the pool to only 1 keleres flavor
                .map(FactionModel::getAlias)
                .toList());
        List<String> factionDraft = createFactionDraft(specs.numFactions, unbannedFactions, specs.priorityFactions);
        draftManager.setFactionDraft(factionDraft);

        // validate slice count + sources
        int redTiles = draftManager.getRed().size();
        int blueTiles = draftManager.getBlue().size();
        int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
        if (specs.numSlices > maxSlices) {
            String msg = "Milty draft in this bot does not support " + specs.numSlices
                    + " slices. You can enable DS to allow building additional slices";
            msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles
                    + "blue/" + redTiles + "red]";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            return "Could not start milty draft, fix the error and try again";
        }

        String startMsg = "## Generating the milty draft!!";
        startMsg +=
                "\n - Also clearing out any tiles that may have already been on the map so that the draft will fill in tiles properly.";
        if (specs.numSlices == maxSlices) {
            startMsg += "\n - *You asked for the max number of slices, so this may take several seconds*";
        }

        game.clearTileMap();
        try {
            MiltyDraftHelper.buildPartialMap(game, event);
        } catch (Exception e) {
            // Ignore
        }

        if (specs.presetSlices != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "### You are using preset slices!! Starting the draft right away!");
            specs.presetSlices.forEach(draftManager::addSlice);
            MiltyDraftDisplayService.repostDraftInformation(draftManager, game);
        } else {
            event.getMessageChannel().sendMessage(startMsg).queue((ignore) -> {
                boolean slicesCreated = GenerateSlicesService.generateSlices(event, draftManager, specs);
                if (!slicesCreated) {
                    String msg = "Generating slices was too hard so I gave up.... Please try again.";
                    if (specs.numSlices == maxSlices) {
                        msg += "\n*...and maybe consider asking for fewer slices*";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                } else {
                    MiltyDraftDisplayService.repostDraftInformation(draftManager, game);
                    for (String player : draftManager.getPlayers()) {
                        Player p = game.getPlayer(player);
                        if (p != null
                                && p != draftManager.getCurrentDraftPlayer(game)
                                && p.getCardsInfoThread() != null) {
                            MessageHelper.sendMessageToChannel(
                                    p.getCardsInfoThread(),
                                    p.getRepresentation() + " You can queue your choices with these buttons",
                                    draftManager.getQueueButtons(p, game));
                        }
                    }
                    game.setPhaseOfGame("miltydraft");
                    GameManager.save(game, "Milty"); // TODO: We should be locking since we're saving
                    if (game.isThundersEdge()) {
                        ThundersEdgeRulesService.alertTabletalkWithRulesAtStartOfDraft(game);
                    }
                }
            });
        }
        return null;
    }

    private static void initDraftOrder(MiltyDraftManager draftManager, List<String> playerIDs, boolean staticOrder) {
        List<String> players = new ArrayList<>(playerIDs);
        if (!staticOrder) {
            Collections.shuffle(players);
        }

        List<String> playersReversed = new ArrayList<>(players);
        Collections.reverse(playersReversed);

        List<String> draftOrder = new ArrayList<>(players);
        draftOrder.addAll(playersReversed);
        draftOrder.addAll(players);

        draftManager.setDraftOrder(draftOrder);
        draftManager.setPlayers(players);
    }

    private static List<String> createFactionDraft(
            int factionCount, List<String> factions, List<String> firstFactions) {
        List<String> randomOrder = new ArrayList<>(firstFactions);
        Collections.shuffle(randomOrder);
        Collections.shuffle(factions);
        randomOrder.addAll(factions);

        int i = 0;
        List<String> output = new ArrayList<>();
        while (output.size() < factionCount) {
            if (i >= randomOrder.size()) return output;
            String f = randomOrder.get(i);
            i++;
            if (output.contains(f)) continue;
            if (!factions.contains(f)) continue;
            output.add(f);
        }
        return output;
    }

    public static void miltySetup(GenericInteractionCreateEvent event, Game game) {
        MiltySettings menu = game.initializeMiltySettings();
        // TODO: Settings should use a flag for nucleus generation.
        // But for now, trying to keep our settings changes minimal.
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            if (buttonEvent.getButton().getCustomId().endsWith("_nucleus")) {
                menu.getDraftMode().setChosenKey("nucleus");
            }
        }
        menu.postMessageAndButtons(event);
        ButtonHelper.deleteMessage(event);
    }

    public static void setupExtraFactionTiles(Game game, Player player, String faction, String positionHS, Tile tile) {
        // HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction) || "miltymod_ghost".equals(faction)) {
            if (!game.isBaseGameMode()) {
                tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            }
            if (game.isTwilightsFallMode()) {
                player.addAbility("echo_of_sacrifice");
            }

            // Add the new tile
            String pos = addAnotherCornerTile(game, "51", positionHS);
            player.setHomeSystemPosition(pos);
        }

        // HANDLE CRIMSON'S HOME SYSTEM LOCATION
        if ("crimson".equals(faction)) {
            if (!game.isBaseGameMode()) {
                tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            }
            if (!game.isTwilightsFallMode()) {
                tile.addToken(Constants.TOKEN_BREACH_INACTIVE, Constants.SPACE);
            } else {
                player.addAbility("echo_of_divergence");
            }

            // Add the new tile
            String pos = addAnotherCornerTile(game, "118", positionHS);
            player.setHomeSystemPosition(pos);
        }
    }

    private static String addAnotherCornerTile(Game game, String tileID, String anchorPos) {
        record TileAndAnchor(Tile tile, String pos) {}
        List<TileAndAnchor> tilesWithAnchors = Stream.of("tl", "tr", "bl", "br")
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .map(t -> new TileAndAnchor(t, getAnchorPositionForTile(game, t)))
                .collect(Collectors.toCollection(ArrayList::new));
        tilesWithAnchors.add(new TileAndAnchor(new Tile(tileID, "tl"), anchorPos));
        if (tilesWithAnchors.size() > 4) {
            String err = "# " + game.getPing() + " UNABLE TO ADD ANOTHER CORNER TILE. PLEASE RESOLVE.";
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), err);
            return null;
        }
        String pos = "tr";
        for (List<String> positions : CollectionUtils.permutations(List.of("tl", "tr", "bl", "br"))) {
            boolean acceptable = true;
            Point center = PositionMapper.getTilePosition("000");
            for (int x = 0; x < tilesWithAnchors.size(); x++) {
                String xAnchorPos = tilesWithAnchors.get(x).pos();
                if (xAnchorPos != null) {
                    Point anchor = PositionMapper.getTilePosition(xAnchorPos);
                    String corner = positions.get(x);
                    acceptable &= switch (corner) {
                        case "tl" -> anchor.x <= center.x && anchor.y <= center.y;
                        case "tr" -> anchor.x >= center.x && anchor.y <= center.y;
                        case "bl" -> anchor.x <= center.x && anchor.y >= center.y;
                        case "br" -> anchor.x >= center.x && anchor.y >= center.y;
                        default -> false;
                    };
                }
            }
            if (acceptable) {
                for (int x = 0; x < positions.size(); x++) {
                    String corner = positions.get(x);
                    if (x >= tilesWithAnchors.size()) {
                        game.getTileMap().remove(corner);
                    } else {
                        Tile tile = tilesWithAnchors.get(x).tile();
                        if (tile.getTileID().equals(tileID)) pos = corner;
                        tile.setPosition(corner);
                        game.setTile(tile);
                    }
                }
            }
        }
        return pos;
    }

    private static String getAnchorPositionForTile(Game game, Tile t) {
        return switch (t.getTileID()) {
            case "51" -> game.getTile("17").getPosition();
            case "118" -> game.getTile("94").getPosition();
            default -> null;
        };
    }
}
