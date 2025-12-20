package hoavinh.mocvien_coffee.controller;

import hoavinh.mocvien_coffee.dto.DashboardStats;
import hoavinh.mocvien_coffee.model.CafeSettings;
import hoavinh.mocvien_coffee.model.CafeTable;
import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.model.TableStatus;
import hoavinh.mocvien_coffee.service.CustomerService;
import hoavinh.mocvien_coffee.service.DashboardService;
import hoavinh.mocvien_coffee.service.ProductService;
import hoavinh.mocvien_coffee.service.SettingsService;
import hoavinh.mocvien_coffee.service.TableService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final DashboardService dashboardService;
    private final ProductService productService;
    private final TableService tableService;
    private final SettingsService settingsService;
    private final CustomerService customerService;

    public AdminController(DashboardService dashboardService,
                           ProductService productService,
                           TableService tableService,
                           SettingsService settingsService,
                           CustomerService customerService) {
        this.dashboardService = dashboardService;
        this.productService = productService;
        this.tableService = tableService;
        this.settingsService = settingsService;
        this.customerService = customerService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStats stats = dashboardService.buildTodayStats();
        model.addAttribute("stats", stats);
        return "admin/dashboard";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", productService.findAll());
        model.addAttribute("productForm", new Product());
        return "admin/products";
    }

    @PostMapping("/products")
    public String createProduct(@ModelAttribute("productForm") Product product,
                                RedirectAttributes redirectAttributes) {
        product.setAvailable(product.isAvailable());
        productService.save(product);
        redirectAttributes.addFlashAttribute("success", "Product saved");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable Long id,
                                @ModelAttribute Product product,
                                RedirectAttributes redirectAttributes) {
        var existing = productService.getById(id);
        existing.setName(product.getName());
        existing.setPrice(product.getPrice());
        existing.setCategory(product.getCategory());
        existing.setImageUrl(product.getImageUrl());
        existing.setAvailable(product.isAvailable());
        existing.setCost(product.getCost());
        productService.save(existing);
        redirectAttributes.addFlashAttribute("success", "Product updated");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Product deleted");
        return "redirect:/admin/products";
    }

    @GetMapping("/tables")
    public String tables(Model model) {
        model.addAttribute("tables", tableService.findAll());
        model.addAttribute("statuses", TableStatus.values());
        model.addAttribute("tableForm", new CafeTable());
        return "admin/tables";
    }

    @PostMapping("/tables")
    public String createTable(@ModelAttribute CafeTable form,
                              RedirectAttributes redirectAttributes) {
        form.setId(null);
        tableService.save(form);
        redirectAttributes.addFlashAttribute("success", "Table saved");
        return "redirect:/admin/tables";
    }

    @PostMapping("/tables/{id}")
    public String updateTable(@PathVariable Long id,
                              @ModelAttribute CafeTable form,
                              RedirectAttributes redirectAttributes) {
        CafeTable table = tableService.getById(id);
        table.setName(form.getName());
        table.setActive(form.isActive());
        table.setStatus(form.getStatus());
        tableService.save(table);
        redirectAttributes.addFlashAttribute("success", "Table updated");
        return "redirect:/admin/tables";
    }

    @PostMapping("/tables/{id}/delete")
    public String deleteTable(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        tableService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Table deleted");
        return "redirect:/admin/tables";
    }

    @PostMapping("/tables/{id}/reset")
    public String resetTable(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        tableService.release(id);
        redirectAttributes.addFlashAttribute("success", "Table reset to available");
        return "redirect:/admin/tables";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        CafeSettings settings = settingsService.getSettings();
        model.addAttribute("settings", settings);
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@ModelAttribute CafeSettings settings,
                                 RedirectAttributes redirectAttributes) {
        settingsService.save(settings);
        redirectAttributes.addFlashAttribute("success", "Settings updated");
        return "redirect:/admin/settings";
    }

    @GetMapping("/customer")
    public String customers(Model model) {
        model.addAttribute("customers", customerService.findAll());
        return "admin/customer";
    }
}

