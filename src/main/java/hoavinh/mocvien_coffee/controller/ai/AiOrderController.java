package hoavinh.mocvien_coffee.controller.ai;

import hoavinh.mocvien_coffee.ai_engine.service.AiOrderAgentService;
import hoavinh.mocvien_coffee.dto.AiOrderRequest;
import hoavinh.mocvien_coffee.dto.AiOrderResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/order")
public class AiOrderController {

    private final AiOrderAgentService aiOrderAgentService;

    public AiOrderController(AiOrderAgentService aiOrderAgentService) {
        this.aiOrderAgentService = aiOrderAgentService;
    }

    /**
     * Endpoint chính để xử lý message từ khách hàng
     * POST /ai/order/chat
     */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiOrderResponse> chat(@RequestBody AiOrderRequest request) {
        try {
            AiOrderResponse response = aiOrderAgentService.processCustomerMessage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // Log error
            // Return error response
            AiOrderResponse errorResponse = new AiOrderResponse(
                    "Xin lỗi, có lỗi xảy ra. Anh/chị vui lòng thử lại ạ.",
                    hoavinh.mocvien_coffee.ai_engine.advisors.OrderIntent.UNKNOWN,
                    java.util.Collections.emptyList(),
                    null,
                    null,
                    false
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Order Service is running");
    }
}

