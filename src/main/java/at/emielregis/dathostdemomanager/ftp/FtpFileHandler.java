package at.emielregis.dathostdemomanager.ftp;

import at.emielregis.dathostdemomanager.dathost.DatHostServerAccessor;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class FtpFileHandler {

    private static final Logger logger = LoggerFactory.getLogger(FtpFileHandler.class);

    @Value("${ftp.local.directory}")
    private String localDirectory;

    @Value("${ftp.local.archive-directory}")
    private String archiveDirectory;

    @Value("${ftp.local.latest-demos-directory}")
    private String latestDemosDirectory;

    @Value("${ftp.local.max-archive-demos}")
    private int maxArchiveDemos;

    @Value("${ftp.local.max-latest-demos}")
    private int maxLatestDemos;

    @Value("${settings.demos.minutes-after-demo}")
    private int neededMinutesPassed;

    @Value("${settings.demos.allowed-file-ending}")
    private String allowedFileEnding;

    @Value("${settings.maps.min-megabytes-maps}")
    private int minMegabytesMaps;

    public void connectAndCopyDemos(FtpClientData ftpClientData) {
        connectAndExecuteFtpOperationForFile(
                ftpClientData,
                ftpClientData.getDemosFolder(),
                "Copying"
        );
    }

    public void connectAndDeleteMaps(FtpClientData ftpClientData, DatHostServerAccessor serverAccessor) {
        long totalSizeInMegabytes = getTotalMapFilesSize(ftpClientData);
        if (totalSizeInMegabytes < minMegabytesMaps) {
            logger.warn("Total map files size is less than {} MB. Aborting deletion.", minMegabytesMaps);
            return;
        }

        String serverId = ftpClientData.getServerId();
        boolean isServerRunning = serverAccessor.isServerRunning(serverId);

        if (!isServerRunning) {
            logger.warn("Server is not running, proceeding with map deletion.");
            performFtpMapDeletion(ftpClientData);
            return;
        }

        int amountOfPlayersOnServer = serverAccessor.getAmountOfPlayersOnServer(serverId);

        if (amountOfPlayersOnServer != 0) {
            logger.warn("Server is running and not empty. Aborting map deletion.");
            return; // Do not delete maps unless server is empty!
        }

        boolean serverShutdownSuccess = serverAccessor.shutdownServer(serverId);
        if (!serverShutdownSuccess) {
            logger.warn("Failed to shutdown the server. Aborting map deletion.");
            return;
        }

        performFtpMapDeletion(ftpClientData);

        boolean serverStartSuccess = serverAccessor.startServer(serverId);
        if (!serverStartSuccess) {
            logger.warn("Failed to restart the server after map deletion.");
        }
    }

    private long getTotalMapFilesSize(FtpClientData ftpClientData) {
        try {
            ftpClientData.connect();
            ftpClientData.login();

            String content730Folder = ftpClientData.getMapsFolder() + "/content/730";
            long totalSizeInBytes = calculateTotalFileSize(ftpClientData.getFtpClient(), content730Folder);

            ftpClientData.logout();
            ftpClientData.disconnect();

            return totalSizeInBytes / (1024 * 1024); // Convert to MB
        } catch (IOException e) {
            logger.error("Error occurred while calculating total map file size.", e);
            return 0;
        }
    }

    private void performFtpMapDeletion(FtpClientData ftpClientData) {
        try {
            ftpClientData.connect();
            ftpClientData.login();

            deleteMapFiles(ftpClientData.getFtpClient(), ftpClientData.getMapsFolder());

            ftpClientData.logout();
            ftpClientData.disconnect();
        } catch (Exception e) {
            logger.error("Error during FTP map deletion operation.", e);
        }
    }

    private void connectAndExecuteFtpOperationForFile(FtpClientData ftpClientData, String targetFolder, String actionDescription) {

        try {
            ftpClientData.connect();
            logger.info("Connected to FTP server {} on port {}", ftpClientData.getHost(), ftpClientData.getPort());

            ftpClientData.login();
            logger.info("Logged in to FTP server {} as user {}", ftpClientData.getHost(), ftpClientData.getUsername());

            if (ftpClientData.changeWorkingDirectory(targetFolder)) {
                logger.info("Changed target directory to {}", targetFolder);
                String[] files = ftpClientData.listNames();
                if (files != null && files.length > 0) {
                    logger.info("Files found in the '{}' directory of FTP server {}: {}", targetFolder, ftpClientData.getHost(), String.join(", ", files));
                    for (String file : files) {
                        logger.info("{} file: {}", actionDescription, file);
                        copyFileFromFtp(ftpClientData.getFtpClient(), file);
                    }
                } else {
                    logger.info("No files found in the '{}' directory of FTP server {}", targetFolder, ftpClientData.getHost());
                }
            } else {
                logger.warn("Failed to change directory to '{}' on FTP server {}", targetFolder, ftpClientData.getServerId());
            }

            ftpClientData.logout();
            logger.info("Logged out from FTP server {}", ftpClientData.getHost());
        } catch (IOException e) {
            logger.error("Error occurred during FTP operation", e);
        } finally {
            try {
                if (ftpClientData.isConnected()) {
                    ftpClientData.disconnect();
                    logger.info("Disconnected from FTP server {}", ftpClientData.getHost());
                }
            } catch (IOException ex) {
                logger.error("Error occurred while disconnecting from FTP server", ex);
            }
        }
    }

    public void copyFileFromFtp(FTPClient ftpClient, String remoteFileName) throws IOException {
        cleanUpTempFiles(localDirectory);

        if (!remoteFileName.endsWith(allowedFileEnding)) {
            logger.info("Skipping file: {}, as it does not end with: {}", remoteFileName, allowedFileEnding);
            return;
        }

        String localFilePath = localDirectory + "/" + remoteFileName;
        File localFile = new File(localFilePath);

        long epochSecond = extractDateFromFilename(remoteFileName);
        LocalDateTime fileTime = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
        LocalDateTime currentTime = LocalDateTime.now();
        long minutes = Math.abs(ChronoUnit.MINUTES.between(fileTime, currentTime));

        if (minutes <= neededMinutesPassed) {
            logger.info("Skipping demo for now: {}, minutes passed since creation: {}", remoteFileName, minutes);
            return;
        }

        if (localFile.exists()) {
            logger.info("File already exists: {}", remoteFileName);
            return;
        }

        String tempFilePath = localFilePath + ".tmp";
        File tempFile = new File(tempFilePath);

        boolean success = false;
        try (OutputStream outputStream = new FileOutputStream(tempFilePath)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            if (ftpClient.retrieveFile(remoteFileName, outputStream)) {
                logger.info("Successfully downloaded file: {} to temporary file {}", remoteFileName, tempFilePath);
                success = true;
            } else {
                logger.warn("Failed to download file: {}", remoteFileName);
            }
        } catch (IOException e) {
            logger.error("Error occurred while downloading file: {}", remoteFileName, e);
        }

        if (success) {
            if (tempFile.renameTo(localFile)) {
                logger.info("Successfully moved temporary file to final destination: {}", localFilePath);
                handleArchiveAndLatestDemos(remoteFileName);

                boolean deleted = ftpClient.deleteFile(remoteFileName);
                if (deleted) {
                    logger.info("Successfully deleted file: {} from FTP server.", remoteFileName);
                } else {
                    logger.warn("Failed to delete file: {} from FTP server.", remoteFileName);
                }
            } else {
                logger.error("Failed to rename temporary file to final destination: {}", localFilePath);
            }
        } else {
            if (tempFile.exists() && !tempFile.delete()) {
                logger.warn("Failed to delete temporary file: {}", tempFilePath);
            }
        }
    }

    public void deleteMapFiles(FTPClient ftpClient, String targetFolder) {
        String fileToDelete = targetFolder + "/appworkshop_730.acf";

        try {
            FTPFile[] filesAndDirs = ftpClient.listFiles(targetFolder + "/content/730");

            for (FTPFile fileOrDir : filesAndDirs) {
                if (fileOrDir.isDirectory()) {
                    String dirPath = targetFolder + "/content/730/" + fileOrDir.getName();
                    boolean deleted = deleteDirectoryRecursively(ftpClient, dirPath);
                    if (deleted) {
                        logger.info("Successfully deleted directory: {}", dirPath);
                    } else {
                        logger.warn("Failed to delete directory: {}", dirPath);
                    }
                }
            }

            if (ftpClient.listFiles(fileToDelete).length > 0) {
                boolean deleted = ftpClient.deleteFile(fileToDelete);
                if (deleted) {
                    logger.info("Successfully deleted file: {}", fileToDelete);
                } else {
                    logger.warn("Failed to delete file: {}", fileToDelete);
                }
            } else {
                logger.info("File not found: {}", fileToDelete);
            }

        } catch (IOException e) {
            logger.error("Error occurred while trying to delete map files", e);
        }
    }

    private long calculateTotalFileSize(FTPClient ftpClient, String directoryPath) throws IOException {
        long totalSize = 0;

        // List files and directories at the given path
        FTPFile[] files = ftpClient.listFiles(directoryPath);

        for (FTPFile file : files) {
            // If it's a file, add its size
            if (file.isFile()) {
                totalSize += file.getSize();
            }
            // If it's a directory, recursively calculate the size
            else if (file.isDirectory()) {
                String subDirectoryPath = directoryPath + "/" + file.getName();
                totalSize += calculateTotalFileSize(ftpClient, subDirectoryPath);
            }
        }

        return totalSize;
    }

    private boolean deleteDirectoryRecursively(FTPClient ftpClient, String dirPath) throws IOException {
        FTPFile[] subFiles = ftpClient.listFiles(dirPath);

        for (FTPFile subFile : subFiles) {
            String filePath = dirPath + "/" + subFile.getName();
            if (subFile.isDirectory()) {
                if (!deleteDirectoryRecursively(ftpClient, filePath)) {
                    return false;
                }
            } else {
                if (!ftpClient.deleteFile(filePath)) {
                    logger.warn("Failed to delete file: {}", filePath);
                    return false;
                }
            }
        }

        return ftpClient.removeDirectory(dirPath);
    }

    private void cleanUpTempFiles(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] tempFiles = directory.listFiles((dir, name) -> name.endsWith(".tmp"));
            if (tempFiles != null) {
                for (File tempFile : tempFiles) {
                    if (tempFile.delete()) {
                        logger.info("Deleted temporary file: {}", tempFile.getName());
                    } else {
                        logger.warn("Failed to delete temporary file: {}", tempFile.getName());
                    }
                }
            }
        } else {
            logger.warn("Directory {} does not exist or is not a directory.", directoryPath);
        }
    }

    private void handleArchiveAndLatestDemos(String newDemoFileName) throws IOException {
        File latestDemosDir = new File(latestDemosDirectory);

        if (Optional.ofNullable(latestDemosDir.list()).orElse(new String[0]).length >= maxLatestDemos) {
            deleteOldestFile(latestDemosDir);
        }

        Path sourcePath = Paths.get(localDirectory + "/" + newDemoFileName);
        Path destinationPath = Paths.get(latestDemosDirectory + "/" + newDemoFileName);

        if (Files.exists(destinationPath)) {
            logger.info("File {} already exists in the latest demos directory. Skipping copy.", newDemoFileName);
        } else {
            Files.copy(sourcePath, destinationPath);
            logger.info("Copied {} to latest demos directory.", newDemoFileName);
        }

        File localDir = new File(localDirectory);

        if (Optional.ofNullable(localDir.list()).orElse(new String[0]).length >= maxArchiveDemos) {
            zipAndClearLocalDirectory(localDir);
        }
    }

    private void deleteOldestFile(File directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory.toPath())) {
            paths.map(Path::toFile)
                    .sorted(Comparator.comparing(this::extractDateFromFilename))
                    .limit(1)
                    .forEach(File::delete);
        }
        logger.info("Deleted the oldest file in latest demos directory.");
    }

    private void zipAndClearLocalDirectory(File localDir) {
        File[] files = Optional.ofNullable(localDir.listFiles()).orElse(new File[0]);
        if (files.length == 0) {
            logger.info("No files to archive in the local directory.");
            return;
        }

        String newFileName = getNextArchiveFileName(files.length);
        File tempZipFile = new File(archiveDirectory + "/" + newFileName + ".tmp");
        File finalZipFile = new File(archiveDirectory + "/" + newFileName);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZipFile))) {
            for (File file : files) {
                logger.info("Adding file {} to zip file.", file.getName());
                zos.putNextEntry(new ZipEntry(file.getName()));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            logger.error("Error occurred while creating zip file: {}", finalZipFile.getName(), e);
        }
        try {
            if (tempZipFile.renameTo(finalZipFile)) {
                logger.info("Successfully created archive: {}", finalZipFile.getName());

                for (File file : files) {
                    if (file.delete()) {
                        logger.info("Deleted original file: {}", file.getName());
                    } else {
                        logger.warn("Failed to delete original file: {}", file.getName());
                    }
                }
            } else {
                logger.error("Failed to rename temporary zip file to final destination: {}", finalZipFile.getName());
            }
        } finally {
            if (tempZipFile.exists() && !finalZipFile.exists()) {
                if (tempZipFile.delete()) {
                    logger.info("Deleted temporary zip file: {}", tempZipFile.getName());
                } else {
                    logger.warn("Failed to delete temporary zip file: {}", tempZipFile.getName());
                }
            }
        }

        logger.info("Zipped and cleared local directory.");
    }

    private String getNextArchiveFileName(int fileCount) {
        File archiveDir = new File(archiveDirectory);
        File[] archiveFiles = Optional.ofNullable(archiveDir.listFiles((dir, name) -> name.matches("\\d{1,6}-\\d{1,6}\\.zip"))).orElse(new File[0]);

        int left = 1;
        int right = fileCount;

        if (archiveFiles.length > 0) {
            int maxRight = 0;

            for (File file : archiveFiles) {
                String name = file.getName().replace(".zip", "");
                String[] parts = name.split("-");
                int currentRight = Integer.parseInt(parts[1]);

                if (currentRight > maxRight) {
                    maxRight = currentRight;
                }
            }

            left = maxRight + 1;
            right = left + fileCount - 1;
        }

        logger.info("Generated next archive file name: {}-{}.zip", left, right);
        return left + "-" + right + ".zip";
    }

    private long extractDateFromFilename(File file) {
        String filename = file.getName();
        String[] split = filename.split("_");
        if (split.length < 2) {
            logger.warn("Failed to parse date from filename: {}. Falling back to last modified time.", filename);
            return file.lastModified();
        }
        String datePart = split[0] + "_" + split[1];
        try {
            return LocalDateTime.parse(datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")).toEpochSecond(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse date from filename: {}. Falling back to last modified time.", filename, e);
            return file.lastModified(); // fallback to last modified time if the file does not have a correct name
        }
    }

    private long extractDateFromFilename(String filename) {
        String[] split = filename.split("_");
        if (split.length < 2) {
            logger.warn("Failed to parse date from filename: {}. Falling back to last modified time.", filename);
            return LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        }
        String datePart = split[0] + "_" + split[1];
        try {
            return LocalDateTime.parse(datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")).toEpochSecond(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse date from filename: {}. Falling back to last modified time.", filename, e);
            return LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        }
    }
}
