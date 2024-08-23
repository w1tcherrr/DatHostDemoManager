package at.emielregis.dathostdemomanager.ftp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ftp")
@Data
public class FtpProperties {

    private Config config;
    private Local local;
    private List<Server> servers;

    @Data
    public static class Config {
        private String path;
    }

    @Data
    public static class Local {
        // Getters and Setters
        private String directory;
        private String archiveDirectory;
        private String latestDemosDirectory;
        private int maxArchiveDemos;
        private int maxLatestDemos;

    }

    @Data
    public static class Server {
        private String host;
        private int port;
        private String username;
        private String password;
        private String serverId;
        private String demosFolder;
        private String mapsFolder;
    }
}
