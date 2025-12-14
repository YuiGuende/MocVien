package hoavinh.mocvien_coffee.ai_engine.tools;

import hoavinh.mocvien_coffee.model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class để extract order items từ natural language
 */
public class OrderExtractionHelper {

    /**
     * Extract order items từ message
     * Format: "cho tôi 2 cà phê đen" -> {productName: "cà phê đen", quantity: 2}
     */
    public static List<ExtractedItem> extractOrderItems(String message, List<Product> menu) {
        List<ExtractedItem> items = new ArrayList<>();
        
        if (message == null || message.isBlank() || menu == null) {
            return items;
        }

        // Pattern để tìm số lượng và tên sản phẩm
        // Ví dụ: "2 cà phê đen", "cho tôi 3 bánh mì", "1 ly nước cam"
        Pattern pattern = Pattern.compile(
                "(?:cho\\s+tôi|tôi\\s+muốn|lấy|mua|đặt)\\s*(\\d+)\\s+(.+?)(?:\\s+và|\\s+,|$)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(message);
        
        while (matcher.find()) {
            try {
                int quantity = Integer.parseInt(matcher.group(1));
                String productText = matcher.group(2).trim();
                
                // Tìm product trong menu
                Product matchedProduct = findProductInMenu(productText, menu);
                if (matchedProduct != null) {
                    items.add(new ExtractedItem(matchedProduct, quantity, null));
                }
            } catch (Exception e) {
                // Skip invalid matches
            }
        }

        // Nếu không tìm thấy pattern, thử tìm số đơn giản hơn
        if (items.isEmpty()) {
            Pattern simplePattern = Pattern.compile("(\\d+)\\s+(.+?)(?:\\s+và|\\s+,|$)", Pattern.CASE_INSENSITIVE);
            Matcher simpleMatcher = simplePattern.matcher(message);
            
            while (simpleMatcher.find()) {
                try {
                    int quantity = Integer.parseInt(simpleMatcher.group(1));
                    String productText = simpleMatcher.group(2).trim();
                    
                    Product matchedProduct = findProductInMenu(productText, menu);
                    if (matchedProduct != null) {
                        items.add(new ExtractedItem(matchedProduct, quantity, null));
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }

        return items;
    }

    /**
     * Tìm product trong menu từ text
     */
    private static Product findProductInMenu(String text, List<Product> menu) {
        String normalized = normalize(text);
        
        for (Product product : menu) {
            String normalizedName = normalize(product.getName());
            if (normalizedName.contains(normalized) || normalized.contains(normalizedName)) {
                return product;
            }
        }
        
        return null;
    }

    /**
     * Normalize text
     */
    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[đ]", "d")
                .trim();
    }

    /**
     * Extracted item result
     */
    public static class ExtractedItem {
        private final Product product;
        private final int quantity;
        private final String note;

        public ExtractedItem(Product product, int quantity, String note) {
            this.product = product;
            this.quantity = quantity;
            this.note = note;
        }

        public Product getProduct() {
            return product;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getNote() {
            return note;
        }
    }
}

