package at.emielregis.dathostdemomanager.ftp;

import at.emielregis.dathostdemomanager.dathost.DatHostServerAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FtpConfigProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FtpConfigProcessor.class);

    boolean initialized = false;
    private final List<FtpClientData> ftpClientDataList = new ArrayList<>();

    private final FtpProperties ftpProperties;
    private final FtpFileHandler ftpFileHandler;
    private final DatHostServerAccessor datHostServerAccessor;

    @Autowired
    public FtpConfigProcessor(FtpProperties ftpProperties, FtpFileHandler ftpFileHandler, DatHostServerAccessor datHostServerAccessor) {
        this.ftpProperties = ftpProperties;
        this.ftpFileHandler = ftpFileHandler;
        this.datHostServerAccessor = datHostServerAccessor;
        loadConfigs();
    }

    public void loadConfigs() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (FtpProperties.Server server : ftpProperties.getServers()) {
            FtpClientData ftpClientData = new FtpClientData();
            ftpClientData.setHost(server.getHost());
            ftpClientData.setPort(server.getPort());
            ftpClientData.setUsername(server.getUsername());
            ftpClientData.setPassword(server.getPassword());
            ftpClientData.setServerId(server.getServerId());
            ftpClientData.setDemosFolder(server.getDemosFolder());
            ftpClientData.setMapsFolder(server.getMapsFolder());
            this.ftpClientDataList.add(ftpClientData);
            logger.info("FTP client configuration added: Host={} Port={} Username={}",
                    server.getHost(), server.getPort(), server.getUsername());
        }
    }

    public void downloadDemos() {
        for (FtpClientData ftpClientData : this.ftpClientDataList) {
            ftpFileHandler.connectAndCopyDemos(ftpClientData);
        }
    }

    public void deleteMaps() {
        for (FtpClientData ftpClientData : this.ftpClientDataList) {
            ftpFileHandler.connectAndDeleteMaps(ftpClientData, datHostServerAccessor);
        }
    }
}
