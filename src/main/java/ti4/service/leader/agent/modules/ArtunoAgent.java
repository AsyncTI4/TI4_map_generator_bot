package ti4.service.leader.agent.modules;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class ArtunoAgent extends AgentModule<Integer> {

    static final String ID = "nomadagentartuno";
    private static final int JOKE_ODDS = 10;

    public ArtunoAgent() {
        super(ID, "Artuno the Betrayer, a Nomad");
    }

    public static Button button(Player artuno, int tradeGoods) {
        return Buttons.green(
                ownedButtonId(artuno, ID, String.valueOf(tradeGoods)),
                "Exhaust Artuno With " + StringHelper.pluralize(tradeGoods, "TG"));
    }

    @Override
    public Optional<Integer> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(0);
        }
        if (!StringUtils.isNumeric(payload)) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(payload));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Integer> use) {
        return placeTradeGoodsOnCard(use, ID);
    }

    public static AgentOutcome placeTradeGoodsOnCard(AgentUse<Integer> use, String leaderId) {
        int tradeGoods = use.payload();
        if (tradeGoods == 0) {
            return AgentOutcome.none();
        }
        Player artuno = use.user();
        artuno.getLeader(leaderId).ifPresent(leader -> leader.setTgCount(tradeGoods));
        tellAJokeSometimes(artuno);
        return AgentOutcome.of(Message.addressedTo(
                artuno,
                " placed " + StringHelper.pluralize(tradeGoods, "trade good") + " on top of " + use.agentName() + "."));
    }

    private static void tellAJokeSometimes(Player artuno) {
        MessageChannel channel = artuno.getCorrectChannel();
        if (channel == null || ThreadLocalRandom.current().nextInt(1, JOKE_ODDS + 1) != JOKE_ODDS) {
            return;
        }
        File audioFile = ResourceHelper.getFile("voices/nomad/", "Artuno.mp3");
        if (audioFile.exists()) {
            MessageHelper.sendFileToChannel(channel, audioFile);
        }
    }
}
