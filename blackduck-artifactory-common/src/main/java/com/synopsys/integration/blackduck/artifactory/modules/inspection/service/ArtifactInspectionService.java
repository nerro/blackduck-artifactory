/**
 * blackduck-artifactory-common
 *
 * Copyright (c) 2019 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.blackduck.artifactory.modules.inspection.service;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.bdio.model.externalid.ExternalId;
import com.synopsys.integration.blackduck.api.generated.view.ProjectVersionView;
import com.synopsys.integration.blackduck.artifactory.ArtifactoryPAPIService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.InspectionModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.exception.FailedInspectionException;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.externalid.ExternalIdService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.Artifact;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.ComponentViewWrapper;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.InspectionStatus;
import com.synopsys.integration.blackduck.service.ProjectService;
import com.synopsys.integration.blackduck.service.model.ProjectVersionWrapper;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ArtifactInspectionService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final ArtifactoryPAPIService artifactoryPAPIService;
    private final BlackDuckBOMService blackDuckBOMService;
    private final MetaDataPopulationService metaDataPopulationService;
    private final InspectionModuleConfig inspectionModuleConfig;
    private final InspectionPropertyService inspectionPropertyService;
    private final ProjectService projectService;
    private final ExternalIdService externalIdService;

    public ArtifactInspectionService(final ArtifactoryPAPIService artifactoryPAPIService, final BlackDuckBOMService blackDuckBOMService, final MetaDataPopulationService metaDataPopulationService,
        final InspectionModuleConfig inspectionModuleConfig, final InspectionPropertyService inspectionPropertyService, final ProjectService projectService, final ExternalIdService externalIdService) {
        this.artifactoryPAPIService = artifactoryPAPIService;
        this.blackDuckBOMService = blackDuckBOMService;
        this.metaDataPopulationService = metaDataPopulationService;
        this.inspectionModuleConfig = inspectionModuleConfig;
        this.inspectionPropertyService = inspectionPropertyService;
        this.projectService = projectService;
        this.externalIdService = externalIdService;
    }

    public boolean shouldInspectArtifact(final RepoPath repoPath) {
        if (!inspectionModuleConfig.getRepos().contains(repoPath.getRepoKey())) {
            return false;
        }

        final ItemInfo itemInfo = artifactoryPAPIService.getItemInfo(repoPath);
        final Optional<List<String>> patterns = artifactoryPAPIService.getPackageType(repoPath.getRepoKey())
                                                    .map(inspectionModuleConfig::getPatternsForPackageType);

        if (!patterns.isPresent() || patterns.get().isEmpty() || itemInfo.isFolder()) {
            return false;
        }

        final File artifact = new File(itemInfo.getName());
        final WildcardFileFilter wildcardFileFilter = new WildcardFileFilter(patterns.get());

        return wildcardFileFilter.accept(artifact);
    }

    public Artifact identifyAndMarkArtifact(final RepoPath repoPath) {
        final Artifact artifact = identifyArtifact(repoPath);
        try {
            metaDataPopulationService.populateExternalIdMetadata(artifact);
        } catch (final FailedInspectionException e) {
            inspectionPropertyService.failInspection(e);
        }
        return artifact;
    }

    private Artifact identifyArtifact(final RepoPath repoPath) {
        final ExternalId externalId = externalIdService.extractExternalId(repoPath).orElse(null);
        return new Artifact(repoPath, externalId);
    }

    public void inspectDelta(final String repoKey) {
        final RepoPath repoKeyPath = RepoPathFactory.create(repoKey);
        try {
            inspectDelta(repoKeyPath);
        } catch (final IntegrationException e) {
            logger.error(String.format("An error occurred when inspecting '%s'.", repoKey));
            inspectionPropertyService.failInspection(repoKeyPath, e.getMessage());
        }
    }

    private void inspectDelta(final RepoPath repoKeyPath) throws FailedInspectionException {
        final String repoKey = repoKeyPath.getRepoKey();

        if (!inspectionPropertyService.assertInspectionStatus(repoKeyPath, InspectionStatus.SUCCESS)) {
            // Only inspect a delta if the repository has been successfully initialized.
            return;
        }

        final Optional<String> packageType = artifactoryPAPIService.getPackageType(repoKey);
        if (!packageType.isPresent()) {
            final String message = String.format("The repository '%s' has no package type. Inspection cannot be performed.", repoKey);
            logger.error(message);
            throw new FailedInspectionException(repoKeyPath, message);
        }

        final List<String> patterns = inspectionModuleConfig.getPatternsForPackageType(packageType.get());
        if (patterns.isEmpty()) {
            // If we don't verify that patterns is not empty, artifactory will grab every artifact in the repo.
            logger.warn(String.format("The repository '%s' has a package type of '%s' which either isn't supported or has no patterns configured for it.", repoKey, packageType.get()));
            throw new FailedInspectionException(repoKeyPath, "Repo has an unsupported package type or not patterns configured for it.");
        }

        final ProjectVersionView projectVersionView;
        try {
            final String projectName = inspectionPropertyService.getRepoProjectName(repoKey);
            final String projectVersionName = inspectionPropertyService.getRepoProjectVersionName(repoKey);
            final Optional<ProjectVersionWrapper> projectVersionWrapperOptional = projectService.getProjectVersion(projectName, projectVersionName);

            if (projectVersionWrapperOptional.isPresent()) {
                projectVersionView = projectVersionWrapperOptional.get().getProjectVersionView();
            } else {
                throw new IntegrationException(String.format("Project '%s' and version '%s' could not be found.", projectName, projectVersionName));
            }
        } catch (final IntegrationException e) {
            logger.error("An error occurred when attempting to get the project version from Black Duck.", e);
            throw new FailedInspectionException(repoKeyPath, String.format("Failed to get project version from Black Duck: %s", e.getMessage()));
        }

        final List<Artifact> artifacts = artifactoryPAPIService.searchForArtifactsByPatterns(Collections.singletonList(repoKey), patterns).stream()
                                             .filter(this::isArtifactPendingOrShouldRetry)
                                             .map(this::identifyArtifact)
                                             .collect(Collectors.toList());

        for (final Artifact artifact : artifacts) {
            try {
                if (!inspectionPropertyService.hasExternalIdProperties(artifact.getRepoPath())) {
                    // We don't want to try this step if it already succeeded because it will reset the retry count.
                    metaDataPopulationService.populateExternalIdMetadata(artifact.getRepoPath(), artifact.getExternalId().orElse(null));
                }
                final ComponentViewWrapper componentViewWrapper = blackDuckBOMService.addArtifactToProjectVersion(artifact, projectVersionView);
                metaDataPopulationService.populateBlackDuckMetadata(artifact.getRepoPath(), componentViewWrapper.getComponentVersionView(), componentViewWrapper.getVersionBomComponentView());
            } catch (final IntegrationException e) {
                inspectionPropertyService.failInspection(artifact.getRepoPath(), String.format("Failed to find component: %s", e.getMessage()));
            }
        }
    }

    private boolean isArtifactPendingOrShouldRetry(final RepoPath repoPath) {
        return inspectionPropertyService.assertInspectionStatus(repoPath, InspectionStatus.PENDING) || inspectionPropertyService.shouldRetryInspection(repoPath);
    }
}
