package at.emielregis.dathostdemomanager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class LogViewerController {

    @GetMapping("/logs")
    public String getLogs() {
        String logFile = "logs/application.log"; // specify your log file path
        try (Stream<String> stream = Files.lines(Paths.get(logFile))) {
            return stream.collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Could not read log file: " + e.getMessage();
        }
    }
}
