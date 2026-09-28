package com.chris64233.cc.museumloan.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.museumloan.domain.Artifact;
import com.chris64233.cc.museumloan.domain.BorrowingInstitution;
import com.chris64233.cc.museumloan.repo.ArtifactRepository;
import com.chris64233.cc.museumloan.repo.BorrowingInstitutionRepository;
import com.chris64233.cc.museumloan.web.dto.ArtifactResponse;
import com.chris64233.cc.museumloan.web.dto.CreateArtifactRequest;
import com.chris64233.cc.museumloan.web.dto.CreateInstitutionRequest;
import com.chris64233.cc.museumloan.web.dto.InstitutionResponse;
import com.chris64233.cc.museumloan.web.error.BadRequestException;
import com.chris64233.cc.museumloan.web.error.ConflictException;
import com.chris64233.cc.museumloan.web.error.NotFoundException;

/** 馆藏品与借展机构的登记、查询。 */
@Service
public class CatalogService {

    private final ArtifactRepository artifactRepository;
    private final BorrowingInstitutionRepository institutionRepository;
    private final Clock clock;

    public CatalogService(ArtifactRepository artifactRepository,
                          BorrowingInstitutionRepository institutionRepository,
                          Clock clock) {
        this.artifactRepository = artifactRepository;
        this.institutionRepository = institutionRepository;
        this.clock = clock;
    }

    @Transactional
    public ArtifactResponse createArtifact(CreateArtifactRequest request) {
        if (request.minTemperature().compareTo(request.maxTemperature()) > 0) {
            throw new BadRequestException("允许最低温度不能高于允许最高温度");
        }
        if (request.minHumidity().compareTo(request.maxHumidity()) > 0) {
            throw new BadRequestException("允许最低湿度不能高于允许最高湿度");
        }
        Artifact artifact = new Artifact(request.catalogNo(), request.name(), request.loanable(),
                request.minTemperature(), request.maxTemperature(),
                request.minHumidity(), request.maxHumidity(),
                request.transportRisk(), Instant.now(clock));
        try {
            artifact = artifactRepository.saveAndFlush(artifact);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("藏品号已存在: " + request.catalogNo());
        }
        return toResponse(artifact);
    }

    @Transactional(readOnly = true)
    public ArtifactResponse getArtifact(String catalogNo) {
        return toResponse(artifactRepository.findByCatalogNo(catalogNo)
                .orElseThrow(() -> new NotFoundException("藏品不存在: " + catalogNo)));
    }

    @Transactional(readOnly = true)
    public List<ArtifactResponse> listArtifacts() {
        return artifactRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public InstitutionResponse createInstitution(CreateInstitutionRequest request) {
        BorrowingInstitution institution = new BorrowingInstitution(request.code(),
                request.name(), request.maxRisk(), Instant.now(clock));
        try {
            institution = institutionRepository.saveAndFlush(institution);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("机构编号已存在: " + request.code());
        }
        return toResponse(institution);
    }

    @Transactional(readOnly = true)
    public InstitutionResponse getInstitution(String code) {
        return toResponse(institutionRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("借展机构不存在: " + code)));
    }

    @Transactional(readOnly = true)
    public List<InstitutionResponse> listInstitutions() {
        return institutionRepository.findAll().stream().map(this::toResponse).toList();
    }

    private ArtifactResponse toResponse(Artifact a) {
        return new ArtifactResponse(a.getCatalogNo(), a.getName(), a.isLoanable(),
                a.getMinTemperature(), a.getMaxTemperature(),
                a.getMinHumidity(), a.getMaxHumidity(), a.getTransportRisk().name());
    }

    private InstitutionResponse toResponse(BorrowingInstitution i) {
        return new InstitutionResponse(i.getCode(), i.getName(), i.getMaxRisk().name());
    }
}
