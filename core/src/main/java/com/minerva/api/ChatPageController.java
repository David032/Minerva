package com.minerva.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatPageController {
    @GetMapping("/")
    public String page() {
        return "index";
    }
}
