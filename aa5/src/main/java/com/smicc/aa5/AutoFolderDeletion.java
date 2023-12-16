package com.smicc.aa5;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AutoFolderDeletion {

    private static String ROOT_PATH;
    private static String TEMP_PATH;
    private static int DELETION_FREQUENCY_DAYS;
    private static List<String> FOLDER_NAMES;
    private static final Logger logger = Logger.getLogger(AutoFolderDeletion.class.getName());

    public static void main(String[] args) {
        loadConfiguration();
        while (true) {
            try {
                autoMoveAndDeleteFiles();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error during file moving and deletion", e);
            }
            // Sleep for the specified frequency
            try {
                Thread.sleep(TimeUnit.DAYS.toMillis(DELETION_FREQUENCY_DAYS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                logger.log(Level.WARNING, "Thread interrupted", e);
            }
        }
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);

            ROOT_PATH = properties.getProperty("ROOT_PATH");
            TEMP_PATH = properties.getProperty("TEMP_PATH");
            DELETION_FREQUENCY_DAYS = Integer.parseInt(properties.getProperty("DELETION_FREQUENCY_DAYS"));

            // Read folder names
            FOLDER_NAMES = Arrays.asList(properties.getProperty("FOLDER_NAMES").split(","));
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Configuration file not found", e);
            System.exit(1);
        } catch (IOException | NumberFormatException e) {
            logger.log(Level.SEVERE, "Error reading configuration file or parsing integer", e);
            System.exit(1);
        }
    }

    private static void autoMoveAndDeleteFiles() throws IOException {
        // Iterate through folder names
        for (String folderName : FOLDER_NAMES) {
            Path folderPath = Paths.get(ROOT_PATH, folderName);

            // Check last modified date of the folder
            Date lastModifiedDate = new Date(Files.getLastModifiedTime(folderPath).toMillis());

            // Check if the folder itself is older than the threshold
            if (isOlderThanThreshold(lastModifiedDate)) {
                // Delete existing folder from the temporary folder
                deleteFolderFromTemp(folderPath.toFile());

                // Move the folder to the temporary folder
                moveFolderToTemp(folderPath, Paths.get(TEMP_PATH));
            } else {
                // Iterate through files in the folder
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folderPath)) {
                    for (Path fileOrFolderPath : directoryStream) {
                        // Check if it is a directory
                        if (Files.isDirectory(fileOrFolderPath)) {
                            // Handle subfolder
                            handleSubfolder(fileOrFolderPath);
                        } else {
                            // Handle files
                            handleFile(fileOrFolderPath);
                        }
                    }
                }
            }
        }
    }

    private static void handleSubfolder(Path subfolderPath) throws IOException {
        // Check last modified date of the subfolder
        Date lastModifiedDate = new Date(Files.getLastModifiedTime(subfolderPath).toMillis());

        // Check if the subfolder itself is older than the threshold
        if (isOlderThanThreshold(lastModifiedDate)) {
            // Delete existing subfolder from the temporary folder
            deleteFolderFromTemp(subfolderPath.toFile());

            // Move the subfolder to the temporary folder
            moveFolderToTemp(subfolderPath, Paths.get(TEMP_PATH));
        } else {
            // Iterate through files in the subfolder
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(subfolderPath)) {
                for (Path fileOrFolderPath : directoryStream) {
                    // Check if it is a directory
                    if (Files.isDirectory(fileOrFolderPath)) {
                        // Handle nested subfolders recursively
                        handleSubfolder(fileOrFolderPath);
                    } else {
                        // Handle files
                        handleFile(fileOrFolderPath);
                    }
                }
            }
        }
    }

    private static void handleFile(Path filePath) throws IOException {
        // Check last modified date
        Date lastModifiedDate = new Date(Files.getLastModifiedTime(filePath).toMillis());
        if (isOlderThanThreshold(lastModifiedDate)) {
            // Delete existing file from the temporary folder
            deleteFileFromTemp(filePath.toFile());

            // Move the file to the temporary folder
            moveFileToTemp(filePath, Paths.get(TEMP_PATH));
        }
    }
    private static void moveFileToTemp(Path source, Path destination) {
        // Move the file to the temporary folder
        try {
            Files.move(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            logger.info("File moved to temp folder: " + source.toString());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error moving file to temp folder", e);
        }
    }

    private static void moveFolderToTemp(Path source, Path destination) {
        // Move the folder to the temporary folder
        try {
            Files.move(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Folder moved to temp folder: " + source.toString());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error moving folder to temp folder", e);
        }
    }

    private static void deleteFolderFromTemp(File folder) {
        // Delete the folder from the temporary folder
        Path tempFolderPath = Paths.get(TEMP_PATH, folder.getName());
        try {
            Files.walk(tempFolderPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            logger.info("Folder deleted from temp folder: " + tempFolderPath.toString());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error deleting folder from temp folder", e);
        }
    }

    private static void deleteFileFromTemp(File file) {
        // Delete the file from the temporary folder
        Path tempFilePath = Paths.get(TEMP_PATH, file.getName());
        try {
            Files.deleteIfExists(tempFilePath);
            logger.info("File deleted from temp folder: " + tempFilePath.toString());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error deleting file from temp folder", e);
        }
    }

    private static boolean isOlderThanThreshold(Date lastModifiedDate) {
        // Calculate the deletion threshold
        Date deletionThreshold = calculateDeletionThreshold();

        // Compare last modified date with the threshold
        return lastModifiedDate.before(deletionThreshold);
    }

    private static Date calculateDeletionThreshold() {
        // Get the current date
        Calendar calendar = Calendar.getInstance();

        // Subtract the deletion frequency days
        calendar.add(Calendar.DAY_OF_MONTH, -DELETION_FREQUENCY_DAYS);

        // Return the calculated date
        return calendar.getTime();
    }
}
