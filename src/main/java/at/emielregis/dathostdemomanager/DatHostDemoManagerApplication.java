package at.emielregis.dathostdemomanager;

import at.emielregis.dathostdemomanager.ftp.FtpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FtpProperties.class)
public class DatHostDemoManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatHostDemoManagerApplication.class, args);
    }
}
