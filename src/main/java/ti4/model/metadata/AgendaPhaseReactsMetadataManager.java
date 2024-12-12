package ti4.model.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

@UtilityClass
public class AgendaPhaseReactsMetadataManager {

    private static final String AGENDA_PHASE_REACTS_FILE = "AgendaPhaseReacts.json";

    public static synchronized void updateAgendaPhaseReacts(String gameName, String playerId, boolean whens) {
        AllAgendaPhaseReacts agendaPhaseReacts = readFile();
        if (agendaPhaseReacts == null) {
            return;
        }

        GameAgendaPhaseReacts gameAgendaPhaseReacts = agendaPhaseReacts.gameNameToAllAgendaPhaseReacts
            .computeIfAbsent(gameName, k -> new GameAgendaPhaseReacts(new ArrayList<>(), new ArrayList<>()));

        if (whens) {
            gameAgendaPhaseReacts.whenReacts.add(playerId);
        } else {
            gameAgendaPhaseReacts.afterReacts.add(playerId);
        }

        persistFile(agendaPhaseReacts);
    }

    public static AllAgendaPhaseReacts readFile() {
        try {
            var allAgendaPhaseReacts = PersistenceManager.readObjectFromJsonFile(AGENDA_PHASE_REACTS_FILE, AllAgendaPhaseReacts.class);
            return allAgendaPhaseReacts != null ? allAgendaPhaseReacts : new AllAgendaPhaseReacts(new HashMap<>());
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for AllAgendaPhaseReacts.", e);
            return null;
        }
    }

    public static void persistFile(AllAgendaPhaseReacts toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(AGENDA_PHASE_REACTS_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for AllAgendaPhaseReacts.", e);
        }
    }

    public record AllAgendaPhaseReacts(Map<String, GameAgendaPhaseReacts> gameNameToAllAgendaPhaseReacts) {}

    public record GameAgendaPhaseReacts(List<String> whenReacts, List<String> afterReacts) {}
}
