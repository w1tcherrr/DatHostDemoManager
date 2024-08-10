package at.emielregis.dathostdemomanager.ftp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ftp")
public class FtpProperties {

    private Config config;
    private Local local;
    private List<Server> servers;

    // Getters and Setters
    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public static class Config {
        private String path;

        // Getters and Setters
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Local {
        private String directory;
        private String archiveDirectory;
        private String latestDemosDirectory;
        private int maxArchiveDemos;
        private int maxLatestDemos;

        // Getters and Setters
        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getArchiveDirectory() {
            return archiveDirectory;
        }

        public void setArchiveDirectory(String archiveDirectory) {
            this.archiveDirectory = archiveDirectory;
        }

        public String getLatestDemosDirectory() {
            return latestDemosDirectory;
        }

        public void setLatestDemosDirectory(String latestDemosDirectory) {
            this.latestDemosDirectory = latestDemosDirectory;
        }

        public int getMaxArchiveDemos() {
            return maxArchiveDemos;
        }

        public void setMaxArchiveDemos(int maxArchiveDemos) {
            this.maxArchiveDemos = maxArchiveDemos;
        }

        public int getMaxLatestDemos() {
            return maxLatestDemos;
        }

        public void setMaxLatestDemos(int maxLatestDemos) {
            this.maxLatestDemos = maxLatestDemos;
        }
    }

    public static class Server {
        private String host;
        private int port;
        private String username;
        private String password;
        private String demosFolder;

        // Getters and Setters
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDemosFolder() {
            return demosFolder;
        }

        public void setDemosFolder(String demosFolder) {
            this.demosFolder = demosFolder;
        }
    }
}
