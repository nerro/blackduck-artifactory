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

import java.util.List;

import com.synopsys.integration.blackduck.api.generated.view.ComponentVersionView;
import com.synopsys.integration.blackduck.api.generated.view.PolicyStatusView;
import com.synopsys.integration.util.NameVersion;

public class PolicyStatusNotification extends BlackDuckNotification {
    private final PolicyStatusView policyStatusView;

    public PolicyStatusNotification(final List<NameVersion> affectedProjectVersions, final ComponentVersionView componentVersionView, final PolicyStatusView policyStatusView) {
        super(affectedProjectVersions, componentVersionView);
        this.policyStatusView = policyStatusView;
    }

    public PolicyStatusView getPolicyStatusView() {
        return policyStatusView;
    }
}
