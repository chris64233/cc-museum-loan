package com.chris64233.cc.museumloan.web;

import com.chris64233.cc.museumloan.service.LoanService;
import com.chris64233.cc.museumloan.web.dto.ArtifactResponse;
import com.chris64233.cc.museumloan.web.dto.InstitutionResponse;
import com.chris64233.cc.museumloan.web.dto.RegisterArtifactRequest;
import com.chris64233.cc.museumloan.web.dto.RegisterInstitutionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 馆藏品与借展机构基础资料登记。 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final LoanService loanService;

    public CatalogController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/artifacts")
    public ResponseEntity<ArtifactResponse> registerArtifact(
            @Valid @RequestBody RegisterArtifactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.registerArtifact(request));
    }

    @PostMapping("/institutions")
    public ResponseEntity<InstitutionResponse> registerInstitution(
            @Valid @RequestBody RegisterInstitutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.registerInstitution(request));
    }
}
