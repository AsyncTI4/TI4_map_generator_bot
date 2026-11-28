package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class AgendaPriorProbeTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.game.setVp(10);
        test.game.resetAgendas();
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Law_For/Against_7_regulations");
        test.nekroHome();
        String lodor = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", lodor);
        test.solHomeBeside(lodor);
    }

    // One line per seat, carrying the full deck look-ahead that seat would price a kept vote from.
    @Test
    void reportsTheFullDeckSpreadForEverySeat() {
        List<String> lines = AgendaPriorProbe.report("g", test.game);

        assertThat(lines).hasSize(test.game.getRealPlayers().size());
        assertThat(lines.getFirst()).startsWith("AGENDAPRIOR g r3 regulations seat=");
        String sol = lines.stream()
                .filter(line -> line.contains("seat=sol "))
                .findFirst()
                .orElseThrow();
        double reported = Double.parseDouble(sol.replaceAll(".*deckSpread=([0-9.]+) .*", "$1"));
        assertThat(reported).isPositive();
    }
}
