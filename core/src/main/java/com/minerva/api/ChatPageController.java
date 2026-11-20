package com.minerva.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatPageController {
    @GetMapping("/")
    public String page() {
        return "index";
    }

    /**
     * Displays the Library page for managing RSS feeds.
     *
     * @return the Library page template
     */
    @GetMapping("/library")
    public String libraryPage() {
        return "library";
    }
}
