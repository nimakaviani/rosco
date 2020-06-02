package com.netflix.spinnaker.rosco.manifests.cdk8s;

import static com.netflix.spinnaker.rosco.manifests.BakeManifestRequest.TemplateRenderer;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import groovy.util.logging.Slf4j;
import java.io.IOException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CDK8SBakeManifestService extends BakeManifestService<CDK8SBakeManifestRequest> {
  private final CDK8STemplateUtils cdk8sTemplateUtils;

  public CDK8SBakeManifestService(CDK8STemplateUtils cdk8sTemplateUtils, JobExecutor jobExecutor) {
    super(jobExecutor);
    this.cdk8sTemplateUtils = cdk8sTemplateUtils;
  }

  @Override
  public Class<CDK8SBakeManifestRequest> requestType() {
    return CDK8SBakeManifestRequest.class;
  }

  @Override
  public boolean handles(String type) {
    return TemplateRenderer.CDK8S.toString().equals(type);
  }

  @Override
  public Artifact bake(CDK8SBakeManifestRequest bakeManifestRequest) throws IOException {
    System.out.println(">> baking manifest");
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = cdk8sTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);

      String bakeResult = doBake(recipe);
      return Artifact.builder()
          .type("embedded/base64")
          .name(bakeManifestRequest.getOutputArtifactName())
          .reference(Base64.getEncoder().encodeToString(bakeResult.getBytes()))
          .build();
    }
  }
}
