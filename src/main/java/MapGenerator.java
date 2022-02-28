import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

public class MapGenerator {

    public static void main(String[] args)
            throws LoginException {
        JDA jda = JDABuilder.createDefault("OTQ3NzYzMTQwNTE3NTYwMzMx.Yhx_NQ.E-Vb0yG51dpPtQH6rYevymiqgi4").build();
        //You can also add event listeners to the already built JDA instance
        // Note that some events may not be received if the listener is added after calling build()
        // This includes events such as the ReadyEvent
//        jda.addEventListener(new MessageListener());

        JDABuilder.createLight(args[0], GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
                .addEventListeners(new MessageListener())
//                .setActivity(Activity.playing("Type !ping"))
                .build();
    }
}
