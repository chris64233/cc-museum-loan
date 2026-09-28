package com.chris64233.cc.museumloan.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.museumloan.service.CatalogService;
import com.chris64233.cc.museumloan.web.dto.ArtifactResponse;
import com.chris64233.cc.museumloan.web.dto.CreateArtifactRequest;
import com.chris64233.cc.museumloan.web.dto.CreateInstitutionRequest;
import com.chris64233.cc.museumloan.web.dto.InstitutionResponse;

/** 馆藏品与借展机构资料管理。 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/artifacts")
    public ResponseEntity<ArtifactResponse> createArtifact(@Valid @RequestBody CreateArtifactRequest request) {
        ArtifactResponse response = catalogService.createArtifact(request);
        return ResponseEntity.created(URI.create("/api/artifacts/" + response.catalogNo())).body(response);
    }

    @GetMapping("/artifacts")
    public List<ArtifactResponse> listArtifacts() {
        return catalogService.listArtifacts();
    }

    @GetMapping("/artifacts/{catalogNo}")
    public ArtifactResponse getArtifact(@PathVariable String catalogNo) {
        return catalogService.getArtifact(catalogNo);
    }

    @PostMapping("/institutions")
    public ResponseEntity<InstitutionResponse> createInstitution(@Valid @RequestBody CreateInstitutionRequest request) {
        InstitutionResponse response = catalogService.createInstitution(request);
        return ResponseEntity.created(URI.create("/api/institutions/" + response.code())).body(response);
    }

    @GetMapping("/institutions")
    public List<InstitutionResponse> listInstitutions() {
        return catalogService.listInstitutions();
    }

    @GetMapping("/institutions/{code}")
    public InstitutionResponse getInstitution(@PathVariable String code) {
        return catalogService.getInstitution(code);
    }
}
