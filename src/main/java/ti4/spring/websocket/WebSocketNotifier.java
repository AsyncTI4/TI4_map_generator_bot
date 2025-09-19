package ti4.spring.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class WebSocketNotifier {
    private final SimpMessagingTemplate messagingTemplate;

    public void notifyGameRefresh(String gameId) {
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, "refresh");
    }
}
