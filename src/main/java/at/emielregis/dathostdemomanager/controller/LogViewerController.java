package at.emielregis.dathostdemomanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class LogViewerController {

    @GetMapping("/api/logs")
    public String getLogs() {
        String logFile = "logs/application.log"; // Path to your log file
        try (Stream<String> stream = Files.lines(Paths.get(logFile))) {
            return stream.collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Could not read log file: " + e.getMessage();
        }
    }
}
