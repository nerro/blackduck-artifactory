package com.synopsys.integration.blackduck.artifactory.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.artifactory.modules.ModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.ModuleRegistry;
import com.synopsys.integration.builder.BuilderStatus;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class StatusCheckService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
    private final int LINE_CHARACTER_LIMIT = 100;
    private final String LINE_SEPARATOR = System.lineSeparator();
    private final String BLOCK_SEPARATOR = LINE_SEPARATOR + StringUtils.repeat("-", LINE_CHARACTER_LIMIT) + LINE_SEPARATOR;

    private final ModuleRegistry moduleRegistry;
    private final PluginConfig pluginConfig;
    private final File versionFile;

    public StatusCheckService(final ModuleRegistry moduleRegistry, final PluginConfig pluginConfig, final File versionFile) {
        this.moduleRegistry = moduleRegistry;
        this.pluginConfig = pluginConfig;
        this.versionFile = versionFile;
    }

    public String generateStatusCheckMessage() {
        final String pluginVersion = getPluginVersion();
        final StringBuilder statusCheckMessage = new StringBuilder(BLOCK_SEPARATOR + String.format("Status Check: Plugin Version - %s", pluginVersion) + BLOCK_SEPARATOR);

        final BuilderStatus generalBuilderStatus = new BuilderStatus();
        final ConfigValidationReport configValidationReport = new ConfigValidationReport(generalBuilderStatus);
        pluginConfig.validate(configValidationReport);

        final String configErrorMessage = configValidationReport.hasError() ? "CONFIGURATION ERROR" : "";
        statusCheckMessage.append(String.format("General Settings: %s", configErrorMessage)).append(LINE_SEPARATOR);
        appendPropertyReport(configValidationReport, statusCheckMessage);
        statusCheckMessage.append(BLOCK_SEPARATOR);

        for (final ModuleConfig moduleConfig : moduleRegistry.getAllModuleConfigs()) {
            appendConfigStatusReport(statusCheckMessage, moduleConfig);
            statusCheckMessage.append(BLOCK_SEPARATOR);
        }

        return statusCheckMessage.toString();
    }

    private void appendConfigStatusReport(final StringBuilder statusCheckMessage, final ModuleConfig moduleConfig) {
        final BuilderStatus builderStatus = new BuilderStatus();
        final ConfigValidationReport configValidationReport = new ConfigValidationReport(builderStatus);
        moduleConfig.validate(configValidationReport);

        final String moduleName = moduleConfig.getModuleName();
        final String state = moduleConfig.isEnabled() ? "Enabled" : "Disabled";
        final String configErrorMessage = configValidationReport.hasError() ? "CONFIGURATION ERROR" : "";
        final String moduleLine = String.format("%s [%s] %s", moduleName, state, configErrorMessage);
        statusCheckMessage.append(moduleLine).append(LINE_SEPARATOR);

        appendPropertyReport(configValidationReport, statusCheckMessage);
    }

    private void appendPropertyReport(final ConfigValidationReport configValidationReport, final StringBuilder statusCheckMessage) {
        for (final PropertyValidationReport propertyReport : configValidationReport.getPropertyReports()) {
            final Optional<String> errorMessage = propertyReport.getErrorMessage();

            final String mark = errorMessage.isPresent() ? "X" : "✔";
            final String property = propertyReport.getConfigurationProperty().getKey();
            final String reportSuffix = errorMessage.isPresent() ? String.format(LINE_SEPARATOR + "      * %s", errorMessage.get()) : "";
            final String reportLine = String.format("[%s] - %s %s", mark, property, reportSuffix);
            statusCheckMessage.append(wrapLine(reportLine)).append(LINE_SEPARATOR);
        }

        if (!configValidationReport.getBuilderStatus().isValid()) {
            final String otherMessages = wrapLine("Other Messages: " + configValidationReport.getBuilderStatus().getFullErrorMessage());
            statusCheckMessage.append(otherMessages).append(LINE_SEPARATOR);
        }
    }

    private String wrapLine(final String line) {
        return WordUtils.wrap(line, LINE_CHARACTER_LIMIT, LINE_SEPARATOR + "        ", true);
    }

    private String getPluginVersion() {
        String version = "Unknown";
        try {
            version = FileUtils.readFileToString(versionFile, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return version;
    }
}
