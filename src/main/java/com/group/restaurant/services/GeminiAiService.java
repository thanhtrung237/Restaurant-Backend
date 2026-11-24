package com.group.restaurant.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.restaurant.dtos.response.report.AnomalyAlert;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class GeminiAiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Gửi dữ liệu bất thường sang Gemini để phân tích hành vi và đề xuất giải pháp.
     */
    public void enrichAnomalyWithAi(AnomalyAlert alert) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("xxx")) {
            log.info("Gemini API key not configured. Using statistical fallback explanation.");
            applyFallbackExplanation(alert);
            return;
        }

        try {
            String url =
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + model
                            + ":generateContent?key="
                            + apiKey.trim();

            String prompt =
                    String.format(
                            """
                Bạn là Chuyên gia Kiểm toán Vận hành và Chống gian lận F&B (Nhà hàng SaaS).
                Dưới đây là một hành vi bất thường vừa được hệ thống tự động phát hiện:
                - Loại bất thường: %s
                - Mức độ: %s
                - Địa điểm: %s
                - Mô tả: %s
                - Số liệu liên quan: %s

                Hãy đóng vai trò chuyên gia, viết 2 đoạn ngắn bằng tiếng Việt:
                1. AI_EXPLANATION: Giải thích ngắn gọn vì sao hành vi này bất thường và rủi ro thất thoát/gian lận có thể xảy ra (khoảng 2-3 câu).
                2. RECOMMENDATION: Đề xuất 1-2 hành động cụ thể người quản lý/chủ nhà hàng nên thực hiện ngay để xử lý hoặc ngăn chặn.

                Trả về ĐÚNG định dạng JSON sau (không kèm markdown code block, chỉ JSON thuần):
                {
                   "aiExplanation": "...",
                   "recommendation": "..."
                }
                """,
                            alert.getType().getDescription(),
                            alert.getSeverity(),
                            alert.getLocation(),
                            alert.getDescription(),
                            alert.getMetrics() != null ? alert.getMetrics().toString() : "{}");

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    String text =
                            candidates
                                    .get(0)
                                    .path("content")
                                    .path("parts")
                                    .get(0)
                                    .path("text")
                                    .asText();
                    parseAiResponse(alert, text);
                    log.info(
                            "Successfully enriched anomaly alert [{}] using Gemini AI",
                            alert.getId());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Gemini API call failed or timed out: {}. Falling back to rule-based engine.",
                    e.getMessage());
        }

        applyFallbackExplanation(alert);
    }

    /**
     * Sinh đoạn tóm tắt tổng quan rủi ro vận hành toàn chuỗi bằng Gemini
     */
    public String generateExecutiveSummary(List<AnomalyAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return "Hệ thống vận hành ổn định trong kỳ báo cáo. Không ghi nhận dấu hiệu gian lận hay thất thoát bất thường.";
        }

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("xxx")) {
            return buildFallbackExecutiveSummary(alerts);
        }

        try {
            String url =
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + model
                            + ":generateContent?key="
                            + apiKey.trim();

            StringBuilder sb = new StringBuilder();
            for (AnomalyAlert a : alerts) {
                sb.append(
                        String.format(
                                "- [%s] %s tại %s: %s\n",
                                a.getSeverity(),
                                a.getTitle(),
                                a.getLocation(),
                                a.getDescription()));
            }

            String prompt =
                    String.format(
                            """
                Bạn là Cố vấn Điều hành Cấp cao (Executive Operations Advisor) của chuỗi nhà hàng SaaS.
                Tổng hợp các cảnh báo bất thường dưới đây thành 1 đoạn nhận định tổng quan (khoảng 3-4 câu) gửi cho Ban Giám đốc:
                %s

                Yêu cầu:
                - Ngắn gọn, chuyên nghiệp, đánh giá rủi ro thất thoát doanh thu.
                - Đưa ra lời khuyên trọng tâm cho chủ nhà hàng trong ca làm việc.
                - Trả về văn bản thuần tiếng Việt, không dùng bullet point.
                """,
                            sb.toString());

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    return candidates
                            .get(0)
                            .path("content")
                            .path("parts")
                            .get(0)
                            .path("text")
                            .asText()
                            .trim();
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Gemini Executive summary failed: {}. Using fallback summary.", e.getMessage());
        }

        return buildFallbackExecutiveSummary(alerts);
    }

    private void parseAiResponse(AnomalyAlert alert, String rawText) {
        try {
            String cleaned = rawText.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has("aiExplanation") && !node.get("aiExplanation").asText().isBlank()) {
                alert.setAiExplanation(node.get("aiExplanation").asText());
            }
            if (node.has("recommendation") && !node.get("recommendation").asText().isBlank()) {
                alert.setRecommendation(node.get("recommendation").asText());
            }
        } catch (Exception e) {
            alert.setAiExplanation(rawText.trim());
            alert.setRecommendation(
                    "Quản lý chi nhánh cần rà soát lại nhật ký giao dịch và camera giám sát trong khung giờ xảy ra sự kiện.");
        }
    }

    private void applyFallbackExplanation(AnomalyAlert alert) {
        switch (alert.getType()) {
            case SURGE_CANCELLATION -> {
                alert.setAiExplanation(
                        "Tỷ lệ hủy món cao bất thường so với mức trung bình của chi nhánh. Đây có thể là dấu hiệu bếp quá tải dẫn đến chậm trễ khiến khách hủy, hoặc nguy cơ nhân viên in tạm tính thu tiền mặt rồi hủy món để hưởng lợi chênh lệch.");
                alert.setRecommendation(
                        "Rà soát camera khu vực thu ngân và bếp; đối chiếu các phiếu in tạm tính (pre-bill) đã in trước thời điểm hủy.");
            }
            case SUSPICIOUS_DISCOUNT -> {
                alert.setAiExplanation(
                        "Phát hiện chiết khấu thủ công vượt hạn mức thông thường hoặc áp dụng lặp lại cho nhiều bàn. Cần kiểm tra quyền hạn áp dụng chiết khấu để ngăn chặn việc lạm dụng quyền hạn cho người quen.");
                alert.setRecommendation(
                        "Kiểm tra mã phê duyệt của quản lý (Manager PIN) và yêu cầu nhân viên giải trình lý do giảm giá vượt hạn mức.");
            }
            case REPEATED_TABLE_CANCELLATIONS -> {
                alert.setAiExplanation(
                        "Bàn ăn có nhiều lượt hủy món sau khi đã chế biến. Tình trạng này gây lãng phí nguyên vật liệu và ảnh hưởng trực tiếp đến trải nghiệm thực khách.");
                alert.setRecommendation(
                        "Kiểm tra thời gian phục vụ của nhân viên phụ trách bàn và phản hồi từ khách tại bàn.");
            }
            default -> {
                alert.setAiExplanation(
                        "Hệ thống phát hiện biến động vận hành vượt ngưỡng thống kê tiêu chuẩn (Z-score > 2.5). Cần được quản lý thẩm định.");
                alert.setRecommendation(
                        "Kiểm tra nhật ký kiểm toán (Audit Log) để xác định nhân sự thao tác và bối cảnh sự kiện.");
            }
        }
    }

    private String buildFallbackExecutiveSummary(List<AnomalyAlert> alerts) {
        long critical =
                alerts.stream()
                        .filter(
                                a ->
                                        a.getSeverity()
                                                == com.group.restaurant.enums.AnomalySeverity
                                                        .CRITICAL)
                        .count();
        if (critical > 0) {
            return String.format(
                    "Hệ thống ghi nhận %d sự kiện có dấu hiệu bất thường nghiêm trọng (nguy cơ thất thoát doanh thu cao). Ban quản lý cần ưu tiên kiểm tra các ca trực có tỷ lệ hủy món đột biến và các hóa đơn giảm giá thủ công bất thường.",
                    critical);
        }
        return String.format(
                "Ghi nhận %d cảnh báo vận hành cần lưu ý trong kỳ báo cáo. Tỷ lệ hủy món và chiết khấu đang có dấu hiệu dao động cao hơn mức trung bình, khuyến nghị quản lý chi nhánh rà soát quy trình phục vụ tại bàn.",
                alerts.size());
    }
}
