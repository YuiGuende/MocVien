package hoavinh.mocvien_coffee.controller;

import hoavinh.mocvien_coffee.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
    }

    @PostMapping
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String fullName,
                             @RequestParam String role,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.createUser(username, password, fullName, role);
            redirectAttributes.addFlashAttribute("success", "User created");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("success", "User deleted");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String password,
                                RedirectAttributes redirectAttributes) {
        userService.resetPassword(id, password);
        redirectAttributes.addFlashAttribute("success", "Password reset");
        return "redirect:/admin/users";
    }
}

