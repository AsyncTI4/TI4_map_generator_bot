package ti4.commands.ds;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.AbilityInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class SetPolicy extends DiscordantStarsSubcommandData {

    public SetPolicy() {
        super(Constants.SET_POLICY, "Set Policies for Olradin faction ability to their + or - side.");
        addOptions(new OptionData(OptionType.STRING, Constants.SET_PEOPLE, "Policy: The People - \"Connect\" (+) or \"Control\" (-)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_ENVIRONMENT, "Policy: The Environment - \"Preserve\" (+) or \"Plunder\" (-)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_ECONOMY, "Policy: The Economy - \"Empower\" (+) or \"Exploit\" (-)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to set Olradin Policies").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        // List<String> playerAbilities = player.getAbilities().stream().sorted().toList();
        OptionMapping policy1 = event.getOption(Constants.SET_PEOPLE);
        OptionMapping policy2 = event.getOption(Constants.SET_ENVIRONMENT);
        OptionMapping policy3 = event.getOption(Constants.SET_ECONOMY);
        String pol1 = null;
        String pol2 = null;
        String pol3 = null;

        if ((policy1 == null) && (policy2 == null) && (policy3 == null)) {
            MessageHelper.sendMessageToEventChannel(event, "Must set at least 1 Policy!");
            return;
        }

        // break down choices into + or - from their original inputs
        if (policy1 != null) {
            pol1 = policy1.getAsString().toLowerCase();
            pol1 = convertChoice(pol1);
            if (pol1 == null) {
                MessageHelper.sendMessageToEventChannel(event,
                    "Received an invalid input for Policy: The People, will either ignore or default to \"Connect\" (+) if this is your first time setting Policies.");
            }
        }
        if (policy2 != null) {
            pol2 = policy2.getAsString().toLowerCase();
            pol2 = convertChoice(pol2);
            if (pol2 == null) {
                MessageHelper.sendMessageToEventChannel(event,
                    "Received an invalid input for Policy: The Environment, will either ignore or default to \"Preserve\" (+) if this is your first time setting Policies.");
            }
        }
        if (policy3 != null) {
            pol3 = policy3.getAsString().toLowerCase();
            pol3 = convertChoice(pol3);
            if (pol3 == null) {
                MessageHelper.sendMessageToEventChannel(event,
                    "Received an invalid input for Policy: The Economy, will either ignore or default to \"Empower\" (+) if this is your first time setting Policies.");
            }
        }

        if (!player.hasOlradinPolicies()) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have the Olradin Policy faction ability.");
            return;
        }

        // extra returns for the first time policies are set
        if (player.hasAbility("policies")) {
            player.removeAbility("policies");
            MessageHelper.sendMessageToEventChannel(event, "Initiating Policies for Olradin.");
            if (pol1 == null) {
                pol1 = "+";
                MessageHelper.sendMessageToEventChannel(event, "Need to initially set Policy: The People. Defaulting to \"Connect\" (+).");
            }
            if (pol2 == null) {
                pol2 = "+";
                MessageHelper.sendMessageToEventChannel(event,
                    "Need to initially set Policy: The Environment. Defaulting to \"Preserve\" (+).");
            }
            if (pol3 == null) {
                pol3 = "+";
                MessageHelper.sendMessageToEventChannel(event, "Need to initially set Policy: The Economy. Defaulting to \"Empower\" (+).");
            }
        }
        // MessageHelper.sendMessageToEventChannel(event, "debug finalset - pol1" + pol1 + " pol2 " + pol2 + " pol3 " +
        // pol3); (debug messagesender if more work is needed)

        int negativePolicies = 0;
        int positivePolicies = 0;

        // go through each option and set the policy accordingly
        if (pol1 != null) {
            if ("-".equals(pol1)) {
                if (player.hasAbility("policy_the_people_connect")) {
                    player.removeAbility("policy_the_people_connect");
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The People; removed \"Connect\" (+) and added \"Control\" (-).");
                } else {
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The People; added \"Control\" (-).");
                }
                player.addAbility("policy_the_people_control");
                negativePolicies++;
            } else if ("+".equals(pol1)) {
                if (player.hasAbility("policy_the_people_control")) {
                    player.removeAbility("policy_the_people_control");
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The People; removed \"Control\" (-) and added \"Connect\" (+).");
                } else {
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The People; added \"Connect\" (+).");
                }
                player.addAbility("policy_the_people_connect");
                positivePolicies++;
            }
        }
        if (pol2 != null) {
            if ("-".equals(pol2)) {
                if (player.hasAbility("policy_the_environment_preserve")) {
                    player.removeAbility("policy_the_environment_preserve");
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The Environment; removed \"Preserve\" (+) and added \"Plunder\" (-).");
                } else {
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The Environment; added \"Plunder\" (-).");
                }
                player.addAbility("policy_the_environment_plunder");
                negativePolicies++;
            } else if ("+".equals(pol2)) {
                if (player.hasAbility("policy_the_environment_plunder")) {
                    player.removeAbility("policy_the_environment_plunder");
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The Environment; removed \"Plunder\" (-) and added \"Preserve\" (+).");
                } else {
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The Environment; added \"Preserve\" (+).");
                }
                player.addAbility("policy_the_environment_preserve");
                positivePolicies++;
            }
        }
        if (pol3 != null) {
            if ("-".equals(pol3)) {
                String message = "";
                if (player.hasAbility("policy_the_economy_empower")) {
                    player.removeAbility("policy_the_economy_empower");
                    message = "Updated Policy: The Economy; removed \"Empower\" (+) and added \"Exploit\" (-).";
                } else {
                    message = "Updated Policy: The Economy; added \"Exploit\" (-).";
                }
                if (!player.hasAbility("policy_the_economy_exploit")) {
                    player.addAbility("policy_the_economy_exploit");
                    player.setCommoditiesTotal(player.getCommoditiesTotal() - 1);
                    MessageHelper.sendMessageToEventChannel(event, message
                        + " This has also decreased your commodity total by 1 (you should double check that this value is correct).");
                } else if (player.hasAbility("policy_the_economy_exploit")) {
                    player.addAbility("policy_the_economy_exploit");
                    MessageHelper.sendMessageToEventChannel(event, message
                        + " You already had this policy, so your commodity total is unchanged.");
                }
                negativePolicies++;
            } else if ("+".equals(pol3)) {
                if (player.hasAbility("policy_the_economy_exploit")) {
                    player.removeAbility("policy_the_economy_exploit");
                    player.setCommoditiesTotal(player.getCommoditiesTotal() + 1);
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The Economy; removed \"Exploit\" (-) and added \"Empower\" (+)." 
                        + " This has also increased commodity total by 1 (you should double check that this value is correct).");
                } else {
                    MessageHelper.sendMessageToEventChannel(event, "Updated Policy: The Economy; added \"Empower\" (+). "
                        + "This has also increased commodity total by 1 (you should double check that this value is correct).");
                }
                player.addAbility("policy_the_economy_empower");
                positivePolicies++;
            }
        }
        
        boolean hadPositiveMech = player.hasUnit("olradin_mech_positive");
        boolean hadNegativeMech = player.hasUnit("olradin_mech_negative");

        player.removeOwnedUnitByID("olradin_mech");
        player.removeOwnedUnitByID("olradin_mech_positive");
        player.removeOwnedUnitByID("olradin_mech_negative");
        String unitModelID;
        if (positivePolicies >= 2) {
            unitModelID = "olradin_mech_positive";
            if (hadNegativeMech)
            {
                MessageHelper.sendMessageToEventChannel(event, "Your Exemplar mechs have flipped to the positive (+) side.");
            }
        } else if (negativePolicies >= 2) {
            unitModelID = "olradin_mech_negative";
            if (hadPositiveMech)
            {
                MessageHelper.sendMessageToEventChannel(event, "Your Exemplar mechs have flipped to the negative (-) side.");
            }
        } else {
            unitModelID = "olradin_mech";
            MessageHelper.sendMessageToEventChannel(event, "Your Exemplar mechs have not flipped to either side; please fix this somehow.");
        }
        player.addOwnedUnitByID(unitModelID);
        UnitModel unitModel = Mapper.getUnit(unitModelID);

        DiscordantStarsHelper.checkOlradinMech(game);

        AbilityInfo.sendAbilityInfo(game, player, event);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "", List.of(unitModel.getRepresentationEmbed(false)));
    }

    public static String convertChoice(String inputChoice) {
        if (inputChoice == null) {
            return null;
        }
        if (("exploit".equals(inputChoice)) || ("plunder".equals(inputChoice)) || ("control".equals(inputChoice))
            || ("-".equals(inputChoice))) {
            return "-";
        }
        if (("empower".equals(inputChoice)) || ("preserve".equals(inputChoice)) || ("connect".equals(inputChoice))
            || ("+".equals(inputChoice))) {
            return "+";
        }
        return null;

    }
}
