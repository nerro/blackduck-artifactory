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
package com.synopsys.integration.blackduck.artifactory.modules.inspection.notifications.model;

import com.synopsys.integration.util.Stringable;

public class ArtifactMetaData extends Stringable {
    private final String forge;
    private final String originId;
    private final PolicyVulnerabilityAggregate policyVulnerabilityAggregate;

    public ArtifactMetaData(final String forge, final String originId, final PolicyVulnerabilityAggregate policyVulnerabilityAggregate) {
        this.forge = forge;
        this.originId = originId;
        this.policyVulnerabilityAggregate = policyVulnerabilityAggregate;
    }

    public String getForge() {
        return forge;
    }

    public String getOriginId() {
        return originId;
    }

    public PolicyVulnerabilityAggregate getPolicyVulnerabilityAggregate() {
        return policyVulnerabilityAggregate;
    }
}
