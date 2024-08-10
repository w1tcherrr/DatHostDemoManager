package at.emielregis.dathostdemomanager.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FtpClient {
    private static final Logger logger = LoggerFactory.getLogger(FtpClient.class);

    private String host;
    private int port;
    private String username;
    private String password;
    private String demosFolder;

    private final FtpFileHandler ftpFileHandler;

    @Autowired
    public FtpClient(FtpFileHandler ftpFileHandler) {
        this.ftpFileHandler = ftpFileHandler;
    }

    public FtpClient(String host, int port, String username, String password, String demosFolder, FtpFileHandler ftpFileHandler) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.demosFolder = demosFolder;
        this.ftpFileHandler = ftpFileHandler;
    }

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

    public FtpFileHandler getFtpFileHandler() {
        return ftpFileHandler;
    }

    public void connectAndCopyDemos() {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, port);
            logger.info("Connected to FTP server {} on port {}", host, port);

            ftpClient.login(username, password);
            logger.info("Logged in to FTP server {} as user {}", host, username);

            if (ftpClient.changeWorkingDirectory(demosFolder)) {
                String[] files = ftpClient.listNames();
                if (files != null && files.length > 0) {
                    logger.info("Files found in the 'demos' directory of FTP server {}: {}", host, String.join(", ", files));
                    for (String file : files) {
                        logger.info("Copying file: {}", file);
                        ftpFileHandler.copyFileFromFtp(ftpClient, file);
                    }
                } else {
                    logger.info("No files found in the 'demos' directory of FTP server {}", host);
                }
            } else {
                logger.warn("Failed to change directory to 'demos' on FTP server {}", host);
            }

            ftpClient.logout();
            logger.info("Logged out from FTP server {}", host);
        } catch (IOException e) {
            logger.error("Error occurred during FTP operation", e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                    logger.info("Disconnected from FTP server {}", host);
                }
            } catch (IOException ex) {
                logger.error("Error occurred while disconnecting from FTP server", ex);
            }
        }
    }
}
