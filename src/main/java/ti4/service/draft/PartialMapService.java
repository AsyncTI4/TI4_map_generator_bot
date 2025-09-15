package ti4.service.draft;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.MapTemplateHelper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.map.AddTileService;
import ti4.service.milty.MiltyDraftSlice;

@UtilityClass
public class PartialMapService {
    /**
     * This function depends on knowing which class types have the required
     * information,
     * so it kinda breaks the abstraction a bit, but oh well.
     *
     * @param draftManager
     */
    public void tryUpdateMap(GenericInteractionCreateEvent event, DraftManager draftManager) {
        Game game = draftManager.getGame();
        String mapTemplateId = getMapTemplateModelId(draftManager);
        SliceDraftable sliceDraftable = getSliceDraftable(draftManager);
        SeatDraftable seatDraftable = getSeatDraftable(draftManager);
        SpeakerOrderDraftable speakerOrderDraftable = getSpeakerOrderDraftable(draftManager);
        FactionDraftable factionDraftable = getFactionDraftable(draftManager);
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplateId);

        if (seatDraftable == null && speakerOrderDraftable == null) {
            // No way to place tiles on the map
            return;
        }

        if (sliceDraftable == null && factionDraftable == null) {
            // Nothing of value to place on the map
            return;
        }

        boolean updateMap = false;

        // General map setup tasks
        for (MapTemplateTile templateTile : mapTemplateModel.getTemplateTiles()) {
            Tile gameTile = game.getTileByPosition(templateTile.getPos());
            
            if(gameTile == null && templateTile.getPos() != null) {
                Tile toAdd = MapTemplateHelper.getTileFromTemplateTile(templateTile);
                if(toAdd != null) {
                    game.setTile(toAdd);
                    updateMap = true;
                }
            }
            
            if (templateTile.getPos() != null && templateTile.getCustodians() != null && templateTile.getCustodians()) {
                if (gameTile != null)
                    AddTileService.addCustodianToken(gameTile, game); // only works on MR for now
            }
        }

        for (PlayerDraftState pState : draftManager.getPlayerStates().values()) {
            // Get their position, to see if we can do anything
            Integer position = getPlayerPosition(
                    pState, seatDraftable == null ? speakerOrderDraftable.getType() : seatDraftable.getType());
            if (position == null) {
                // Player hasn't picked a position yet
                continue;
            }

            FactionModel factionModel = getPlayerFactionModel(pState);
            MiltyDraftSlice slice = getPlayerSlice(pState, sliceDraftable);
            if (factionModel == null && slice == null) {
                // Player hasn't picked anything we can place on the map
                continue;
            }

            List<MapTemplateTile> mapTiles = mapTemplateModel.getTemplateTiles();
            for (MapTemplateTile templateTile : mapTiles) {
                if (templateTile.getPos() == null) {
                    continue;
                }
                Tile gameTile = game.getTileByPosition(templateTile.getPos());
                if (gameTile != null && !TileHelper.isDraftTile(gameTile.getTileModel())) {
                    // Tile already placed here
                    continue;
                }

                if (templateTile.getPlayerNumber() != position) {
                    // Doesn't pertain to this player
                    continue;
                }
                if (templateTile.getHome() != null
                        && templateTile.getHome()
                        && factionModel != null
                        && !factionModel.getAlias().startsWith("keleres")) {
                    String hsTileId = factionModel.getHomeSystem();
                    hsTileId = AliasHandler.resolveTile(hsTileId);
                    Tile toAdd = new Tile(hsTileId, templateTile.getPos());
                    game.setTile(toAdd);
                    updateMap = true;
                } else if (templateTile.getMiltyTileIndex() != null && slice != null) {
                    String tileID = slice.getTiles()
                            .get(templateTile.getMiltyTileIndex())
                            .getTile()
                            .getTileID();
                    tileID = AliasHandler.resolveTile(tileID);

                    Tile toAdd = new Tile(tileID, templateTile.getPos());
                    game.setTile(toAdd);
                    updateMap = true;
                }
            }
        }

        if (updateMap) {
            ButtonHelper.updateMap(game, event);
        }
    }

    private Integer getPlayerPosition(PlayerDraftState pState, DraftableType seatDraftable) {
        Integer position = null;

        Map<DraftableType, List<DraftChoice>> choices = pState.getPicks();
        if (choices.containsKey(seatDraftable) && !choices.get(seatDraftable).isEmpty()) {
            String seatStr = choices.get(seatDraftable).get(0).getChoiceKey();
            position = Integer.parseInt(seatStr);
        }
        return position;
    }

    private FactionModel getPlayerFactionModel(PlayerDraftState pState) {
        Map<DraftableType, List<DraftChoice>> choices = pState.getPicks();
        if (choices.containsKey(FactionDraftable.TYPE)
                && !choices.get(FactionDraftable.TYPE).isEmpty()) {
            String factionId = choices.get(FactionDraftable.TYPE).get(0).getChoiceKey();
            return Mapper.getFaction(factionId);
        }
        return null;
    }

    private MiltyDraftSlice getPlayerSlice(PlayerDraftState pState, SliceDraftable sliceDraftable) {
        Map<DraftableType, List<DraftChoice>> choices = pState.getPicks();
        if (choices.containsKey(SliceDraftable.TYPE)
                && !choices.get(SliceDraftable.TYPE).isEmpty()) {
            String sliceName = choices.get(SliceDraftable.TYPE).get(0).getChoiceKey();
            return sliceDraftable.getSliceByName(sliceName);
        }
        return null;
    }

    private SliceDraftable getSliceDraftable(DraftManager draftManager) {
        for (Draftable draftable : draftManager.getDraftables()) {
            if (draftable instanceof SliceDraftable) {
                return (SliceDraftable) draftable;
            }
        }
        return null;
    }

    private SeatDraftable getSeatDraftable(DraftManager draftManager) {
        for (Draftable draftable : draftManager.getDraftables()) {
            if (draftable instanceof SeatDraftable) {
                return (SeatDraftable) draftable;
            }
        }
        return null;
    }

    private SpeakerOrderDraftable getSpeakerOrderDraftable(DraftManager draftManager) {
        for (Draftable draftable : draftManager.getDraftables()) {
            if (draftable instanceof SpeakerOrderDraftable) {
                return (SpeakerOrderDraftable) draftable;
            }
        }
        return null;
    }

    private FactionDraftable getFactionDraftable(DraftManager draftManager) {
        for (Draftable draftable : draftManager.getDraftables()) {
            if (draftable instanceof FactionDraftable) {
                return (FactionDraftable) draftable;
            }
        }
        return null;
    }

    private String getMapTemplateModelId(DraftManager draftManager) {
        String mapTemplateId = draftManager.getGame().getMapTemplateID();
        if (mapTemplateId == null || mapTemplateId.isEmpty()) {
            MapTemplateModel mapTemplateModel = Mapper.getDefaultMapTemplateForPlayerCount(
                    draftManager.getPlayerStates().size());
            if (mapTemplateModel == null) {
                throw new IllegalStateException(
                        "No default map template for " + draftManager.getPlayerStates().size() + " players");
            }
            mapTemplateId = mapTemplateModel.getAlias();
        }
        return mapTemplateId;
    }
}
