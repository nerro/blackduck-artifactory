/**
 * blackduck-artifactory-common
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
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
package com.synopsys.integration.blackduck.artifactory.configuration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.synopsys.integration.blackduck.artifactory.util.FileIO;
import com.synopsys.integration.blackduck.artifactory.util.TestUtil;

class ConfigurationPropertyManagerTest {
    private final ConfigurationProperty timeoutProperty = () -> "blackduck.timeout";
    private final ConfigurationProperty scanNamePatternsProperty = () -> "blackduck.artifactory.scan.name.patterns";
    private final ConfigurationProperty repositoryKeyListProperty = () -> "blackduck.artifactory.scan.repos";
    private final ConfigurationProperty repositoryKeyCsvProperty = () -> "blackduck.artifactory.scan.repos.csv.path";
    private final ConfigurationProperty isEnabledProperty = () -> "blackduck.artifactory.scan.enabled";
    private ConfigurationPropertyManager configurationPropertyManager;
    private Properties properties;

    @BeforeEach
    void init() throws IOException {
        properties = TestUtil.getDefaultProperties();
        configurationPropertyManager = new ConfigurationPropertyManager(properties);
    }

    @Test
    void getRepositoryKeysFromProperties() throws IOException {
        final List<String> repositoryKeysFromProperties = configurationPropertyManager.getRepositoryKeysFromProperties(repositoryKeyListProperty, repositoryKeyCsvProperty);
        assertAll("repo keys",
            () -> assertEquals(2, repositoryKeysFromProperties.size()),
            () -> assertTrue(repositoryKeysFromProperties.contains("ext-release-local")),
            () -> assertTrue(repositoryKeysFromProperties.contains("libs-release"))
        );

    }

    @Test
    @FileIO
    void getRepositoryKeysFromPropertiesCsv() throws IOException {
        configurationPropertyManager.getProperties().setProperty(repositoryKeyCsvProperty.getKey(), TestUtil.getResourceAsFilePath("/repoCSV"));
        final List<String> repositoryKeysFromProperties = configurationPropertyManager.getRepositoryKeysFromProperties(repositoryKeyListProperty, repositoryKeyCsvProperty);

        assertAll("repo keys",
            () -> assertEquals(7, repositoryKeysFromProperties.size()),
            () -> assertEquals("[test-repo1, test-repo2,  test-repo3, test-repo4 , test-repo5 ,  test-repo6, test-repo7]", Arrays.toString(repositoryKeysFromProperties.toArray()))
        );
    }

    @Test
    void getProperties() {
        assertEquals(properties, configurationPropertyManager.getProperties());
    }

    @Test
    void getProperty() {
        assertEquals("ext-release-local,libs-release", configurationPropertyManager.getProperty(repositoryKeyListProperty));
    }

    @Test
    void getBooleanProperty() {
        assertTrue(configurationPropertyManager.getBooleanProperty(isEnabledProperty));
    }

    @Test
    void getIntegerProperty() {
        // TODO: Look into why assertEquals cannot take integer values
        final Integer expectedTimeout = 120;
        final Integer actualTimeout = configurationPropertyManager.getIntegerProperty(timeoutProperty);
        assertEquals(String.valueOf(expectedTimeout), String.valueOf(actualTimeout));
    }

    @Test
    void getPropertyAsList() {
        assertArrayEquals("*.war,*.zip,*.tar.gz,*.hpi".split(","), configurationPropertyManager.getPropertyAsList(scanNamePatternsProperty).toArray());
    }
}