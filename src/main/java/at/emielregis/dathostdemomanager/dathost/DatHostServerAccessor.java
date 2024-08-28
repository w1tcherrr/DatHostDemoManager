package at.emielregis.dathostdemomanager.dathost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Component
public class DatHostServerAccessor {

    @Value("${settings.dathost-credentials.username}")
    private String username;

    @Value("${settings.dathost-credentials.password}")
    private String password;

    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        return headers;
    }

    public boolean isServerRunning(String serverId) {
        String url = "https://dathost.net/api/0.1/game-servers/" + serverId;
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> body = response.getBody();
            return body != null && (boolean) body.getOrDefault("on", false);
        }

        return false;
    }

    public int getAmountOfPlayersOnServer(String serverId) {
        String url = "https://dathost.net/api/0.1/game-servers/" + serverId;
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> body = response.getBody();
            if (body != null) {
                return (int) body.getOrDefault("players_online", -1);
            }
        }

        return -1;
    }

    public boolean shutdownServer(String serverId) {
        String url = "https://dathost.net/api/0.1/game-servers/" + serverId + "/stop";
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return response.getStatusCode() == HttpStatus.OK;
    }

    public boolean startServer(String serverId) {
        String url = "https://dathost.net/api/0.1/game-servers/" + serverId + "/start";
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

        return response.getStatusCode() == HttpStatus.OK;
    }
}
