package at.emielregis.dathostdemomanager.ftp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FtpConfigProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FtpConfigProcessor.class);

    private final List<FtpClient> ftpClients = new ArrayList<>();

    private final FtpProperties ftpProperties;
    private final FtpFileHandler ftpFileHandler;

    @Autowired
    public FtpConfigProcessor(FtpProperties ftpProperties, FtpFileHandler ftpFileHandler) {
        this.ftpProperties = ftpProperties;
        this.ftpFileHandler = ftpFileHandler;
        loadConfigs();
    }

    public void loadConfigs() {
        for (FtpProperties.Server server : ftpProperties.getServers()) {
            FtpClient ftpClient = new FtpClient(
                    server.getHost(),
                    server.getPort(),
                    server.getUsername(),
                    server.getPassword(),
                    server.getDemosFolder(),
                    ftpFileHandler
            );
            ftpClients.add(ftpClient);
            logger.info("FTP client configuration added: Host={} Port={} Username={}",
                    server.getHost(), server.getPort(), server.getUsername());
        }
    }

    public void processAllConfigs() {
        for (FtpClient ftpClient : ftpClients) {
            ftpClient.connectAndCopyDemos();
        }
    }
}
