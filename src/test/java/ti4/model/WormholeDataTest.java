package ti4.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.model.WormholeModel.Wormhole;
import ti4.testUtils.BaseTi4Test;

class WormholeDataTest extends BaseTi4Test {

    // The enum as it was before wormholes came from data. The old file-name parser depends on this order.
    private static final List<String> OLD_ENUM = List.of(("alpha beta gamma delta epsilon zeta eta theta iota kappa"
                    + " lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega custom_eronous_whepsilon"
                    + " custom_eronous_wheta custom_eronous_whiota custom_eronous_whtheta custom_eronous_whzeta"
                    + " whnarrows whprobability whchampion whvoyage vortex")
            .split(" "));

    // Old source wormholes that were never real: the file name merely contained the letters.
    private static final Map<String, Set<Wormhole>> INTENDED_SOURCE_CHANGES = Map.of(
            "token_sigma_lambda.png", EnumSet.of(Wormhole.LAMBDA),
            "token_sigma_palace_emperors.png", EnumSet.noneOf(Wormhole.class),
            "token_mustache.png", EnumSet.noneOf(Wormhole.class));

    // Old target matches that were never real, per source wormhole.
    private static final Map<String, Predicate<String>> INTENDED_TARGET_CHANGES = Map.of(
            "eta",
                    file -> "attachment_interplanetaryhub.png".equals(file)
                            || "attachment_planetaryarcologies.png".equals(file),
            "sigma", file -> file.contains("sigma"),
            "mu", file -> "token_mustache.png".equals(file));

    // Images on disk that look like wormholes but were never addable, or are not wormholes (whall).
    private static final Set<String> NOT_WORMHOLE_IMAGES = Set.of(
            "token_whunknown.png",
            "token_custom_eronous_whalpha.png",
            "token_custom_eronous_whbeta.png",
            "token_custom_eronous_whno.png");

    @Test
    void lookupMatchesTheOldFileNameParserExceptKnownFalseWormholes() {
        for (String file : allTokenFileNames()) {
            Set<Wormhole> expected = INTENDED_SOURCE_CHANGES.getOrDefault(file, translate(oldSourceParse(file)));
            assertThat(asSet(Mapper.getTokenWormholes(file))).as(file).isEqualTo(expected);
        }
    }

    @Test
    void targetMatchingMatchesTheOldSubstringRuleExceptKnownFalseMatches() {
        Set<String> oldSources = new TreeSet<>(List.of("mu", "sigma", "vortex"));
        oldSources.addAll(List.of("whnarrows", "whprobability", "whchampion", "whvoyage"));
        TileHelper.getAllTileModels().stream()
                .filter(tile -> tile.getWormholes() != null)
                .flatMap(tile -> tile.getWormholes().stream())
                .filter(Objects::nonNull)
                .forEach(wormhole -> oldSources.add(wormhole.toString()));

        for (String source : oldSources) {
            Wormhole wormhole = translate(source);
            Predicate<String> intended = INTENDED_TARGET_CHANGES.getOrDefault(source, file -> false);
            for (String file : allTokenFileNames()) {
                if (intended.test(file)) continue;
                assertThat(Mapper.getTokenWormholes(file).contains(wormhole))
                        .as(source + " -> " + file)
                        .isEqualTo(oldTargetMatch(file, source));
            }
        }
    }

    @Test
    void entriesSharingAnImageDeclareTheSameWormholesAndMergeFlag() {
        // The lookups are keyed by image, so entries sharing one must not disagree.
        Map<String, String> byImage = new HashMap<>();
        for (TokenModel token : Mapper.getTokensValues()) {
            String declared =
                    asSet(token.getWormholes()) + " merge=" + !Boolean.FALSE.equals(token.getWormholeIconMerge());
            assertThat(byImage.merge(token.getImagePath(), declared, (a, b) -> a))
                    .as(token.getId())
                    .isEqualTo(declared);
        }
    }

    @Test
    void everyWormholeLookingImageDeclaresWormholes() {
        wormholeLookingImages("token_(wh|creuss|ion|gamma|custom_eronous_wh).*\\.png")
                .forEach(name ->
                        assertThat(Mapper.getTokenWormholes(name)).as(name).isNotEmpty());
    }

    private static Set<String> allTokenFileNames() {
        Set<String> files = new TreeSet<>();
        Mapper.getTokensFromProperties().stream()
                .map(Mapper::getTokenID)
                .filter(Objects::nonNull)
                .forEach(files::add);
        for (TokenModel token : Mapper.getTokensValues()) {
            files.add(token.getImagePath());
            if (token.getLegacyImagePaths() != null) files.addAll(token.getLegacyImagePaths());
        }
        Mapper.getAttachments().values().forEach(attachment -> files.add(attachment.getImagePath()));
        files.addAll(wormholeLookingImages("token_(wh|custom_eronous_wh).*\\.png"));
        return files;
    }

    private static List<String> wormholeLookingImages(String pattern) {
        String[] images = new File("src/main/resources/tokens").list();
        assertThat(images).isNotNull();
        return Arrays.stream(images)
                .filter(name -> name.matches(pattern))
                .filter(name -> !name.contains(" copy") && !NOT_WORMHOLE_IMAGES.contains(name))
                .toList();
    }

    // Verbatim copy of the removed FoWHelper source-side parser.
    private static Set<String> oldSourceParse(String token) {
        Set<String> ids = new TreeSet<>();
        String tokenName =
                "wh" + token.replace("token_", "").replace(".png", "").replace("creuss", "");
        if (!tokenName.contains("champion")) {
            tokenName = tokenName.replace("ion", "");
        }
        for (String wh : OLD_ENUM) {
            if (tokenName.contains("wh" + wh)) {
                ids.add(wh);
                break;
            }
        }
        if (tokenName.contains("sigma_weirdway")) {
            ids.add("alpha");
            ids.add("beta");
        }
        return ids;
    }

    // Verbatim copy of the removed FoWHelper target-side matching for one token.
    private static boolean oldTargetMatch(String token, String wormholeID) {
        boolean match = token.contains(wormholeID)
                && !("eta".equals(wormholeID)
                        && (token.contains("beta") || token.contains("theta") || token.contains("zeta")));
        boolean weirdway =
                token.contains("sigma_weirdway") && ("alpha".equals(wormholeID) || "beta".equals(wormholeID));
        return match || weirdway;
    }

    private static Set<Wormhole> translate(Set<String> oldIds) {
        return oldIds.stream()
                .map(WormholeDataTest::translate)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Wormhole.class)));
    }

    private static Wormhole translate(String oldId) {
        String name = oldId.replace("custom_eronous_wh", "").replaceFirst("^wh", "");
        return Wormhole.valueOf(name.toUpperCase());
    }

    private static Set<Wormhole> asSet(List<Wormhole> wormholes) {
        Set<Wormhole> set = EnumSet.noneOf(Wormhole.class);
        if (wormholes != null) set.addAll(wormholes);
        return set;
    }
}
