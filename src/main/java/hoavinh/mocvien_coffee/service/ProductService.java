package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.ai_engine.service.QdrantMenuService;
import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final QdrantMenuService qdrantMenuService;

    public ProductService(ProductRepository productRepository,
                         QdrantMenuService qdrantMenuService) {
        this.productRepository = productRepository;
        this.qdrantMenuService = qdrantMenuService;
    }

    public List<Product> getAvailableProducts(String category) {
        return searchProducts(category, null);
    }

    public List<Product> searchProducts(String category, String search) {
        List<Product> base = (category == null || category.isBlank() || category.equalsIgnoreCase("all"))
                ? productRepository.findAllAvailable()
                : productRepository.findByCategoryIgnoreCaseAndAvailableTrueOrderByNameAsc(category);

        if (search == null || search.isBlank()) {
            return base;
        }
        String keyword = search.toLowerCase(Locale.ROOT);
        return base.stream()
                .filter(product -> product.getName().toLowerCase(Locale.ROOT).contains(keyword))
                .collect(Collectors.toList());
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional
    public Product save(Product product) {
        Product saved = productRepository.save(product);
        // Sync to Qdrant
        if (saved.isAvailable()) {
            // Chỉ sync product này, không sync lại toàn bộ
            qdrantMenuService.syncProductToQdrant(saved);
        } else {
            // Nếu unavailable, xóa khỏi Qdrant (chỉ xóa product này)
            qdrantMenuService.removeProductFromQdrant(saved.getId());
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        // Xóa khỏi DB trước
        productRepository.deleteById(id);
        // Chỉ xóa product cụ thể khỏi Qdrant, không sync lại toàn bộ
        qdrantMenuService.removeProductFromQdrant(id);
    }

    public Product getById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }
}

