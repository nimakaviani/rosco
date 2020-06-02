package com.netflix.spinnaker.rosco.manifests.cdk8s;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CDK8SBakeManifestRequest extends BakeManifestRequest {
  private Artifact inputArtifact;
}
