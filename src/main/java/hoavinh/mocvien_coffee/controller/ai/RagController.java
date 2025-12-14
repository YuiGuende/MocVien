package hoavinh.mocvien_coffee.controller.ai;

import hoavinh.mocvien_coffee.ai_engine.service.MenuContextService;
import hoavinh.mocvien_coffee.ai_engine.service.QdrantMenuService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
public class RagController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final MenuContextService menuContextService;
    private final QdrantMenuService qdrantMenuService;

    public RagController(ChatClient.Builder chatClientBuilder, 
                         VectorStore vectorStore,
                         MenuContextService menuContextService,
                         QdrantMenuService qdrantMenuService) {
        this.chatClient = chatClientBuilder
                .defaultSystem("Bạn là nhân viên AI của quán Mộc Miên.")
                .build();
        this.vectorStore = vectorStore;
        this.menuContextService = menuContextService;
        this.qdrantMenuService = qdrantMenuService;
    }

    @GetMapping("/ai/load")
    public String loadData() {
        // Load menu từ database và sync vào Qdrant
        var products = menuContextService.loadMenuFromDatabase();
        qdrantMenuService.syncAllMenuToQdrant();
        
        return String.format("✅ Đã nạp xong Menu từ database vào Qdrant! (Tổng: %d món)", products.size());
    }

    // --- API 2: Hỏi đáp (Retrieval + Generation) ---
    @GetMapping(value = "/ai/ask", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> askAi(@RequestParam String question) {

        // --- BƯỚC 1: RETRIEVAL (Đo đạc) ---
        long startRetrieval = System.currentTimeMillis();

        // Tìm kiếm Top 2 documents (Tối ưu cho RAM ít, đừng tham Top 5-10)
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(2).build()
        );

        long endRetrieval = System.currentTimeMillis();
        System.out.printf(">>> [1] Retrieval: %d ms | Found: %d docs%n",
                (endRetrieval - startRetrieval), similarDocuments.size());

        if (similarDocuments.isEmpty()) {
            return Flux.just("Xin lỗi, quán em chưa có thông tin món này ạ.");
        }

        // --- BƯỚC 2: PREPARE PROMPT ---
        String context = similarDocuments.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n"));

        String promptText = """
                Dựa vào menu sau:
                {context}
                ----------------------
                Trả lời khách hàng câu hỏi: {question}
                (Trả lời ngắn gọn, vui vẻ, xưng là 'em' và gọi khách là 'anh/chị')
                """;

        PromptTemplate template = new PromptTemplate(promptText);
        Prompt prompt = template.create(Map.of("context", context, "question", question));

        // --- BƯỚC 3: GENERATION (STREAMING + METRICS) ---
        long startGen = System.currentTimeMillis();

        // Dùng Atomic để thread-safe trong Flux
        AtomicBoolean isFirstToken = new AtomicBoolean(true);
        AtomicLong firstTokenTime = new AtomicLong(0);

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(token -> {
                    // Bắt khoảnh khắc Token đầu tiên xuất hiện (TTFT)
                    if (isFirstToken.getAndSet(false)) {
                        long ttft = System.currentTimeMillis() - startGen;
                        firstTokenTime.set(ttft);
                        System.out.println(">>> [2] TTFT (Độ trễ phản hồi): " + ttft + " ms");
                    }
                })
                .doOnComplete(() -> {
                    long totalTime = System.currentTimeMillis() - startGen;
                    long ttft = firstTokenTime.get();
                    // TPS = Tokens Per Second (tính ước lượng)
                    // Cậu nên log thêm số lượng token nhận được nếu muốn tính chính xác TPS
                    System.out.printf(">>> [3] Total Generation: %d ms | Generation Time (trừ TTFT): %d ms%n",
                            totalTime, (totalTime - ttft));
                    System.out.println("--------------------------------------------------");
                })
                .doOnError(e -> System.err.println(">>> [ERROR]: " + e.getMessage()));
    }
}