package hoavinh.mocvien_coffee.ai_engine.service;

import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.repository.ProductRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QdrantMenuService {

    private final VectorStore vectorStore;
    private final ProductRepository productRepository;
    private final String collectionName;
    private final String qdrantHost;
    private final int qdrantPort;

    public QdrantMenuService(VectorStore vectorStore, 
                            ProductRepository productRepository,
                            @Value("${spring.ai.vectorstore.qdrant.collection-name:mocvien_menu}") String collectionName,
                            @Value("${spring.ai.vectorstore.qdrant.host:localhost}") String qdrantHost,
                            @Value("${spring.ai.vectorstore.qdrant.port:6334}") int qdrantPort) {
        this.vectorStore = vectorStore;
        this.productRepository = productRepository;
        this.collectionName = collectionName;
        this.qdrantHost = qdrantHost;
        // REST API dùng port 6333, gRPC dùng port 6334
        // Spring AI QdrantVectorStore dùng gRPC (port 6334) cho add/search operations
        // REST API delete operations dùng port 6333 (HTTP)
        this.qdrantPort = 6333; // Dùng port 6333 cho REST API delete operations
        System.out.println("🔧 QdrantMenuService initialized: " + qdrantHost + ":" + qdrantPort + "/" + collectionName);
        System.out.println("🔧 Note: Spring AI uses gRPC (port 6334), REST API delete uses port 6333");
    }

    /**
     * Sync toàn bộ menu từ DB vào Qdrant
     */
    @Transactional
    public void syncAllMenuToQdrant() {
        List<Product> products = productRepository.findAllAvailable();
        syncProductsToQdrant(products);
    }

    /**
     * Sync một hoặc nhiều products vào Qdrant
     */
    public void syncProductsToQdrant(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        try {
            List<Document> documents = products.stream()
                    .map(this::productToDocument)
                    .collect(Collectors.toList());

            vectorStore.add(documents);
            System.out.println("✅ Successfully synced " + documents.size() + " products to Qdrant");
        } catch (Exception e) {
            System.err.println("❌ Error syncing products to Qdrant: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to sync products to Qdrant", e);
        }
    }

    /**
     * Sync một product vào Qdrant
     */
    public void syncProductToQdrant(Product product) {
        if (product == null) {
            return;
        }
        try {
            Document document = productToDocument(product);
            vectorStore.add(List.of(document));
            System.out.println("✅ Successfully synced product " + product.getId() + " (" + product.getName() + ") to Qdrant");
        } catch (Exception e) {
            System.err.println("❌ Error syncing product " + product.getId() + " to Qdrant: " + e.getMessage());
            e.printStackTrace();
            // Không throw exception để không block save operation
            // Product vẫn được lưu vào DB, chỉ sync Qdrant fail
        }
    }

    /**
     * Xóa product khỏi Qdrant bằng cách delete document có metadata.productId = productId
     * Cách 1: Tìm point bằng search, lấy UUID, rồi xóa bằng UUID
     * Cách 2: Xóa bằng filter (REST API)
     */
    public void removeProductFromQdrant(Long productId) {
        if (productId == null) {
            return;
        }

        try {
            // Cách 1: Tìm point bằng search để lấy UUID thực tế
            String pointUuid = findPointUuidByProductId(productId);
            if (pointUuid != null) {
                deletePointByUuid(pointUuid);
                System.out.println("✅ Successfully deleted product " + productId + " (UUID: " + pointUuid + ")");
                return;
            }
            
            // Cách 2: Fallback - xóa bằng filter (REST API)
            System.out.println("⚠️ Could not find UUID, trying filter method...");
            deleteProductViaRestApi(productId);
        } catch (Exception e) {
            System.err.println("❌ Error deleting product from Qdrant: " + e.getMessage());
            e.printStackTrace();
            // Fallback: re-sync (loại bỏ product đã xóa) - chỉ khi tất cả methods fail
            System.out.println("🔄 Falling back to re-sync all menu...");
            syncAllMenuToQdrant();
        }
    }

    /**
     * Tìm UUID của point bằng cách search với filter productId
     */
    private String findPointUuidByProductId(Long productId) {
        try {
            // Note: Spring AI SearchRequest không hỗ trợ filter trực tiếp
            // Cần search tất cả rồi filter trong code
            // Dùng query "product" để lấy documents (không thể dùng empty query)
            List<Document> allDocs = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("product") // Query bất kỳ để lấy documents
                            .topK(1000) // Lấy nhiều để tìm
                            .build()
            );
            
            // Tìm document có productId trùng
            for (Document doc : allDocs) {
                var metadata = doc.getMetadata();
                if (metadata != null) {
                    String productIdStr = (String) metadata.get("productId");
                    if (productIdStr != null && productIdStr.equals(productId.toString())) {
                        String docId = doc.getId();
                        System.out.println("🔍 Found point UUID: " + docId + " for productId: " + productId);
                        return docId;
                    }
                }
            }
            
            System.out.println("⚠️ Could not find point with productId: " + productId);
            return null;
        } catch (Exception e) {
            System.err.println("Error finding point UUID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Xóa point bằng UUID
     */
    private void deletePointByUuid(String pointUuid) throws java.io.IOException, InterruptedException {
        int[] portsToTry = {6333, 6334};
        
        for (int port : portsToTry) {
            try {
                String url = String.format("http://%s:%d/collections/%s/points/delete", 
                        qdrantHost, port, collectionName);
                
                String pointIdJson = String.format("{\"points\":[\"%s\"]}", pointUuid);
                
                System.out.println("🗑️ Deleting point UUID: " + pointUuid + " on port " + port);
                
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build();
                
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(pointIdJson))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("✅ Successfully deleted point " + pointUuid + " (port " + port + ")");
                    Thread.sleep(500);
                    return;
                }
            } catch (Exception e) {
                if (port == portsToTry[portsToTry.length - 1]) {
                    throw e;
                }
                continue;
            }
        }
        
        throw new RuntimeException("Failed to delete point by UUID on all ports");
    }

    /**
     * Xóa product từ Qdrant bằng REST API
     * Sử dụng filter để xóa bằng productId trong payload (reliable hơn UUID)
     * Qdrant REST API: POST /collections/{collection_name}/points/delete
     * Body: { "filter": { "must": [{ "key": "productId", "match": { "value": "9" } }] } }
     * 
     * Reference: https://qdrant.tech/documentation/concepts/points/#delete-points
     */
    private void deleteProductViaRestApi(Long productId) throws java.io.IOException, InterruptedException {
        // Ưu tiên dùng filter vì productId có trong payload, reliable hơn UUID
        deleteProductViaFilter(productId);
    }

    /**
     * Xóa bằng filter - tìm point có productId trong payload
     * Qdrant REST API: POST /collections/{collection_name}/points/delete
     * Body: { "filter": { "must": [{ "key": "productId", "match": { "value": "9" } }] } }
     */
    private void deleteProductViaFilter(Long productId) throws java.io.IOException, InterruptedException {
        // Qdrant REST API thường ở port 6333 (HTTP) hoặc 6334 (gRPC)
        // Thử port 6333 trước (HTTP REST API)
        int[] portsToTry = {6333, 6334};
        
        for (int port : portsToTry) {
            try {
                String url = String.format("http://%s:%d/collections/%s/points/delete", 
                        qdrantHost, port, collectionName);
                
                // Build filter để tìm document có productId trong payload
                String filterJson = String.format(
                    "{\"filter\":{\"must\":[{\"key\":\"productId\",\"match\":{\"value\":\"%s\"}}]}}",
                    productId.toString()
                );
                
                System.out.println("🔍 Attempting to delete product " + productId + " from Qdrant");
                System.out.println("🔍 Filter JSON: " + filterJson);
                System.out.println("🔍 URL: " + url);
                
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build();
                
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(filterJson))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                
                System.out.println("📡 Qdrant delete response status: " + response.statusCode());
                System.out.println("📡 Qdrant delete response body: " + response.body());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String responseBody = response.body();
                    if (responseBody != null && (responseBody.contains("\"status\":\"ok\"") || 
                                                  responseBody.contains("\"status\":\"acknowledged\""))) {
                        System.out.println("✅ Successfully deleted product " + productId + " from Qdrant (port " + port + ")");
                        Thread.sleep(500); // Wait for async operation
                        return; // Success, exit
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Failed to delete on port " + port + ": " + e.getMessage());
                if (port == portsToTry[portsToTry.length - 1]) {
                    // Last port, throw exception
                    throw e;
                }
                // Try next port
                continue;
            }
        }
        
        throw new RuntimeException("Failed to delete product from Qdrant on all ports");
    }

    /**
     * Alternative filter format - thử cách khác (nếu method chính fail)
     * Note: Method này được gọi từ deleteProductViaFilter nếu cần
     */
    @SuppressWarnings("unused")
    private void deleteProductViaFilterAlternative(Long productId) throws java.io.IOException, InterruptedException {
        String url = String.format("http://%s:%d/collections/%s/points/delete", 
                qdrantHost, qdrantPort, collectionName);
        
        // Thử format khác: dùng any array
        String filterJson = String.format(
            "{\"filter\":{\"must\":[{\"key\":\"productId\",\"match\":{\"any\":[\"%s\"]}}]}}",
            productId.toString()
        );
        
        System.out.println("🔄 Alternative filter: " + filterJson);
        
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(filterJson))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ Successfully deleted product " + productId + " (alternative method)");
        } else {
            System.err.println("❌ Alternative delete also failed. Status: " + response.statusCode());
            System.err.println("Response: " + response.body());
            throw new RuntimeException("Qdrant delete failed with all methods. Check logs above for details.");
        }
    }

    /**
     * Tìm kiếm products trong Qdrant bằng semantic search
     */
    public List<Product> searchProductsInQdrant(String query, int topK) {
        var searchRequest = org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        
        return documents.stream()
                .map(this::documentToProduct)
                .filter(product -> product != null && product.isAvailable())
                .collect(Collectors.toList());
    }

    /**
     * Convert Product thành Document để lưu vào Qdrant
     * QUAN TRỌNG: Spring AI QdrantVectorStore yêu cầu document ID phải là UUID format
     * Giải pháp: Generate UUID từ productId (deterministic) để có thể upsert
     */
    private Document productToDocument(Product product) {
        // Format content cho AI hiểu
        String content = String.format(
                "Tên món: %s\n" +
                "Giá: %s VNĐ\n" +
                "Danh mục: %s\n" +
                "Trạng thái: %s",
                product.getName(),
                formatPrice(product.getPrice()),
                product.getCategory(),
                product.isAvailable() ? "Còn hàng" : "Hết hàng"
        );

        // Metadata để có thể query và filter
        var metadata = new java.util.HashMap<String, Object>();
        metadata.put("productId", product.getId().toString());
        metadata.put("productName", product.getName());
        metadata.put("category", product.getCategory());
        metadata.put("price", product.getPrice());
        metadata.put("available", product.isAvailable());

        // Generate UUID từ productId (deterministic)
        // Cách này đảm bảo cùng productId luôn có cùng UUID, cho phép upsert
        String documentId = generateUuidFromProductId(product.getId());
        
        // Spring AI Document constructor: Document(String id, String content, Map<String, Object> metadata)
        // ID phải là UUID format
        Document doc = new Document(documentId, content, metadata);
        return doc;
    }

    /**
     * Generate UUID từ productId (deterministic)
     * Cùng productId sẽ luôn có cùng UUID, cho phép upsert
     */
    private String generateUuidFromProductId(Long productId) {
        // Tạo UUID v5 (deterministic) từ productId
        // UUID v5 namespace: dùng một namespace cố định
        String namespace = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"; // Standard namespace UUID
        String name = "product_" + productId;
        
        try {
            java.util.UUID namespaceUuid = java.util.UUID.fromString(namespace);
            return java.util.UUID.nameUUIDFromBytes(
                (namespaceUuid.toString() + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ).toString();
        } catch (Exception e) {
            // Fallback: dùng hash-based UUID
            return java.util.UUID.nameUUIDFromBytes(
                ("mocvien_product_" + productId).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ).toString();
        }
    }

    /**
     * Convert Document về Product (từ Qdrant)
     */
    private Product documentToProduct(Document document) {
        var metadata = document.getMetadata();
        if (metadata == null) {
            return null;
        }

        String productIdStr = (String) metadata.get("productId");
        if (productIdStr == null) {
            return null;
        }

        try {
            Long productId = Long.parseLong(productIdStr);
            return productRepository.findById(productId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Format price
     */
    private String formatPrice(Double price) {
        if (price == null) return "0";
        return String.format("%.0f", price);
    }

    /**
     * Clear và re-sync toàn bộ menu (dùng khi cần reset)
     */
    @Transactional
    public void clearAndResyncMenu() {
        // Clear collection bằng REST API
        try {
            String url = String.format("http://%s:%d/collections/%s/points/delete", 
                    qdrantHost, qdrantPort, collectionName);
            
            // Delete all points
            String filterJson = "{\"filter\":{\"must\":[]}}";
            
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(filterJson))
                    .build();
            
            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Error clearing Qdrant collection: " + e.getMessage());
        }
        
        // Re-sync
        syncAllMenuToQdrant();
    }
}
