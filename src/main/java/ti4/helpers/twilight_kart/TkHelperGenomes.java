package ti4.helpers.twilight_kart;

import static ti4.helpers.ButtonHelperAgents.getYinAgentButtons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class TkHelperGenomes {
    private static final String EXHAUST_AGENT = "exhaustAgent_";

    private enum TkGenome {
        DEPLOYMENT,
        SWARM,
        SPLITTING;

        static final String PREFIX = "tknova";
        static final String SUFFIX = "agent";

        String getId() {
            return PREFIX + toString().toLowerCase() + SUFFIX;
        }

        String getName() {
            LeaderModel leaderModel = Mapper.getLeader(getId());
            if (leaderModel != null) {
                return leaderModel.getName();
            }
            return StringUtils.capitalize(toString().toLowerCase()) + " Genome";
        }

        TI4Emoji getEmoji() {
            LeaderModel leaderModel = Mapper.getLeader(getId());
            String faction = leaderModel != null ? leaderModel.getFaction() : "";
            return FactionEmojis.getFactionIcon(faction);
        }

        Button getExhaustButton(Player player) {
            if (!player.hasUnexhaustedLeader(getId())) {
                return null;
            }
            return Buttons.gray(
                    player.factionButtonChecker() + EXHAUST_AGENT + getId(), "Use " + getName(), getEmoji());
        }

        static Optional<TkGenome> fromId(String id) {
            if (!id.startsWith(PREFIX) || !id.endsWith(SUFFIX)) {
                return Optional.empty();
            }
            String name = id.replace(PREFIX, "").replace(SUFFIX, "").toUpperCase();
            try {
                return Optional.of(TkGenome.valueOf(name));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public static List<Button> getStartOfTurnButtons(Game game, Player player) {
        return Stream.of(TkGenome.DEPLOYMENT)
                .map(genome -> genome.getExhaustButton(player))
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<Button> getEndOfTurnButtons(Game game, Player player) {
        return Stream.of(TkGenome.SWARM)
                .map(genome -> genome.getExhaustButton(player))
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<Button> getGeneralCombatButtons(
            Game game, String pos, Player p1, Player p2, Player agentHolder, String factionChecker) {
        List<Button> buttons = new ArrayList<>();
        if (game.isFowMode() && agentHolder != p1) {
            return buttons;
        }

        if (agentHolder.hasUnexhaustedLeader(TkGenome.SPLITTING.getId())) {
            /*buttons.add(Buttons.gray(
            factionChecker + "yinagent_" + pos,
            "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Yin Agent",
            FactionEmojis.Yin));*/
            buttons.add(Buttons.gray(
                    factionChecker + EXHAUST_AGENT + TkGenome.SPLITTING.getId() + "_" + pos,
                    "Use " + TkGenome.SPLITTING.getName(),
                    FactionEmojis.Yin));
        }

        return buttons;
    }

    public static void onExhaust(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String agent,
            String ssruuClever,
            String rest) {
        TkGenome tkGenome = TkGenome.fromId(agent).orElse(null);
        if (tkGenome == null) {
            return;
        }
        String name = tkGenome.getName();
        String exhaustText =
                String.format("%s has exhausted the %s_%s_.", player.getRepresentation(), ssruuClever, name);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), exhaustText);
        switch (tkGenome) {
            case TkGenome.DEPLOYMENT -> ButtonHelper.resolveTransitDiodesStep1(game, player);

            case TkGenome.SWARM -> {
                String text = player.getRepresentation() + ", use buttons to drop 2 infantry on a planet.";
                List<Button> buttons = new ArrayList<>(
                        Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
                if (!buttons.isEmpty()) {
                    buttons.add(Buttons.red("deleteButtons", "Done"));
                }
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), text, buttons);
            }

            case TkGenome.SPLITTING -> {
                String[] args = rest.split("_");
                if (args.length < 2) {
                    return;
                }
                String pos = args[1];

                Player targetPlayer = player;
                if (args.length >= 3) {
                    String targetFaction = args[2];
                    targetPlayer = game.getPlayerFromColorOrFaction(targetFaction);
                }

                if (targetPlayer == null) {
                    return;
                }
                MessageChannel yinChannel = event.getMessageChannel();
                if (game.isFowMode()) {
                    yinChannel = targetPlayer.getPrivateChannel();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the chosen player.");
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        yinChannel,
                        targetPlayer.getRepresentationUnfogged() + ", use buttons to resolve the " + ssruuClever + name,
                        getYinAgentButtons(targetPlayer, game, pos));
            }
        }
    }
}
