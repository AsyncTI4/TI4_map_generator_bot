import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static void main(String[] args)
            throws LoginException {

        JDA jda = JDABuilder.createDefault(args[0]).build();
        //You can also add event listeners to the already built JDA instance
        // Note that some events may not be received if the listener is added after calling build()
        // This includes events such as the ReadyEvent

//        JDABuilder.createLight(args[0], GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
//                .addEventListeners(new MessageListener())
////                .setActivity(Activity.playing("Type !ping"))
//                .build();
        jda.addEventListener(new MessageListener());
    }
}
