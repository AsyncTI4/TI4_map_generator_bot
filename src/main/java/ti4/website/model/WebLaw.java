package ti4.website.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.game.Game;
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
            // Check if it's a player faction
            if (Mapper.isValidFaction(electedInfo)) {
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
