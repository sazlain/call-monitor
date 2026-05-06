package com.monitor.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DefaultController {


    private static Logger logger = LoggerFactory.getLogger(DefaultController.class);

    @GetMapping("/hello")
    public String hello() {
        logger.info("Received request for /api/hello");
        return "Hello, World!";
    }
}