package at.emielregis.dathostdemomanager;

import at.emielregis.dathostdemomanager.ftp.FtpConfigProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ProgramExecutor {

    @Autowired
    private FtpConfigProcessor ftpConfigProcessor;

    @Scheduled(fixedRateString = "${settings.run-interval}", timeUnit = TimeUnit.MINUTES)
    public void init() {
        ftpConfigProcessor.loadConfigs();
        ftpConfigProcessor.processAllConfigs();
    }
}
