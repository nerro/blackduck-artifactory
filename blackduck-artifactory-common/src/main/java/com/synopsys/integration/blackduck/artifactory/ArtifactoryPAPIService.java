package com.synopsys.integration.blackduck.artifactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.fs.FileLayoutInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.Repositories;
import org.artifactory.repo.RepositoryConfiguration;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.search.Searches;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ArtifactoryPAPIService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final Repositories repositories;
    private final Searches searches;

    public ArtifactoryPAPIService(final Repositories repositories, final Searches searches) {
        this.repositories = repositories;
        this.searches = searches;
    }

    public Optional<RepositoryConfiguration> getRepositoryConfiguration(final String repoKey) {
        return Optional.ofNullable(repositories.getRepositoryConfiguration(repoKey));
    }

    public Long getArtifactCount(final List<String> repoKeys) {
        return repoKeys.stream()
                   .map(RepoPathFactory::create)
                   .map(repositories::getArtifactsCount)
                   .mapToLong(Long::longValue)
                   .sum();
    }

    public ItemInfo getItemInfo(final RepoPath repoPath) {
        return repositories.getItemInfo(repoPath);
    }

    public boolean isValidRepository(final String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            logger.warn("A blank repo key is invalid");
            return false;
        }

        final RepoPath repoPath = RepoPathFactory.create(repoKey);
        final boolean isValid = repositories.exists(repoPath) && getRepositoryConfiguration(repoKey).isPresent();

        if (!isValid) {
            logger.warn(String.format("Repository '%s' was not found or is not a valid repository.", repoKey));
        }

        return isValid;
    }

    public List<RepoPath> searchForArtifactsByPatterns(final List<String> repoKeys, final List<String> patterns) {
        final List<RepoPath> repoPaths = new ArrayList<>();

        for (final String pattern : patterns) {
            final List<RepoPath> foundRepoPaths = searches.artifactsByName(pattern, repoKeys.toArray(new String[0]));
            if (!foundRepoPaths.isEmpty()) {
                repoPaths.addAll(foundRepoPaths);
                logger.debug(String.format("Found %d artifacts matching pattern [%s]", foundRepoPaths.size(), pattern));
            } else {
                logger.debug(String.format("No artifacts fund that match the pattern pattern [%s]", pattern));
            }
        }

        return repoPaths;
    }

    public FileLayoutInfo getLayoutInfo(final RepoPath repoPath) {
        return repositories.getLayoutInfo(repoPath);
    }

    public ResourceStreamHandle getContent(final RepoPath repoPath) {
        return repositories.getContent(repoPath);
    }

    public Properties getProperties(final RepoPath repoPath) {
        return repositories.getProperties(repoPath);
    }
}
