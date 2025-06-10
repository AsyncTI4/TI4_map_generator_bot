package ti4.website;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
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
        webLaw.setId(lawId);
        webLaw.setUniqueId(uniqueId);

        // Get agenda model
        AgendaModel agendaModel = Mapper.getAgenda(lawId);
        if (agendaModel != null) {
            webLaw.setName(agendaModel.getName());
            webLaw.setType(agendaModel.getType());
            webLaw.setTarget(agendaModel.getTarget());
            webLaw.setText1(agendaModel.getText1());
            webLaw.setText2(agendaModel.getText2());
            webLaw.setMapText(agendaModel.getMapText());
            webLaw.setDisplaysElectedFaction(agendaModel.displayElectedFaction());
        }

        // Get elected information
        String electedInfo = game.getLawsInfo().get(lawId);
        webLaw.setElectedInfo(electedInfo);

        if (electedInfo != null && !electedInfo.isEmpty()) {
            // Check if it's a player faction
            if (Mapper.isValidFaction(electedInfo)) {
                webLaw.setElectedType("player");
                webLaw.setElectedFaction(electedInfo);
            }
            // Check if it's a planet
            else if (Mapper.isValidPlanet(electedInfo)) {
                webLaw.setElectedType("planet");
            }
            // Check if it's a secret objective
            else if (Mapper.isValidSecretObjective(electedInfo)) {
                webLaw.setElectedType("objective");
            }
            // Otherwise it's some other type of election
            else {
                webLaw.setElectedType("other");
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
        webLaw.setControlTokens(controlTokens);

        return webLaw;
    }
}