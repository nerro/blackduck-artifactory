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
package com.synopsys.integration.blackduck.artifactory.modules.inspection.util;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.fs.FileLayoutInfo;
import org.artifactory.repo.RepoPath;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.bdio.model.Forge;
import com.synopsys.integration.bdio.model.externalid.ExternalId;
import com.synopsys.integration.bdio.model.externalid.ExternalIdFactory;
import com.synopsys.integration.blackduck.artifactory.BlackDuckArtifactoryProperty;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.ExternalIdProperties;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.SupportedPackageType;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.service.InspectionPropertyService;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ArtifactoryExternalIdFactory {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(ArtifactoryExternalIdFactory.class));

    private final InspectionPropertyService inspectionPropertyService;
    private final ExternalIdFactory externalIdFactory;

    public ArtifactoryExternalIdFactory(final InspectionPropertyService inspectionPropertyService, final ExternalIdFactory externalIdFactory) {
        this.inspectionPropertyService = inspectionPropertyService;
        this.externalIdFactory = externalIdFactory;
    }

    public Optional<ExternalId> createExternalId(final String packageType, final FileLayoutInfo fileLayoutInfo, final RepoPath repoPath, final org.artifactory.md.Properties properties) {
        final Optional<ExternalId> optionalExternalId;

        if (inspectionPropertyService.hasExternalIdProperties(repoPath)) {
            optionalExternalId = createExternalIdFromOriginIdProperties(repoPath);
        } else {
            optionalExternalId = createExternalIdFromArtifactoryMetadata(packageType, fileLayoutInfo, properties);
        }

        return optionalExternalId;
    }

    private Optional<ExternalId> createExternalIdFromOriginIdProperties(final RepoPath repoPath) {
        final ExternalIdProperties externalIdProperties = inspectionPropertyService.getExternalIdProperties(repoPath);

        if (externalIdProperties.getForge().isPresent() && externalIdProperties.getOriginId().isPresent()) {
            final Forge forge = Forge.getKnownForges().get(externalIdProperties.getForge().get());
            if (forge == null) {
                logger.debug(String.format("Failed to extract forge from property %s.", BlackDuckArtifactoryProperty.BLACKDUCK_FORGE.getName()));
                return Optional.empty();
            }

            final String originId = externalIdProperties.getOriginId().get();
            final String[] originIdPieces = originId.split(forge.getKbSeparator());
            ExternalId externalId = null;
            if (originIdPieces.length == 2) {
                externalId = externalIdFactory.createNameVersionExternalId(forge, originIdPieces[0], originIdPieces[1]);
            } else if (originIdPieces.length == 3 && forge.equals(Forge.MAVEN)) {
                externalId = externalIdFactory.createMavenExternalId(originIdPieces[0], originIdPieces[1], originIdPieces[2]);
            } else {
                logger.debug(String.format("Invalid forge or origin id on artifact '%s'", repoPath.getPath()));
            }

            return Optional.ofNullable(externalId);
        } else {
            logger.debug(String.format("Unable to generate an external id from properties on artifact '%s'", repoPath.getPath()));
            return Optional.empty();
        }
    }

    private Optional<ExternalId> createExternalIdFromArtifactoryMetadata(final String packageType, final FileLayoutInfo fileLayoutInfo, final org.artifactory.md.Properties properties) {
        Optional<ExternalId> optionalExternalId = Optional.empty();

        try {
            final Optional<SupportedPackageType> possiblySupportedPackageType = SupportedPackageType.getAsSupportedPackageType(packageType);
            if (possiblySupportedPackageType.isPresent()) {
                final SupportedPackageType supportedPackageType = possiblySupportedPackageType.get();
                final Forge forge = supportedPackageType.getForge();

                if (supportedPackageType.hasNameVersionProperties()) {
                    final String nameProperty = supportedPackageType.getArtifactoryNameProperty();
                    final String versionProperty = supportedPackageType.getArtifactoryVersionProperty();
                    optionalExternalId = createNameVersionExternalIdFromProperties(forge, properties, nameProperty, versionProperty);
                }

                if (!optionalExternalId.isPresent()) {
                    optionalExternalId = createExternalIdFromFileLayoutInfo(forge, fileLayoutInfo);
                }
            } else {
                logger.warn(String.format("Package type (%s) not supported", packageType));
            }
        } catch (final Exception e) {
            logger.error("Could not resolve the item to a dependency:", e);
        }

        return optionalExternalId;
    }

    private Optional<ExternalId> createExternalIdFromFileLayoutInfo(final Forge forge, final FileLayoutInfo fileLayoutInfo) {
        final String name = fileLayoutInfo.getModule();
        final String version = fileLayoutInfo.getBaseRevision();
        if (forge.equals(Forge.MAVEN)) {
            final String group = fileLayoutInfo.getOrganization();
            return createMavenExternalId(group, name, version);
        }
        return createNameVersionExternalId(forge, name, version);
    }

    private Optional<ExternalId> createNameVersionExternalIdFromProperties(final Forge forge, final org.artifactory.md.Properties properties, final String namePropertyName, final String versionPropertyName) {
        final String name = properties.getFirst(namePropertyName);
        final String version = properties.getFirst(versionPropertyName);
        return createNameVersionExternalId(forge, name, version);
    }

    private Optional<ExternalId> createNameVersionExternalId(final Forge forge, final String name, final String version) {
        ExternalId externalId = null;
        if (StringUtils.isNoneBlank(name, version)) {
            externalId = externalIdFactory.createNameVersionExternalId(forge, name, version);
        }
        return Optional.ofNullable(externalId);
    }

    private Optional<ExternalId> createMavenExternalId(final String group, final String name, final String version) {
        ExternalId externalId = null;
        if (StringUtils.isNoneBlank(group, name, version)) {
            externalId = externalIdFactory.createMavenExternalId(group, name, version);
        }
        return Optional.ofNullable(externalId);
    }

}
