package hoavinh.mocvien_coffee.controller;

import hoavinh.mocvien_coffee.dto.ChartDataPoint;
import hoavinh.mocvien_coffee.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardRestController {

    private final DashboardService dashboardService;

    public AdminDashboardRestController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/revenue")
    public List<ChartDataPoint> revenue(@RequestParam(defaultValue = "today") String range) {
        return dashboardService.revenueSeries(range);
    }

    @GetMapping("/product-mix")
    public List<ChartDataPoint> productMix() {
        return dashboardService.productMix();
    }
}

