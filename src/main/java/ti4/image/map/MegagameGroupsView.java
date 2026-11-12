package ti4.image.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.StrategyCardModel;
import ti4.service.megagame.MegagameGroupService;

public record MegagameGroupsView(String gameName, String title, List<Group> groups, boolean showCards) {

    public record Group(String id, List<Member> members) {}

    public record Member(
            String faction, @Nullable String customEmoji, boolean speaker, boolean picking, List<Card> cards) {}

    public record Card(int number, int iconNumber, String name, boolean played) {}

    public static MegagameGroupsView of(Game game) {
        return of(game, MegagameGroupService.currentLayout(game), true);
    }

    public static MegagameGroupsView of(Game game, MegagameGroupService.Layout layout, boolean showCards) {
        boolean picking = showCards && "strategy".equalsIgnoreCase(game.getPhaseOfGame());
        List<Group> groups = layout.groups().stream()
                .map(group -> group(game, layout, group, picking, showCards))
                .toList();
        return new MegagameGroupsView(game.getName(), title(game, groups, showCards), groups, showCards);
    }

    private static String title(Game game, List<Group> groups, boolean showCards) {
        if (!showCards) {
            return "Round " + (game.getRound() + 1) + "  ·  Next round groups";
        }
        List<String> parts = new ArrayList<>();
        parts.add("Round " + game.getRound());
        String phase = phaseName(game.getPhaseOfGame());
        if (phase != null) parts.add(phase);
        List<String> picking = groups.stream()
                .filter(group -> group.members().stream().anyMatch(Member::picking))
                .map(group -> "Group " + MegagameGroupService.name(group.id()))
                .toList();
        if (!picking.isEmpty()) parts.add(String.join(", ", picking) + " picking");
        return String.join("  ·  ", parts);
    }

    @Nullable
    private static String phaseName(@Nullable String phaseOfGame) {
        if (phaseOfGame == null) return null;
        String phase = phaseOfGame.toLowerCase();
        for (String name : List.of("strategy", "action", "status", "agenda")) {
            if (phase.startsWith(name)) return StringUtils.capitalize(name) + " phase";
        }
        return null;
    }

    int rows() {
        return groups.stream().mapToInt(group -> group.members().size()).max().orElse(0);
    }

    private static Group group(
            Game game, MegagameGroupService.Layout layout, String group, boolean picking, boolean showCards) {
        Player speaker = layout.speakers().get(group);
        Player picker = picking ? MegagameGroupService.currentPicker(game, group) : null;
        List<Member> members = layout.members().get(group).stream()
                .map(player -> new Member(
                        player.getFaction(),
                        player.hasCustomFactionEmoji() ? player.getFactionEmoji() : null,
                        player.equals(speaker),
                        player.equals(picker),
                        showCards ? cards(game, player) : List.of()))
                .toList();
        return new Group(group, members);
    }

    private static List<Card> cards(Game game, Player player) {
        return player.getSCs().stream().map(sc -> card(game, sc)).toList();
    }

    private static Card card(Game game, int sc) {
        Optional<StrategyCardModel> model = game.getStrategyCardModelByInitiative(sc);
        int iconNumber = model.map(StrategyCardModel::getBotSCAutomationID)
                .map(Mapper::getStrategyCard)
                .map(StrategyCardModel::getInitiative)
                .orElse(sc);
        return new Card(
                sc,
                iconNumber,
                model.map(StrategyCardModel::getName).orElse(""),
                Boolean.TRUE.equals(game.getScPlayed().get(sc)));
    }
}
