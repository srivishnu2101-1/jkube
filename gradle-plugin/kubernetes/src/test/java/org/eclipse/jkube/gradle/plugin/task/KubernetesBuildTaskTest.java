/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.gradle.plugin.task;

import java.io.IOException;
import java.util.Collections;

import org.eclipse.jkube.gradle.plugin.KubernetesExtension;
import org.eclipse.jkube.gradle.plugin.TestKubernetesExtension;
import org.eclipse.jkube.kit.build.service.docker.DockerAccessFactory;
import org.eclipse.jkube.kit.build.service.docker.access.DockerAccess;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.BuildConfiguration;
import org.eclipse.jkube.kit.config.service.JKubeServiceException;
import org.eclipse.jkube.kit.config.service.kubernetes.DockerBuildService;

import org.gradle.api.GradleException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KubernetesBuildTaskTest {

  @Rule
  public TaskEnvironment taskEnvironment = new TaskEnvironment();

  private MockedConstruction<DockerAccessFactory> dockerAccessFactoryMockedConstruction;
  private MockedConstruction<DockerBuildService> dockerBuildServiceMockedConstruction;
  private KubernetesExtension extension;
  private boolean isBuildServiceApplicable;
  private boolean isBuildError;

  @Before
  public void setUp() throws IOException {
    // Mock required for environments with no DOCKER available (don't remove)
    dockerAccessFactoryMockedConstruction = mockConstruction(DockerAccessFactory.class,
        (mock, ctx) -> when(mock.createDockerAccess(any())).thenReturn(mock(DockerAccess.class)));
    dockerBuildServiceMockedConstruction = mockConstruction(DockerBuildService.class, (mock, ctx) -> {
      when(mock.isApplicable()).thenReturn(isBuildServiceApplicable);
      if (isBuildError) {
        doThrow(new JKubeServiceException("Exception during Build")).when(mock).build(any());
      }
    });
    isBuildServiceApplicable = true;
    isBuildError = false;
    extension = new TestKubernetesExtension();
    when(taskEnvironment.project.getExtensions().getByType(KubernetesExtension.class)).thenReturn(extension);
    extension.images = Collections.singletonList(ImageConfiguration.builder()
        .name("foo/bar:latest")
        .build(BuildConfiguration.builder()
            .dockerFile("Dockerfile")
            .build())
        .build());
  }

  @After
  public void tearDown() {
    dockerBuildServiceMockedConstruction.close();
    dockerAccessFactoryMockedConstruction.close();
  }

  @Test
  public void runTask_withImageConfiguration_shouldRunBuild() throws JKubeServiceException {
    // Given
    final KubernetesBuildTask buildTask = new KubernetesBuildTask(KubernetesExtension.class);
    // When
    buildTask.runTask();
    // Then
    assertThat(buildTask.jKubeServiceHub.getBuildService()).isNotNull()
        .isInstanceOf(DockerBuildService.class);
    verify(buildTask.jKubeServiceHub.getBuildService(), times(1)).build(any());
  }

  @Test
  public void runTask_withEmptyImageConfigurations_shouldNotRunBuild() throws JKubeServiceException {
    // Given
    extension.images = Collections.emptyList();
    final KubernetesBuildTask buildTask = new KubernetesBuildTask(KubernetesExtension.class);
    // When
    buildTask.runTask();
    // Then
    verify(buildTask.jKubeServiceHub.getBuildService(), times(0)).build(any());
  }

  @Test
  public void runTask_withImageConfigurationAndNoApplicableService_shouldThrowException() {
    // Given
    isBuildServiceApplicable = false;
    final KubernetesBuildTask buildTask = new KubernetesBuildTask(KubernetesExtension.class);
    // When
    final IllegalStateException result = assertThrows(IllegalStateException.class, buildTask::runTask);
    // Then
    assertThat(result)
        .hasMessage("No suitable Build Service was found for your current configuration");
  }

  @Test
  public void runTask_withImageConfigurationAndBuildError_shouldThrowException() {
    // Given
    isBuildError = true;
    final KubernetesBuildTask buildTask = new KubernetesBuildTask(KubernetesExtension.class);
    // When
    final GradleException result = assertThrows(GradleException.class, buildTask::runTask);
    // Then
    assertThat(result)
        .hasMessage("Exception during Build");
  }
}
