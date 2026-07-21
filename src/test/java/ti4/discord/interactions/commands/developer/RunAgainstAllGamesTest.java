package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void shouldSwapRemovedEronousFactionToRandomOfficialFaction() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            plantEronousFaction(harness.getGameName());
            Game game = harness.load();

            Player swapped = game.getPlayers().values().stream()
                    .filter(p -> "canto".equals(p.getFaction()))
                    .findFirst()
                    .orElseThrow();
            String swappedUserId = swapped.getUserID();

            boolean changed = RunAgainstAllGames.removeEronousFactions(game);

            assertThat(changed).isTrue();
            String newFaction = swapped.getFaction();
            assertThat(newFaction).isNotEqualTo("canto");
            FactionModel newFactionModel = Mapper.getFaction(newFaction);
            assertThat(newFactionModel).isNotNull();
            assertThat(newFactionModel.getSource().isOfficial()).isTrue();
            assertThat(game.getPlayers().values().stream()
                            .filter(p -> newFaction.equals(p.getFaction()))
                            .count())
                    .isEqualTo(1);

            // No Eronous component ids may remain on any player
            for (Player p : game.getPlayers().values()) {
                assertThat(p.getTechs()).doesNotContain("cantoy", "cantor");
                assertThat(p.getExhaustedTechs()).doesNotContain("cantoy", "cantor");
                assertThat(p.getFactionTechs()).doesNotContain("cantoy", "cantor");
                assertThat(p.getPlanets()).doesNotContain("thyolcian");
                assertThat(p.getUnitsOwned()).doesNotContain("canto_flagship", "canto_mech", "eidolon_fighter");
                assertThat(p.getPromissoryNotesOwned()).doesNotContain("cantopn");
                assertThat(p.getPromissoryNotes()).doesNotContainKey("cantopn");
                assertThat(p.getLeaders().stream().map(Leader::getId))
                        .doesNotContain("cantoagent", "cantocommander", "cantohero");
                assertThat(p.getAbilities()).doesNotContain("enslave", "dominate", "seamless_integration");
            }

            // The Eronous home system was replaced in place by the new faction's home system
            assertThat(game.getTileMap().values().stream().map(Tile::getTileID)).doesNotContain("as01");
            Tile newHomeTile = game.getTileByPosition("210");
            assertThat(newHomeTile).isNotNull();
            assertThat(newHomeTile.getTileID()).isEqualTo(AliasHandler.resolveTile(newFactionModel.getHomeSystem()));
            for (String homePlanet : newFactionModel.getHomePlanets()) {
                assertThat(swapped.getPlanets()).contains(AliasHandler.resolvePlanet(homePlanet.toLowerCase()));
            }

            // Running again makes no further changes
            assertThat(RunAgainstAllGames.removeEronousFactions(game)).isFalse();

            // The updated game round-trips through save/load
            GameManager.save(game, "test");
            Game reloaded = harness.load();
            Player reloadedPlayer = reloaded.getPlayer(swappedUserId);
            assertThat(reloadedPlayer.getFaction()).isEqualTo(newFaction);
        }
    }

    /**
     * Rewrites the copied test map so the first player is playing the removed Eronous faction "canto"
     * (with its leaders, techs, abilities, planets, and promissory note), a second player holds stray
     * Eronous components, and the canto home system tile as01 is on the map.
     */
    private static void plantEronousFaction(String gameName) throws IOException {
        Path path = Storage.getGamePath(gameName + Constants.TXT);
        List<String> lines = Files.readAllLines(path);
        int playerIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if ("-player-".equals(line)) {
                playerIndex++;
                continue;
            }
            if ("43 210".equals(line)) {
                lines.set(i, "as01 210");
                continue;
            }
            if (playerIndex == 1) {
                if (line.startsWith("faction ")) lines.set(i, "faction canto");
                else if (line.startsWith("faction_tech ")) lines.set(i, "faction_tech cantoy,cantor");
                else if (line.startsWith("tech ")) lines.set(i, line + ",cantoy");
                else if (line.startsWith("planets ")) lines.set(i, line + ",thyolcian");
                else if (line.startsWith("promissory_notes_owned ")) lines.set(i, line + ",cantopn");
                else if (line.startsWith("abilities ")) lines.set(i, "abilities enslave,dominate,seamless_integration");
                else if (line.startsWith("leaders "))
                    lines.set(
                            i,
                            "leaders cantoagent,agent,0,false,false,false;cantocommander,commander,0,false,false,false;cantohero,hero,0,true,false,false;");
            } else if (playerIndex == 2) {
                if (line.startsWith("promissory_notes ")) lines.set(i, line + "cantopn,55;");
                else if (line.startsWith("units_owned ")) lines.set(i, line + ",eidolon_fighter");
            }
        }
        Files.write(path, lines);
    }
}
