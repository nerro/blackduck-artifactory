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
package com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.api.generated.discovery.ApiDiscovery;
import com.synopsys.integration.blackduck.api.generated.view.ComponentVersionView;
import com.synopsys.integration.blackduck.api.generated.view.OriginView;
import com.synopsys.integration.blackduck.api.generated.view.UserView;
import com.synopsys.integration.blackduck.api.manual.view.NotificationUserView;
import com.synopsys.integration.blackduck.artifactory.ArtifactSearchService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model.AffectedArtifact;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model.BlackDuckNotification;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model.PolicyStatusNotification;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model.PolicyVulnerabilityAggregate;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model.VulnerabilityAggregate;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model.VulnerabilityNotification;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.service.InspectionPropertyService;
import com.synopsys.integration.blackduck.service.BlackDuckService;
import com.synopsys.integration.blackduck.service.NotificationService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.util.NameVersion;

public class ArtifactNotificationService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final NotificationRetrievalService notificationRetrievalService;
    private final BlackDuckService blackDuckService;
    private final NotificationService notificationService;
    private final ArtifactSearchService artifactSearchService;
    private final InspectionPropertyService inspectionPropertyService;

    public ArtifactNotificationService(final NotificationRetrievalService notificationRetrievalService, final BlackDuckService blackDuckService, final NotificationService notificationService,
        final ArtifactSearchService artifactSearchService,
        final InspectionPropertyService inspectionPropertyService) {
        this.notificationRetrievalService = notificationRetrievalService;
        this.blackDuckService = blackDuckService;
        this.notificationService = notificationService;
        this.artifactSearchService = artifactSearchService;
        this.inspectionPropertyService = inspectionPropertyService;
    }

    public Optional<Date> updateMetadataFromNotifications(final List<String> repoKeys, final Date startDate, final Date endDate) throws IntegrationException {
        final UserView currentUser = blackDuckService.getResponse(ApiDiscovery.CURRENT_USER_LINK_RESPONSE);
        final List<NotificationUserView> notificationUserViews = notificationService.getAllUserNotifications(currentUser, startDate, endDate);
        final Map<String, PolicyVulnerabilityAggregate.Builder> artifactMetadataAggregateMap = new HashMap<>();
        final List<VulnerabilityNotification> vulnerabilityNotifications = notificationRetrievalService.getVulnerabilityNotifications(notificationUserViews);
        final List<PolicyStatusNotification> policyStatusNotifications = notificationRetrievalService.getPolicyStatusNotifications(notificationUserViews);

        processVulnerabilityNotifications(repoKeys, vulnerabilityNotifications, artifactMetadataAggregateMap);
        processPolicyStatusNotifications(repoKeys, policyStatusNotifications, artifactMetadataAggregateMap);

        for (final Map.Entry<String, PolicyVulnerabilityAggregate.Builder> entry : artifactMetadataAggregateMap.entrySet()) {
            final RepoPath repoPath = RepoPathFactory.create(entry.getKey());
            final PolicyVulnerabilityAggregate policyVulnerabilityAggregate = entry.getValue().build();

            inspectionPropertyService.setPolicyAndVulnerabilityProperties(repoPath, policyVulnerabilityAggregate);
        }

        return getLatestNotificationCreatedAtDate(notificationUserViews);
    }

    private Optional<Date> getLatestNotificationCreatedAtDate(final List<NotificationUserView> notificationUserViews) {
        return notificationUserViews.stream()
                   .max(Comparator.comparing(NotificationUserView::getCreatedAt))
                   .map(NotificationUserView::getCreatedAt);
    }

    private void processVulnerabilityNotifications(final List<String> repoKeys, final List<VulnerabilityNotification> vulnerabilityNotifications, final Map<String, PolicyVulnerabilityAggregate.Builder> artifactMetadataAggregateMap) {
        final List<AffectedArtifact<VulnerabilityNotification>> affectedArtifacts = vulnerabilityNotifications.stream()
                                                                                        .map(notification -> findAffectedArtifacts(repoKeys, notification))
                                                                                        .flatMap(List::stream)
                                                                                        .collect(Collectors.toList());

        for (final AffectedArtifact<VulnerabilityNotification> affectedArtifact : affectedArtifacts) {
            final VulnerabilityNotification vulnerabilityNotification = affectedArtifact.getBlackDuckNotification();
            final VulnerabilityAggregate vulnerabilityAggregate = vulnerabilityNotification.getVulnerabilityAggregate();
            final Optional<String> href = affectedArtifact.getBlackDuckNotification().getComponentVersionView().getHref();
            final PolicyVulnerabilityAggregate.Builder builder = getPolicyVulnerabilityAggregateBuilder(affectedArtifact, artifactMetadataAggregateMap);
            builder.setVulnerabilityAggregate(vulnerabilityAggregate);
            builder.setComponentVersionUrl(href.orElse(null));
        }
    }

    private void processPolicyStatusNotifications(final List<String> repoKeys, final List<PolicyStatusNotification> policyStatusNotifications, final Map<String, PolicyVulnerabilityAggregate.Builder> artifactMetadataAggregateMap) {
        final List<AffectedArtifact<PolicyStatusNotification>> affectedArtifacts = policyStatusNotifications.stream()
                                                                                       .map(notification -> findAffectedArtifacts(repoKeys, notification))
                                                                                       .flatMap(List::stream)
                                                                                       .collect(Collectors.toList());

        for (final AffectedArtifact<PolicyStatusNotification> affectedArtifact : affectedArtifacts) {
            final PolicyStatusNotification policyStatusNotification = affectedArtifact.getBlackDuckNotification();
            final PolicyVulnerabilityAggregate.Builder builder = getPolicyVulnerabilityAggregateBuilder(affectedArtifact, artifactMetadataAggregateMap);

            builder.setPolicySummaryStatusType(policyStatusNotification.getPolicyStatusView().getApprovalStatus());
            builder.setComponentVersionUrl(policyStatusNotification.getComponentVersionView().getHref().orElse(null));
        }
    }

    private PolicyVulnerabilityAggregate.Builder getPolicyVulnerabilityAggregateBuilder(final AffectedArtifact affectedArtifact, final Map<String, PolicyVulnerabilityAggregate.Builder> artifactMetadataAggregateMap) {
        final String key = affectedArtifact.getRepoPath().getId().replaceFirst(":", "/");
        final PolicyVulnerabilityAggregate.Builder builder;
        if (artifactMetadataAggregateMap.containsKey(key)) {
            builder = artifactMetadataAggregateMap.get(key);
        } else {
            builder = new PolicyVulnerabilityAggregate.Builder();
            artifactMetadataAggregateMap.put(key, builder);
        }

        return builder;
    }

    private <T extends BlackDuckNotification> List<AffectedArtifact<T>> findAffectedArtifacts(final List<String> repoKeys, final T notification) {
        final List<AffectedArtifact<T>> affectedArtifacts = new ArrayList<>();

        try {
            final List<NameVersion> affectedProjectVersions = notification.getAffectedProjectVersions();
            final String[] affectedRepoKeys = determineAffectedRepos(repoKeys, affectedProjectVersions).toArray(new String[0]);
            final ComponentVersionView componentVersionView = notification.getComponentVersionView();
            final List<OriginView> originViews = blackDuckService.getAllResponses(componentVersionView, ComponentVersionView.ORIGINS_LINK_RESPONSE);

            for (final OriginView originView : originViews) {
                final String forge = originView.getOriginName();
                final String originId = originView.getOriginId();
                final List<AffectedArtifact<T>> artifactsWithOriginId = artifactSearchService.findArtifactsWithOriginId(forge, originId, affectedRepoKeys).stream()
                                                                            .map(repoPath -> new AffectedArtifact<>(repoPath, notification))
                                                                            .collect(Collectors.toList());
                affectedArtifacts.addAll(artifactsWithOriginId);
            }

            return affectedArtifacts;
        } catch (final IntegrationException e) {
            logger.error(String.format("Failed to get origins for: %s", notification.getComponentVersionView().getHref().orElse("Unknown")), e);
        }

        return affectedArtifacts;
    }

    private List<String> determineAffectedRepos(final List<String> repoKeys, final List<NameVersion> affectedProjectVersions) {
        final List<String> affectedRepos = new ArrayList<>();
        final Map<String, String> nameVersionToRepoKeyMap = projectNameVersionToRepoKey(repoKeys);
        for (final NameVersion nameVersion : affectedProjectVersions) {
            final String projectName = nameVersion.getName();
            final String projectVersionName = nameVersion.getVersion();
            final String projectNameVersionKey = generateProjectNameKey(projectName, projectVersionName);
            final String repoKey = nameVersionToRepoKeyMap.get(projectNameVersionKey);
            affectedRepos.add(repoKey);
        }

        return affectedRepos;
    }

    private Map<String, String> projectNameVersionToRepoKey(final List<String> repoKeys) {
        final Map<String, String> nameVersionToRepoKeyMap = new HashMap<>();
        for (final String repoKey : repoKeys) {
            final String repoProjectName = inspectionPropertyService.getRepoProjectName(repoKey);
            final String repoProjectVersionName = inspectionPropertyService.getRepoProjectVersionName(repoKey);
            final String key = generateProjectNameKey(repoProjectName, repoProjectVersionName);
            nameVersionToRepoKeyMap.put(key, repoKey);
        }

        return nameVersionToRepoKeyMap;
    }

    private String generateProjectNameKey(final String projectName, final String projectVersionName) {
        return projectName + ":" + projectVersionName;
    }
}
