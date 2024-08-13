package at.emielregis.dathostdemomanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationController {

    @Autowired
    private ConfigurableApplicationContext context;

    @GetMapping("api/kill")
    public String killApplication() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            context.close(); // Gracefully shut down the application
        }).start();
        return "Application is shutting down...";
    }
}
