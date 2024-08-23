package at.emielregis.dathostdemomanager.ftp;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Data
@RequiredArgsConstructor
public class FtpClientData {

    private static final Logger logger = LoggerFactory.getLogger(FtpClientData.class);

    private String host;
    private int port;
    private String username;
    private String password;
    private String serverId;
    private String demosFolder;
    private String mapsFolder;

    private final FTPClient ftpClient = new FTPClient();

    public void connect() throws IOException {
        ftpClient.connect(host, port);
    }

    public void login() throws IOException {
        ftpClient.login(username, password);
    }

    public boolean changeWorkingDirectory(String targetFolder) throws IOException {
        return ftpClient.changeWorkingDirectory(targetFolder);
    }

    public String[] listNames() throws IOException {
        return ftpClient.listNames();
    }

    public void logout() throws IOException {
        ftpClient.logout();
    }

    public boolean isConnected() {
        return ftpClient.isConnected();
    }

    public void disconnect() throws IOException {
        ftpClient.disconnect();
    }
}
