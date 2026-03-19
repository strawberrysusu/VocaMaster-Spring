package com.vocamaster.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/pages")
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/decks")
    public String decks() {
        return "decks";
    }

    @GetMapping("/decks/{id}")
    public String deckDetail(@PathVariable Long id) {
        return "deck-detail";
    }

    @GetMapping("/decks/{id}/quiz")
    public String quiz(@PathVariable Long id) {
        return "quiz";
    }
}
