package hoavinh.mocvien_coffee.controller;

import hoavinh.mocvien_coffee.service.ProductService;
import hoavinh.mocvien_coffee.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/pos")
public class PosController {

    private final ProductService productService;
    private final SettingsService settingsService;

    public PosController(ProductService productService, SettingsService settingsService) {
        this.productService = productService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String pos(Model model) {
        model.addAttribute("categories", new String[]{"All", "Coffee", "Tea", "Smoothie"});
        model.addAttribute("products", productService.getAvailableProducts("all"));
        model.addAttribute("settings", settingsService.getSettings());
        return "pos";
    }

    @GetMapping("/huuTinh")
    public String huuTinh(Model model) {
        model.addAttribute("ten","do huu tinh");
        return "hello";

    }
}

