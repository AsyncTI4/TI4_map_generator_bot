package ti4.service.draft;

import java.util.List;
import java.util.Map.Entry;
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
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable.ReferenceCardPackage;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.map.AddTileService;
import ti4.service.milty.MiltyDraftSlice;

@UtilityClass
public class PartialMapService {
    /**
     * Attempt to update the Game's map by placing tiles as the draft state allows.
     * This service uses known Draftables to try and derive tile positions. For example,
     * a SliceDraftable and a SeatDraftable can be used to place slice tiles on the map.
     * @param draftManager the draft manager containing the game and draft state
     * @param event the event that triggered this update; required if renderIfUpdated is true
     * @param renderIfUpdated if true, the map image will be re-rendered if any tiles were placed
     */
    public void tryUpdateMap(DraftManager draftManager, GenericInteractionCreateEvent event, boolean renderIfUpdated) {
        boolean mapUpdated = placeTiles(event, draftManager);
        if (mapUpdated && renderIfUpdated) {
            ButtonHelper.updateMap(draftManager.getGame(), event);
        }
    }

    public boolean placeFromTemplate(MapTemplateModel mapTemplateModel, Game game) {
        boolean updateMap = false;
        for (MapTemplateTile templateTile : mapTemplateModel.getTemplateTiles()) {
            Tile gameTile = game.getTileByPosition(templateTile.getPos());

            if (gameTile == null && templateTile.getPos() != null) {
                Tile toAdd = MapTemplateHelper.getTileFromTemplateTile(templateTile);
                if (toAdd != null) {
                    game.setTile(toAdd);
                    updateMap = true;
                }
            }

            // Place items onto certain tiles
            if (templateTile.getPos() != null && templateTile.getCustodians() != null && templateTile.getCustodians()) {
                if (gameTile != null) AddTileService.addCustodianToken(gameTile, game); // only works on MR for now
            }
        }
        return updateMap;
    }

    private boolean placeTiles(GenericInteractionCreateEvent event, DraftManager draftManager) {
        Game game = draftManager.getGame();
        String mapTemplateId = getMapTemplateModelId(draftManager);
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplateId);

        boolean updateMap = placeFromTemplate(mapTemplateModel, game);

        // Check if we can derive any state
        SliceDraftable sliceDraftable = (SliceDraftable) draftManager.getDraftable(SliceDraftable.TYPE);
        SeatDraftable seatDraftable = (SeatDraftable) draftManager.getDraftable(SeatDraftable.TYPE);
        SpeakerOrderDraftable speakerOrderDraftable =
                (SpeakerOrderDraftable) draftManager.getDraftable(SpeakerOrderDraftable.TYPE);
        AndcatReferenceCardsDraftable andcatDraftable =
                (AndcatReferenceCardsDraftable) draftManager.getDraftable(AndcatReferenceCardsDraftable.TYPE);
        FactionDraftable factionDraftable = (FactionDraftable) draftManager.getDraftable(FactionDraftable.TYPE);
        if (seatDraftable == null && speakerOrderDraftable == null && andcatDraftable == null) {
            // No way to place tiles on the map
            return updateMap;
        }
        if (sliceDraftable == null && factionDraftable == null) {
            // Nothing of value to place on the map
            return updateMap;
        }

