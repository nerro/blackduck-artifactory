package com.synopsys.integration.blackduck.artifactory.modules.inspection.externalid;

import java.util.Optional;

import org.artifactory.repo.RepoPath;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.bdio.model.externalid.ExternalId;
import com.synopsys.integration.blackduck.artifactory.ArtifactoryPAPIService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.externalid.composer.ComposerExternalIdExtractor;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.SupportedPackageType;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ExternalIdService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final ArtifactoryPAPIService artifactoryPAPIService;
    private final BlackDuckPropertiesExternalIdExtractor blackDuckPropertiesExternalIdFactory;
    private final ArtifactoryInfoExternalIdExtractor artifactoryInfoExternalIdExtractor;
    private final ComposerExternalIdExtractor composerExternalIdFactory;

    public ExternalIdService(final ArtifactoryPAPIService artifactoryPAPIService, final BlackDuckPropertiesExternalIdExtractor blackDuckPropertiesExternalIdFactory,
        final ArtifactoryInfoExternalIdExtractor artifactoryInfoExternalIdExtractor, final ComposerExternalIdExtractor composerExternalIdFactory) {
        this.artifactoryPAPIService = artifactoryPAPIService;
        this.blackDuckPropertiesExternalIdFactory = blackDuckPropertiesExternalIdFactory;
        this.artifactoryInfoExternalIdExtractor = artifactoryInfoExternalIdExtractor;
        this.composerExternalIdFactory = composerExternalIdFactory;
    }

    public Optional<ExternalId> extractExternalId(final RepoPath repoPath) {
        final String repoKey = repoPath.getRepoKey();
        final String packageType = artifactoryPAPIService.getPackageType(repoKey).orElse(null);
        final Optional<SupportedPackageType> supportedPackageTypeOptional = SupportedPackageType.getAsSupportedPackageType(packageType);

        ExternalId externalId = null;
        if (supportedPackageTypeOptional.isPresent()) {
            final SupportedPackageType supportedPackageType = supportedPackageTypeOptional.get();

            externalId = blackDuckPropertiesExternalIdFactory.extractExternalId(supportedPackageType, repoPath).orElse(null);

            if (supportedPackageType.equals(SupportedPackageType.COMPOSER)) {
                externalId = composerExternalIdFactory.extractExternalId(supportedPackageType, repoPath).orElse(null);
            }

            if (externalId == null) {
                externalId = artifactoryInfoExternalIdExtractor.extractExternalId(supportedPackageType, repoPath).orElse(null);
            }
        } else {
            logger.warn(String.format("Package type (%s) not supported", packageType));
        }

        return Optional.ofNullable(externalId);
    }
}
