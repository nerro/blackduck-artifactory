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
package com.synopsys.integration.blackduck.artifactory.modules.mock;

import org.junit.platform.commons.util.StringUtils;

import com.synopsys.integration.blackduck.artifactory.configuration.ConfigurationProperty;
import com.synopsys.integration.blackduck.artifactory.configuration.model.PropertyGroupReport;
import com.synopsys.integration.blackduck.artifactory.modules.ModuleConfig;

public class MockModuleConfig extends ModuleConfig {
    private final ConfigurationProperty configurationProperty;
    private final String testField;

    public MockModuleConfig(final String moduleName, final Boolean enabled, final ConfigurationProperty configurationProperty, final String testField) {
        super(moduleName, enabled);
        this.configurationProperty = configurationProperty;
        this.testField = testField;
    }

    @Override
    public void validate(final PropertyGroupReport propertyGroupReport) {
        if (StringUtils.isBlank(testField)) {
            propertyGroupReport.addErrorMessage(configurationProperty, "testField is blank");
        }
    }
}
