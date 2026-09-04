package ti4.website.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.model.AgendaModel;

@Data
public class WebLaw {
    // Basic law identification
    private String id;
    private String name;
    private Integer uniqueId;

    // Agenda properties
    private String type;
    private String target;
    private String text1;
    private String text2;
    private String mapText;

    // Election information
    private String electedInfo;
    private String electedFaction;
    private String electedType; // "player", "planet", "objective", "other"

    // Control tokens attached to this law
    private List<String> controlTokens;

    // Additional metadata
    private boolean displaysElectedFaction;

    /**
     * Obscures which faction was elected by a law for players the viewer can't identify
     * (FoWHelper#canSeeStatsOfPlayer), substituting the same "fow:&lt;color&gt;" sentinel used for
     * control tokens and units (see WebTileUnitData#redactControlIdentities). Which color got
     * elected is announced publicly, so it stays visible - only the faction behind it is hidden.
     * electedInfo is dropped outright since it's the raw elected string and would leak the faction.
     */
    public static void redactElectedFaction(List<WebLaw> lawsInPlay, Game game, Player viewer) {
        for (WebLaw law : lawsInPlay) {
            if (!"player".equals(law.electedType) || law.electedFaction == null) {
                continue;
            }
            Player electedPlayer = game.getPlayerFromColorOrFaction(law.electedFaction);
            if (electedPlayer != null && !FoWHelper.canSeeStatsOfPlayer(game, electedPlayer, viewer)) {
                law.electedFaction = WebTileUnitData.UNKNOWN_FACTION_PREFIX + electedPlayer.getColor();
                law.electedInfo = null;
            }
        }
    }

    public static WebLaw fromGameLaw(String lawId, Integer uniqueId, Game game) {
        WebLaw webLaw = new WebLaw();

        // Basic identification
        webLaw.id = lawId;
        webLaw.uniqueId = uniqueId;

        // Get agenda model
        AgendaModel agendaModel = Mapper.getAgenda(lawId);
        if (agendaModel != null) {
            webLaw.name = agendaModel.getName();
            webLaw.type = agendaModel.getType();
            webLaw.target = agendaModel.getTarget();
            webLaw.text1 = agendaModel.getText1();
            webLaw.text2 = agendaModel.getText2();
            webLaw.mapText = agendaModel.getMapText();
            webLaw.displaysElectedFaction = agendaModel.displayElectedFaction();
        }

        // Get elected information
        String electedInfo = game.getLawsInfo().get(lawId);
        webLaw.electedInfo = electedInfo;

        if (electedInfo != null && !electedInfo.isEmpty()) {
            // Check if it's a player - elections are recorded as either the faction or the
            // color (see IsPlayerElectedService, which checks both), so resolve via
            // getPlayerFromColorOrFaction rather than only matching a faction string.
            Player electedPlayer = game.getPlayerFromColorOrFaction(electedInfo);
            if (electedPlayer != null) {
                webLaw.electedType = "player";
                webLaw.electedFaction = electedPlayer.getFaction();
            }
            // The election named a faction that no current player resolves to - it's still a
            // player election, so keep showing it rather than falling through to "other".
            // getPlayerFromColorOrFaction only matches players presently in the game, whereas a
            // recorded election can outlive them (a player leaving, or a stale color recorded
            // before a /player change_color, which doesn't rewrite lawsInfo).
            else if (Mapper.isValidFaction(electedInfo)) {
                webLaw.electedType = "player";
                webLaw.electedFaction = electedInfo;
            }
            // Check if it's a planet
            else if (Mapper.isValidPlanet(electedInfo)) {
                webLaw.electedType = "planet";
            }
            // Check if it's a secret objective
            else if (Mapper.isValidSecretObjective(electedInfo)) {
                webLaw.electedType = "objective";
            }
            // Otherwise it's some other type of election
            else {
                webLaw.electedType = "other";
            }
        }

        // Get control tokens attached to this law
        List<String> controlTokens = new ArrayList<>();
        String controlTokensStoredValue = game.getStoredValue("controlTokensOnAgenda" + uniqueId);
        if (!controlTokensStoredValue.isEmpty()) {
            String[] tokens = controlTokensStoredValue.split("_");
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    controlTokens.add(token);
                }
            }
        }
        webLaw.controlTokens = controlTokens;

        return webLaw;
    }
}
