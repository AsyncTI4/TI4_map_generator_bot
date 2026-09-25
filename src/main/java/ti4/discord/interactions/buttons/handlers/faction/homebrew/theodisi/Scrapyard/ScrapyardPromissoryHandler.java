package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Scrapyard;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ScrapyardPromissoryHandler {
    private static final String SECOND_HAND_SALE = "thpnscrapyard";
    private static final String GAIN_RIG = "gainScrapyardRig_";

    public static void offerUnusedCustomRigs(Game game, Player player) {
        if (game == null || player == null || !player.hasPlayablePromissoryInHand(SECOND_HAND_SALE)) {
            return;
        }

        List<Button> unusedRigs = new ArrayList<>();
        for (String rig : ScrapyardAbilitiesHandler.CUSTOM_RIGS) {
            if (game.getRealPlayers().stream().anyMatch(rigOwner -> rigOwner.hasAbility(rig))) {
                continue;
            }
            AbilityModel rigAbilityModel = Mapper.getAbility(rig);
            if (rigAbilityModel != null) {
                unusedRigs.add(Buttons.green(
                        player.factionButtonChecker() + GAIN_RIG + rig,
                        rigAbilityModel.getName(),
                        FactionEmojis.scrapyard));
            }
        }

        if (unusedRigs.isEmpty()) {
            purgeSecondHandSale(game, player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " had no unused _Custom Rig_ cards to gain and purged _Second Hand Sale_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please select which custom rig you would like to gain.",
                unusedRigs);
    }

    @ButtonHandler(GAIN_RIG)
    public static void gainUnusedCustomRig(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rigToGain = buttonID.substring(GAIN_RIG.length());
        AbilityModel rigModel = Mapper.getAbility(rigToGain);
        if (rigModel == null
                || !ScrapyardAbilitiesHandler.CUSTOM_RIGS.contains(rigToGain)
                || !player.hasPlayablePromissoryInHand(SECOND_HAND_SALE)
                || game.getRealPlayers().stream().anyMatch(rigOwner -> rigOwner.hasAbility(rigToGain))) {
            return;
        }

        player.addAbility(rigToGain);
        player.addAbility("drive_decoherence");
        purgeSecondHandSale(game, player);

        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " gained " + rigModel.getName() + " and purged _Second Hand Sale_."
                        + "\nThey also gained the ability _Drive Decoherence_. This is intentional.",
                rigModel.getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    private static void purgeSecondHandSale(Game game, Player player) {
        Player owner = game.getPNOwner(SECOND_HAND_SALE);
        game.setPurgedPN(SECOND_HAND_SALE);
        player.removePromissoryNote(SECOND_HAND_SALE);
        if (owner != null) {
            owner.removePromissoryNote(SECOND_HAND_SALE);
            owner.removeOwnedPromissoryNoteByID(SECOND_HAND_SALE);
        }
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        if (owner != null && owner != player) {
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        }
    }
}
