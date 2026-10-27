package ti4.service.draft;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.draft.DraftBoard;
import ti4.image.draft.NucleusImage;
import ti4.image.io.ImageUploads;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Mapper;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class NucleusImageGeneratorService {

    public FileUpload tryGenerateImage(DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {

        Game game = draftManager.getGame();
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (!mapTemplate.isNucleusTemplate()) {
            return null;
        }

        if (restrictChoiceKeys != null) {
            // This COULD absolutely support limited choice keys. Any tile not present in the restricted set
            // could just be replaced with gray setup tiles. You'd skip printing any additional info about those slices.
            // If restrictChoiceKeys is set, you probably also just render the default map template preview tiles for
            // all non-Nucleus tiles, to avoid leaking info about other draftables.
            throw new IllegalArgumentException("Nucleus image generation does not support incomplete information.");
        }

        BufferedImage nucleusImage = generateImage(draftManager);
        if (nucleusImage == null) {
            return null;
        }
        FileUpload fileUpload = ImageUploads.createFileUpload(nucleusImage, uniqueKey);

        return fileUpload;
    }

    private BufferedImage generateImage(DraftManager draftManager) {
        Game game = draftManager.getGame();
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);

        Map<String, Tile> tileMap = DraftBoard.tilesOnBoard(game);
        Map<String, MiltyDraftSlice> seatPosToNucleusTiles = getSeatToNucleusTiles(mapTemplate, tileMap);
        Map<String, NucleusImage.PlayerInfo> seatToPlayerInfo = getSeatToPlayer(mapTemplate, draftManager);

        return NucleusImage.render(game, seatPosToNucleusTiles, seatToPlayerInfo);
    }

    private Map<String, NucleusImage.PlayerInfo> getSeatToPlayer(
            MapTemplateModel mapTemplateModel, DraftManager draftManager) {
        Map<String, NucleusImage.PlayerInfo> seatNumToPlayerInfo = new HashMap<>();

        Function<PlayerDraftState, Integer> getSeatNumber = null;
        if (draftManager.getDraftable(SeatDraftable.TYPE) != null) {
            getSeatNumber = state -> {
                List<DraftChoice> seatChoices = state.getPicks(SeatDraftable.TYPE);
                if (seatChoices == null || seatChoices.isEmpty()) {
                    return null;
                }
                DraftChoice seatChoice = seatChoices.getFirst();
                Integer seatNum = SeatDraftable.getSeatNumberFromChoiceKey(seatChoice.choiceKey());
                return seatNum;
            };
        } else if (draftManager.getDraftable(SpeakerOrderDraftable.TYPE) != null) {
            getSeatNumber = state -> {
                List<DraftChoice> speakerChoices = state.getPicks(SpeakerOrderDraftable.TYPE);
                if (speakerChoices == null || speakerChoices.isEmpty()) {
                    return null;
                }
                DraftChoice speakerChoice = speakerChoices.getFirst();
                Integer seatNum = SpeakerOrderDraftable.getSpeakerOrderFromChoiceKey(speakerChoice.choiceKey());
                return seatNum;
            };
        }
        if (getSeatNumber == null) {
            return seatNumToPlayerInfo;
        }

        Map<String, PlayerDraftState> playerStates = draftManager.getPlayerStates();
        for (Map.Entry<String, PlayerDraftState> playerState : playerStates.entrySet()) {
            Integer seatNum = getSeatNumber.apply(playerState.getValue());
            if (seatNum == null) {
                continue;
            }
            MapTemplateTile homeTile = getSeatTileForPlayer(mapTemplateModel, seatNum);
            if (homeTile == null) {
                continue;
            }
            String playerUserName =
                    draftManager.getGame().getPlayer(playerState.getKey()).getUserName();
            List<TI4Emoji> summaryEmojis = getPlayerSummaryEmojis(draftManager, playerState.getKey());
            seatNumToPlayerInfo.put(homeTile.getPos(), new NucleusImage.PlayerInfo(playerUserName, summaryEmojis));
        }

        return seatNumToPlayerInfo;
    }

    private List<TI4Emoji> getPlayerSummaryEmojis(DraftManager draftManager, String playerUserId) {
        if (draftManager == null
                || playerUserId == null
                || !draftManager.getPlayerStates().containsKey(playerUserId)) {
            return List.of();
        }

        List<TI4Emoji> summaryEmojis = new ArrayList<>();

        // When Seat and Speaker Order are drafted separately, provide the SpeakerOrder emoji
        if (draftManager.getDraftable(SeatDraftable.TYPE) != null
                && draftManager.getDraftable(SpeakerOrderDraftable.TYPE) != null) {
            List<DraftChoice> speakerChoices = draftManager.getPlayerPicks(playerUserId, SpeakerOrderDraftable.TYPE);
            if (speakerChoices != null && !speakerChoices.isEmpty()) {
                DraftChoice speakerChoice = speakerChoices.getFirst();
                Integer speakerOrder = SpeakerOrderDraftable.getSpeakerOrderFromChoiceKey(speakerChoice.choiceKey());
                TI4Emoji speakerEmoji = MiltyDraftEmojis.getSpeakerPickEmoji(speakerOrder);
                if (speakerEmoji != null) {
                    summaryEmojis.add(speakerEmoji);
                }
            }
        }

        // When a player has explicitly drafted Keleres, show the faction Emoji since the home system is unknown
        if (draftManager.getDraftable(FactionDraftable.TYPE) != null) {
            List<DraftChoice> playerPicks = draftManager.getPlayerPicks(playerUserId, FactionDraftable.TYPE);
            if (playerPicks != null && playerPicks.size() == 1) {
                DraftChoice factionChoice = playerPicks.getFirst();
                FactionModel faction = FactionDraftable.getFactionByChoice(factionChoice);
                if (faction.getAlias().contains("keleres")) {
                    TI4Emoji keleresEmoji = FactionEmojis.getFactionIcon(faction.getAlias());
                    summaryEmojis.add(keleresEmoji);
                }
            }
        }

        // Add other draftable emojis as needed for new types

        return summaryEmojis;
    }

    private Map<String, MiltyDraftSlice> getSeatToNucleusTiles(
            MapTemplateModel mapTemplate, Map<String, Tile> gameTileMap) {
        Map<String, MiltyDraftSlice> seatToNucleusTiles = new HashMap<>();
        for (int i = 1; i <= mapTemplate.getNucleusSliceCount(); ++i) {
            int playerNum = i;

            MapTemplateTile sliceSeat = getSeatTileForPlayer(mapTemplate, playerNum);
            String sliceSeatPos = sliceSeat.getPos();

            Predicate<MapTemplateTile> nucleusSliceFilter =
                    t -> t.getNucleusNumbers() != null && t.getNucleusNumbers().contains(playerNum);
            List<MiltyDraftTile> nucleusSliceTiles = mapTemplate.getTemplateTiles().stream()
                    .filter(nucleusSliceFilter)
                    .map(MapTemplateTile::getPos)
                    .map(pos -> gameTileMap.get(pos).getTileID())
                    .map(DraftTileManager::findTile)
                    .toList();
            MiltyDraftSlice pseudoSlice = new MiltyDraftSlice();
            pseudoSlice.setName("" + i);
            pseudoSlice.setTiles(new ArrayList<>(nucleusSliceTiles));

            seatToNucleusTiles.put(sliceSeatPos, pseudoSlice);
        }
        return seatToNucleusTiles;
    }

    private MapTemplateTile getSeatTileForPlayer(MapTemplateModel mapTemplateModel, int playerNumber) {
        Predicate<MapTemplateTile> seatFilter = t -> t.getHome() != null
                && t.getHome()
                && t.getPlayerNumber() != null
                && t.getPlayerNumber() == playerNumber;
        return mapTemplateModel.getTemplateTiles().stream()
                .filter(seatFilter)
                .findFirst()
                .orElse(null);
    }
}
