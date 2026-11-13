package ti4.service.tournament;

import java.util.List;
import ti4.game.Game;

public interface TournamentSetupOption {

    String id();

    String label();

    String description();

    List<String> choices();

    void apply(Game game, String choice);
}