        // For each player, see if they've made enough picks to place some things on the map.
        for (Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState pState = entry.getValue();
            // Get their position, to see if we can do anything
            Integer position = getPlayerPosition(draftManager, playerUserId, pState);
            if (position == null) {
                // Player hasn't picked a position yet
                continue;
            }

            FactionModel hsFactionModel = getPlayerHsFactionModel(draftManager, pState);
            MiltyDraftSlice slice = getPlayerSlice(pState, sliceDraftable);
            if (hsFactionModel == null && slice == null) {
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

                // Attempt to place a Home System tile if a Faction was picked
                if (templateTile.getHome() != null
                        && templateTile.getHome()
                        && hsFactionModel != null
                        && !hsFactionModel.getAlias().startsWith("keleres")) {
                    String hsTileId = hsFactionModel.getHomeSystem();
                    hsTileId = AliasHandler.resolveTile(hsTileId);
                    Tile toAdd = new Tile(hsTileId, templateTile.getPos());
                    game.setTile(toAdd);
                    updateMap = true;
                } else if (templateTile.getMiltyTileIndex() != null && slice != null) {
                    // Attempt to populate Slice tiles if a Slice was picked
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

        return updateMap;
    }

    private Integer getPlayerPosition(DraftManager draftManager, String playerUserId, PlayerDraftState pState) {
        SeatDraftable seatDraftable = (SeatDraftable) draftManager.getDraftable(SeatDraftable.TYPE);
        SpeakerOrderDraftable speakerOrderDraftable =
                (SpeakerOrderDraftable) draftManager.getDraftable(SpeakerOrderDraftable.TYPE);
        AndcatReferenceCardsDraftable andcatDraftable =
                (AndcatReferenceCardsDraftable) draftManager.getDraftable(AndcatReferenceCardsDraftable.TYPE);

        // Seat Draftables take priority for determining where a player sits
        if (seatDraftable != null) {
            if (pState.getPickCount(seatDraftable.getType()) > 0) {
                String seatChoiceKey =
                        pState.getPicks(seatDraftable.getType()).getFirst().getChoiceKey();
                return SeatDraftable.getSeatNumberFromChoiceKey(seatChoiceKey);
            }
            // If Seat Draftables are excluded from the draft, the Speaker Order is used instead
        } else if (speakerOrderDraftable != null) {
            if (pState.getPickCount(speakerOrderDraftable.getType()) > 0) {
                String pickChoiceKey = pState.getPicks(speakerOrderDraftable.getType())
                        .getFirst()
                        .getChoiceKey();
                return SpeakerOrderDraftable.getSpeakerOrderFromChoiceKey(pickChoiceKey);
            }
        } else if (andcatDraftable != null) {
            if (pState.getPickCount(andcatDraftable.getType()) > 0) {
                String pickChoiceKey =
                        pState.getPicks(andcatDraftable.getType()).getFirst().getChoiceKey();
                ReferenceCardPackage refPackage = andcatDraftable.getPackageByChoiceKey(pickChoiceKey);
                List<String> speakerOrder = andcatDraftable.getSpeakerOrder(draftManager);
                if (speakerOrder != null && refPackage.speakerOrderFaction() != null) {
                    int orderIndex = speakerOrder.indexOf(playerUserId);
                    return orderIndex < 0 ? null : orderIndex + 1; // speaker order is 1-based
                }
            }
        }

        return null;
    }

    private FactionModel getPlayerHsFactionModel(DraftManager draftManager, PlayerDraftState pState) {
        if (pState.getPickCount(FactionDraftable.TYPE) > 0) {
            String factionId = pState.getPicks(FactionDraftable.TYPE).getFirst().getChoiceKey();
            return Mapper.getFaction(factionId);
        }
        if (pState.getPickCount(AndcatReferenceCardsDraftable.TYPE) > 0) {
            String choiceKey = pState.getPicks(AndcatReferenceCardsDraftable.TYPE)
                    .getFirst()
                    .getChoiceKey();
            AndcatReferenceCardsDraftable arcDraftable =
                    (AndcatReferenceCardsDraftable) draftManager.getDraftable(AndcatReferenceCardsDraftable.TYPE);
            ReferenceCardPackage refPackage = arcDraftable.getPackageByChoiceKey(choiceKey);
            if (refPackage.choicesFinal() != null
                    && refPackage.choicesFinal()
                    && refPackage.homeSystemFaction() != null) {
                String factionId = refPackage.homeSystemFaction();
                return Mapper.getFaction(factionId);
            }
        }
        return null;
    }

    private MiltyDraftSlice getPlayerSlice(PlayerDraftState pState, SliceDraftable sliceDraftable) {
        if (pState.getPickCount(SliceDraftable.TYPE) > 0) {
            String sliceName = pState.getPicks(SliceDraftable.TYPE).getFirst().getChoiceKey();
            return sliceDraftable.getSliceByName(sliceName);
        }
        return null;
    }

    private String getMapTemplateModelId(DraftManager draftManager) {
        String mapTemplateId = draftManager.getGame().getMapTemplateID();
        if (mapTemplateId == null || mapTemplateId.isEmpty()) {
            MapTemplateModel mapTemplateModel = Mapper.getDefaultMapTemplateForPlayerCount(
                    draftManager.getPlayerStates().size());
            if (mapTemplateModel == null) {
                throw new IllegalStateException("No default map template for "
                        + draftManager.getPlayerStates().size() + " players");
            }
            mapTemplateId = mapTemplateModel.getAlias();
        }
        return mapTemplateId;
    }
}
