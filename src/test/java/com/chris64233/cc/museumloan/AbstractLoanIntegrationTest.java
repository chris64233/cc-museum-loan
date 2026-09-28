package com.chris64233.cc.museumloan;

import com.chris64233.cc.museumloan.repository.ArtifactRepository;
import com.chris64233.cc.museumloan.repository.IdempotencyRecordRepository;
import com.chris64233.cc.museumloan.repository.InstitutionRepository;
import com.chris64233.cc.museumloan.repository.LoanAuditLogRepository;
import com.chris64233.cc.museumloan.repository.LoanRequestItemRepository;
import com.chris64233.cc.museumloan.repository.LoanRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractLoanIntegrationTest {

    protected static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ArtifactRepository artifactRepository;

    @Autowired
    protected InstitutionRepository institutionRepository;

    @Autowired
    protected LoanRequestRepository loanRequestRepository;

    @Autowired
    protected LoanRequestItemRepository itemRepository;

    @Autowired
    protected LoanAuditLogRepository auditLogRepository;

    @Autowired
    protected IdempotencyRecordRepository idempotencyRepository;

    @BeforeEach
    void cleanDatabase() {
        idempotencyRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        loanRequestRepository.deleteAllInBatch();
        artifactRepository.deleteAllInBatch();
        institutionRepository.deleteAllInBatch();
    }

    protected String newKey() {
        return UUID.randomUUID().toString();
    }

    // ---------------------------------------------------------------
    // 基础资料
    // ---------------------------------------------------------------

    protected ResultActions registerArtifact(String catalogNo, boolean loanable,
                                             String minTemp, String maxTemp,
                                             String minHumidity, String maxHumidity,
                                             String transportRisk) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("catalogNo", catalogNo);
        body.put("name", "藏品-" + catalogNo);
        body.put("loanable", loanable);
        body.put("minTemp", new BigDecimal(minTemp));
        body.put("maxTemp", new BigDecimal(maxTemp));
        body.put("minHumidity", new BigDecimal(minHumidity));
        body.put("maxHumidity", new BigDecimal(maxHumidity));
        body.put("transportRisk", transportRisk);
        return postJson("/api/artifacts", body, null);
    }

    /** 默认环境窗口较宽的藏品，便于聚焦单一变量。 */
    protected ResultActions registerDefaultArtifact(String catalogNo, String transportRisk) throws Exception {
        return registerArtifact(catalogNo, true, "15.00", "25.00", "40.00", "60.00", transportRisk);
    }

    protected ResultActions registerInstitution(String code, String maxRisk) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", "机构-" + code);
        body.put("maxRisk", maxRisk);
        return postJson("/api/institutions", body, null);
    }

    // ---------------------------------------------------------------
    // 借展操作
    // ---------------------------------------------------------------

    protected Map<String, Object> applyBody(String institutionCode, List<String> catalogNos,
                                            String startDate, String endDate,
                                            String minTemp, String maxTemp,
                                            String minHumidity, String maxHumidity,
                                            String transportRisk) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("institutionCode", institutionCode);
        body.put("artifactCatalogNos", catalogNos);
        body.put("startDate", LocalDate.parse(startDate).toString());
        body.put("endDate", LocalDate.parse(endDate).toString());
        body.put("committedMinTemp", new BigDecimal(minTemp));
        body.put("committedMaxTemp", new BigDecimal(maxTemp));
        body.put("committedMinHumidity", new BigDecimal(minHumidity));
        body.put("committedMaxHumidity", new BigDecimal(maxHumidity));
        body.put("transportRisk", transportRisk);
        return body;
    }

    /** 默认承诺区间落在默认藏品窗口内。 */
    protected Map<String, Object> defaultApplyBody(String institutionCode, List<String> catalogNos,
                                                   String startDate, String endDate,
                                                   String transportRisk) {
        return applyBody(institutionCode, catalogNos, startDate, endDate,
                "18.00", "22.00", "45.00", "55.00", transportRisk);
    }

    protected ResultActions apply(String key, Map<String, Object> body) throws Exception {
        return postJson("/api/loans", body, key);
    }

    protected ResultActions approve(long loanId, String key) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/loans/{id}/approval", loanId)
                .header(IDEMPOTENCY_KEY, key));
    }

    protected ResultActions reject(long loanId, String key, String reason) throws Exception {
        return postJson("/api/loans/" + loanId + "/rejection",
                Map.of("reason", reason), key);
    }

    protected ResultActions cancel(long loanId, String key, String reason) throws Exception {
        return postJson("/api/loans/" + loanId + "/cancellation",
                Map.of("reason", reason), key);
    }

    protected ResultActions detail(long loanId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/loans/{id}", loanId));
    }

    protected ResultActions calendar(String catalogNo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/loans/artifacts/{catalogNo}/calendar", catalogNo));
    }

    protected ResultActions postJson(String url, Object body, String idempotencyKey) throws Exception {
        var request = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            request = request.header(IDEMPOTENCY_KEY, idempotencyKey);
        }
        return mvc.perform(request);
    }

    /** 提交申请并断言成功，返回申请 id。 */
    protected long applySuccessfully(String key, Map<String, Object> body) throws Exception {
        MvcResult result = apply(key, body).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .as("apply should succeed: %s", result.getResponse().getContentAsString())
                .isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    protected long applyAndApprove(String institutionCode, List<String> catalogNos,
                                   String startDate, String endDate, String transportRisk) throws Exception {
        long id = applySuccessfully(newKey(),
                defaultApplyBody(institutionCode, catalogNos, startDate, endDate, transportRisk));
        MvcResult approved = approve(id, newKey()).andReturn();
        org.assertj.core.api.Assertions.assertThat(approved.getResponse().getStatus())
                .as("approve should succeed: %s", approved.getResponse().getContentAsString())
                .isEqualTo(200);
        return id;
    }
}
