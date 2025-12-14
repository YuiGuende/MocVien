package hoavinh.mocvien_coffee.ai_engine.service;

import hoavinh.mocvien_coffee.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductMatchingService {

    /**
     * Tìm sản phẩm theo tên (fuzzy matching)
     */
    public List<Product> findProductsByName(String productName, List<Product> menu) {
        if (productName == null || productName.isBlank() || menu == null) {
            return new ArrayList<>();
        }

        String normalizedQuery = normalize(productName);
        return menu.stream()
                .filter(product -> {
                    String normalizedName = normalize(product.getName());
                    return normalizedName.contains(normalizedQuery) ||
                           normalizedQuery.contains(normalizedName) ||
                           calculateSimilarity(normalizedName, normalizedQuery) > 0.7;
                })
                .collect(Collectors.toList());
    }

    /**
     * Tìm sản phẩm tốt nhất từ câu nói tự nhiên
     */
    public Product matchProductFromNaturalLanguage(String text, List<Product> menu) {
        List<Product> matches = findProductsByName(text, menu);
        if (matches.isEmpty()) {
            return null;
        }
        // Trả về match đầu tiên (có thể cải thiện bằng cách tính điểm similarity cao nhất)
        return matches.get(0);
    }

    /**
     * Gợi ý sản phẩm tương tự (partial name matching)
     */
    public List<Product> suggestSimilarProducts(String partialName, List<Product> menu) {
        if (partialName == null || partialName.isBlank() || menu == null) {
            return new ArrayList<>();
        }

        String normalizedQuery = normalize(partialName);
        return menu.stream()
                .filter(product -> {
                    String normalizedName = normalize(product.getName());
                    return normalizedName.contains(normalizedQuery);
                })
                .limit(5) // Giới hạn 5 gợi ý
                .collect(Collectors.toList());
    }

    /**
     * Normalize string để so sánh (lowercase, remove accents, remove spaces)
     */
    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[đ]", "d")
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * Tính similarity đơn giản (Levenshtein-like)
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Tính Levenshtein distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1)
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }
}

