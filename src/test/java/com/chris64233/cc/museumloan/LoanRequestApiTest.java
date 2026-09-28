package com.chris64233.cc.museumloan;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * REST 层测试：接口契约、幂等键头行为（重放返回原结果、内容冲突 409）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoanRequestApiTest {

    @Autowired
    private MockMvc mockMvc;

    private String institutionCode;
    private String catalogNo;

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    @BeforeEach
    void setUpCatalog() throws Exception {
        institutionCode = "INST-" + UUID.randomUUID();
        mockMvc.perform(post("/api/institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s", "name": "测试机构", "maxRisk": "HIGH"}
                                """.formatted(institutionCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(institutionCode))
                .andExpect(jsonPath("$.maxRisk").value("HIGH"));

        catalogNo = "ART-" + UUID.randomUUID();
        mockMvc.perform(post("/api/artifacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "catalogNo": "%s", "name": "测试藏品", "loanable": true,
                                  "minTemperature": 18, "maxTemperature": 24,
                                  "minHumidity": 45, "maxHumidity": 55,
                                  "transportRisk": "LOW"
                                }
                                """.formatted(catalogNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogNo").value(catalogNo))
                .andExpect(jsonPath("$.loanable").value(true));
    }

    private String submitBody(LocalDate start, LocalDate end) {
        return """
                {
                  "institutionCode": "%s",
                  "catalogNos": ["%s"],
                  "startDate": "%s", "endDate": "%s",
                  "promisedMinTemperature": 19, "promisedMaxTemperature": 23,
                  "promisedMinHumidity": 46, "promisedMaxHumidity": 54,
                  "transportPlanRisk": "LOW"
                }
                """.formatted(institutionCode, catalogNo, start, end);
    }

    private String submitAndGetRequestNo(LocalDate start, LocalDate end) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content(submitBody(start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.requestNo");
    }

    @Test
    void submitApproveAndQueryDetailAndCalendar() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);
        String requestNo = submitAndGetRequestNo(start, end);

        mockMvc.perform(post("/api/loan-requests/{no}/approve", requestNo)
                        .header("Idempotency-Key", key()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.audits", hasSize(2)));

        mockMvc.perform(get("/api/loan-requests/{no}", requestNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNo").value(requestNo))
                .andExpect(jsonPath("$.institutionCode").value(institutionCode))
                .andExpect(jsonPath("$.items[0].catalogNo").value(catalogNo));

        mockMvc.perform(get("/api/loan-requests/calendar/{catalogNo}", catalogNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogNo").value(catalogNo))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].requestNo").value(requestNo))
                .andExpect(jsonPath("$.entries[0].startDate").value(start.toString()))
                .andExpect(jsonPath("$.entries[0].endDate").value(end.toString()));
    }

    @Test
    void idempotentReplayReturnsOriginalResult() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);
        String idemKey = key();

        MvcResult first = mockMvc.perform(post("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idemKey)
                        .content(submitBody(start, end)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstBody = first.getResponse().getContentAsString();
        String requestNo = JsonPath.read(firstBody, "$.requestNo");

        // 相同键 + 相同内容重放：返回原结果，不创建新申请
        mockMvc.perform(post("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idemKey)
                        .content(submitBody(start, end)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNo").value(requestNo));

        // 相同键 + 不同内容：409
        mockMvc.perform(post("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idemKey)
                        .content(submitBody(start.plusDays(1), end)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("幂等键")));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(LocalDate.now().plusDays(10), LocalDate.now().plusDays(20))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));
    }

    @Test
    void validationFailureReturns400WithDetails() throws Exception {
        mockMvc.perform(post("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content("""
                                {
                                  "institutionCode": "",
                                  "catalogNos": [],
                                  "startDate": null, "endDate": null,
                                  "promisedMinTemperature": null, "promisedMaxTemperature": null,
                                  "promisedMinHumidity": null, "promisedMaxHumidity": null,
                                  "transportPlanRisk": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void rejectAndCancelFlowWithReasons() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);

        // 拒绝需要原因
        String rejected = submitAndGetRequestNo(start, end);
        mockMvc.perform(post("/api/loan-requests/{no}/reject", rejected)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/loan-requests/{no}/reject", rejected)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content("{\"reason\": \"保护方案不充分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("保护方案不充分"));

        // 已拒绝不能再取消
        mockMvc.perform(post("/api/loan-requests/{no}/cancel", rejected)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content("{\"reason\": \"x\"}"))
                .andExpect(status().isConflict());

        // 批准后可以取消（未开始）
        String approved = submitAndGetRequestNo(start, end);
        mockMvc.perform(post("/api/loan-requests/{no}/approve", approved)
                        .header("Idempotency-Key", key()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/loan-requests/{no}/cancel", approved)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key())
                        .content("{\"reason\": \"借展方取消\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reason").value("借展方取消"));

        // 取消后日历清空
        mockMvc.perform(get("/api/loan-requests/calendar/{catalogNo}", catalogNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void unknownRequestReturns404() throws Exception {
        mockMvc.perform(get("/api/loan-requests/{no}", "LR-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
