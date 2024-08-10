package at.emielregis.dathostdemomanager.ftp;

import org.apache.commons.net.ftp.FTPClient;
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

    @Value("${settings.delete-from-ftp}")
    private boolean deleteFromFtp;

    public void copyFileFromFtp(FTPClient ftpClient, String remoteFileName) throws IOException {
        cleanUpTempFiles(localDirectory);

        String localFilePath = localDirectory + "/" + remoteFileName;
        File localFile = new File(localFilePath);

        if (localFile.exists()) {
            logger.info("File already exists: {}", remoteFileName);
            return;
        }

        String tempFilePath = localFilePath + ".tmp";
        File tempFile = new File(tempFilePath);

        boolean success = false;
        try (OutputStream outputStream = new FileOutputStream(tempFilePath)) {
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

                if (deleteFromFtp) {
                    boolean deleted = ftpClient.deleteFile(remoteFileName);
                    if (deleted) {
                        logger.info("Successfully deleted file: {} from FTP server.", remoteFileName);
                    } else {
                        logger.warn("Failed to delete file: {} from FTP server.", remoteFileName);
                    }
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
                zos.putNextEntry(new ZipEntry(file.getName()));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
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
        } catch (IOException e) {
            logger.error("Error occurred while creating zip file: {}", finalZipFile.getName(), e);
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
        String datePart = filename.split("_")[0];
        try {
            return LocalDateTime.parse(datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")).toEpochSecond(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse date from filename: {}. Falling back to last modified time.", filename, e);
            return file.lastModified(); // fallback to last modified time if the file does not have a correct name
        }
    }
}
