package hoavinh.mocvien_coffee.ai_engine.service;

import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuContextService {

    private final ProductRepository productRepository;

    public MenuContextService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Load tất cả sản phẩm available từ database
     */
    public List<Product> loadMenuFromDatabase() {
        return productRepository.findAllAvailable();
    }

    /**
     * Format menu thành text context cho AI
     */
    public String formatMenuForAiContext(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return "Menu hiện tại đang trống.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== MENU QUÁN MỘC MIÊN ===\n\n");

        // Group by category
        var byCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        byCategory.forEach((category, items) -> {
            sb.append("📋 ").append(category).append(":\n");
            items.forEach(product -> {
                sb.append("  - ").append(product.getName())
                        .append(": ").append(formatPrice(product.getPrice()))
                        .append(" VNĐ");
                if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
                    sb.append(" [Có hình ảnh]");
                }
                sb.append("\n");
            });
            sb.append("\n");
        });

        return sb.toString();
    }

    /**
     * Lấy menu theo category
     */
    public List<Product> getMenuByCategory(String category) {
        return productRepository.findByCategoryIgnoreCaseAndAvailableTrueOrderByNameAsc(category);
    }

    /**
     * Format price
     */
    private String formatPrice(Double price) {
        if (price == null) return "0";
        return String.format("%.0f", price);
    }
}

