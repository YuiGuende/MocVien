package hoavinh.mocvien_coffee.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AiChatController {

    @GetMapping("/ai-chat")
    public String aiChat() {
        return "ai-chat";
    }
}

