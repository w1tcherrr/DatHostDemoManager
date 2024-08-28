package at.emielregis.dathostdemomanager;

import at.emielregis.dathostdemomanager.ftp.FtpConfigProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ProgramExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ProgramExecutor.class);

    @Autowired
    private FtpConfigProcessor ftpConfigProcessor;

    @Value("${settings.demos.delete-demos}")
    private boolean deleteDemos;

    @Value("${settings.maps.delete-maps}")
    private boolean deleteMaps;

    @Scheduled(fixedRateString = "${settings.demos.run-interval-demo-fetching}", timeUnit = TimeUnit.MINUTES)
    public void processDemoFiles() {
        if (!deleteDemos) {
            logger.info("Skipping demo deletion.");
            return;
        }
        ftpConfigProcessor.loadConfigs();
        ftpConfigProcessor.downloadDemos();
    }

    @Scheduled(fixedRateString = "${settings.maps.run-interval-map-deletion}", timeUnit = TimeUnit.MINUTES)
    public void deleteMapFiles() {
        if (!deleteMaps) {
            logger.info("Skipping map deletion.");
            return;
        }
        ftpConfigProcessor.loadConfigs();
        ftpConfigProcessor.deleteMaps();
    }
}
