package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.WormholeModel.Wormhole;
import ti4.testUtils.BaseTi4Test;

class WormholeTokenMergeTest extends BaseTi4Test {

    private static final String ALPHA = "token_whalpha.png";
    private static final String BETA = "token_whbeta.png";
    private static final String GAMMA = "token_whgamma.png";
    private static final String CREUSS_BETA = "token_creussbeta.png";

    @Test
    void groupsHoldAtMostThreeWormholesAndCountDuplicates() {
        assertThat(sizes(ALPHA, BETA)).containsExactly(2);
        assertThat(sizes(ALPHA, BETA, GAMMA, "token_whdelta.png")).containsExactly(2, 2);
        assertThat(sizes(ALPHA, BETA, GAMMA, "token_whdelta.png", "token_custom_eronous_wheta.png"))
                .containsExactly(3, 2);
        assertThat(sizes(
                        ALPHA,
                        BETA,
                        GAMMA,
                        "token_whdelta.png",
                        "token_whmu.png",
                        "token_whnu.png",
                        "token_whxi.png",
                        "token_whomega.png"))
                .containsExactly(3, 3, 2);
        // Sorted by colour and dealt round-robin: delta (blue) and xi (azure) end up in different icons.
        assertThat(WormholeTokenMerge.group(List.of(ALPHA, BETA, "token_whdelta.png", "token_whxi.png")))
                .noneMatch(group -> group.stream()
                        .map(WormholeTokenMerge.Part::wormhole)
                        .toList()
                        .containsAll(List.of(Wormhole.DELTA, Wormhole.XI)));
        // Two images of the same wormhole (the legacy eta file and the current one) share one wedge.
        assertThat(WormholeTokenMerge.group(List.of("token_wheta.png", ALPHA, "token_custom_eronous_wheta.png"))
                        .getFirst())
                .extracting(WormholeTokenMerge.Part::wormhole, WormholeTokenMerge.Part::count)
                .containsExactly(tuple(Wormhole.ALPHA, 1), tuple(Wormhole.ETA, 2));
    }

    @Test
    void onlyPlainWormholeTokensMergeInPlaceOfTheFirstOne() {
        // Creuss tokens keep their own icon (Wormhole Generator moves specific ones); frontier is no wormhole.
        WormholeTokenMerge.DrawOrder order =
                WormholeTokenMerge.apply(List.of("token_frontier.png", ALPHA, CREUSS_BETA, BETA), 2);

        assertThat(order.tokens()).hasSize(3).startsWith("token_frontier.png").endsWith(CREUSS_BETA);
        WormholeTokenMerge.Merged merged = order.merged().get(order.tokens().get(1));
        assertThat(merged.wormholes()).containsExactly(Wormhole.ALPHA, Wormhole.BETA);
        assertThat(merged.image().getWidth()).isEqualTo(80);

        List<String> oneWormhole = List.of(ALPHA, CREUSS_BETA);
        assertThat(WormholeTokenMerge.apply(oneWormhole, 2).tokens()).isEqualTo(oneWormhole);
        // Below the game's threshold nothing merges.
        List<String> twoWormholes = List.of(ALPHA, BETA);
        assertThat(WormholeTokenMerge.apply(twoWormholes, 3).tokens()).isEqualTo(twoWormholes);
    }

    @Test
    void ratRaceTokensMergeOnlyWithEachOther() {
        String ratAlpha = "token_whratalpha.png";
        String ratBeta = "token_whratbeta.png";
        WormholeTokenMerge.DrawOrder order = WormholeTokenMerge.apply(List.of(ALPHA, ratAlpha, BETA, ratBeta), 2);

        assertThat(order.tokens()).hasSize(2);
        assertThat(order.tokens().stream().map(key -> order.merged().get(key).wormholes()))
                .containsExactly(
                        List.of(Wormhole.ALPHA, Wormhole.BETA), List.of(Wormhole.ALPHA_RAT, Wormhole.BETA_RAT));

        // Each family needs the threshold on its own: one rat token stays a single token.
        WormholeTokenMerge.DrawOrder oneRat = WormholeTokenMerge.apply(List.of(ALPHA, ratAlpha, BETA), 2);
        assertThat(oneRat.tokens()).hasSize(2).contains(ratAlpha);
    }

    @Test
    void mapOptionSwitchesBetweenSingleAndMergedTokens() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("wormhole-merge-test");
        Tile tile = new Tile("19", "101");
        game.setTile(tile);
        List.of(ALPHA, BETA, GAMMA).forEach(tile.getSpaceUnitHolder()::addToken);

        BufferedImage singles = render(tile, game);
        game.setWormholeTokenMergeFrom(2);

        assertThat(pixels(render(tile, game))).isNotEqualTo(pixels(singles));
    }

    private static List<Integer> sizes(String... tokens) {
        return WormholeTokenMerge.group(List.of(tokens)).stream()
                .map(List::size)
                .toList();
    }

    private static BufferedImage render(Tile tile, Game game) {
        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        SpaceTokens.draw(tile, g, tile.getSpaceUnitHolder(), game);
        g.dispose();
        return image;
    }

    private static List<Integer> pixels(BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                .boxed()
                .toList();
    }
}
