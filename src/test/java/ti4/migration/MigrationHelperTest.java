package ti4.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.map.Game;

class MigrationHelperTest {

    @Test
    void replaceAgendaCardsReplacesMisspelledIdsAcrossAllAgendaAreas() {
        Game game = new Game();
        game.setAgendaDeckID("agendas_pok");
        game.setAgendas(new ArrayList<>(List.of("minister_commrece", "disarmamament", "minister_commrece")));

        Map<String, Integer> discardAgendas = new LinkedHashMap<>();
        discardAgendas.put("minister_commrece", 1);
        game.setDiscardAgendas(discardAgendas);

        Map<String, Integer> sentAgendas = new LinkedHashMap<>();
        sentAgendas.put("disarmamament", 2);
        game.setSentAgendas(sentAgendas);

        Map<String, Integer> laws = new LinkedHashMap<>();
        laws.put("senate_sancturary", 3);
        game.setLaws(laws);

        Map<String, String> lawsInfo = new LinkedHashMap<>();
        lawsInfo.put("senate_sancturary", "sol");
        game.setLawsInfo(lawsInfo);

        boolean replaced = MigrationHelper.replaceAgendaCards(
                game,
                List.of("agendas_pok"),
                Map.of(
                        "disarmamament", "disarmament",
                        "minister_commrece", "minister_commerce",
                        "senate_sancturary", "senate_sanctuary"));

        assertTrue(replaced);
        assertEquals(List.of("minister_commerce", "disarmament", "minister_commerce"), game.getAgendas());
        assertTrue(game.getDiscardAgendas().containsKey("minister_commerce"));
        assertFalse(game.getDiscardAgendas().containsKey("minister_commrece"));
        assertTrue(game.getSentAgendas().containsKey("disarmament"));
        assertFalse(game.getSentAgendas().containsKey("disarmamament"));
        assertTrue(game.getLaws().containsKey("senate_sanctuary"));
        assertFalse(game.getLaws().containsKey("senate_sancturary"));
        assertTrue(game.getLawsInfo().containsKey("senate_sanctuary"));
        assertFalse(game.getLawsInfo().containsKey("senate_sancturary"));
    }
}
