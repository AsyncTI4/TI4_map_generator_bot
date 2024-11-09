package ti4.map;

import java.util.List;

import lombok.Getter;

@Getter
public class MinifiedGame {

    private final String name;
    private final boolean hasEnded;
    private final long lastModifiedDate;
    private final String creationDate;
    private final boolean playerHasReachedVpTotal;
    private final List<String> runMigrations;

    public MinifiedGame(Game game) {
        name = game.getName();
        hasEnded = game.isHasEnded();
        lastModifiedDate = game.getLastModifiedDate();
        creationDate = game.getCreationDate();
        playerHasReachedVpTotal = game.getPlayers().values().stream()
                .anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp());
        runMigrations = game.getRunMigrations();
    }

    public boolean matches(Game game) {
        return name.equals(game.getName()) && lastModifiedDate == game.getLastModifiedDate();
    }

    public boolean hasRunMigration(String migration) {
        return runMigrations.contains(migration);
    }
}
