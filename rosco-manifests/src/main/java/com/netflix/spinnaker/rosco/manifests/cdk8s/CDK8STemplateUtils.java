/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.rosco.manifests.cdk8s;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CDK8STemplateUtils {
  private final ArtifactDownloader artifactDownloader;

  public CDK8STemplateUtils(ArtifactDownloader artifactDownloader) {
    this.artifactDownloader = artifactDownloader;
  }

  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, CDK8SBakeManifestRequest request)
      throws IOException {
    BakeRecipe result = new BakeRecipe();
    System.out.println(request.toString());
    result.setName(request.getOutputName());
    Artifact artifact = request.getInputArtifact();
    if (artifact == null) {
      throw new IllegalArgumentException("Exactly one input artifact must be provided to bake.");
    }

    String artifactType = Optional.of(artifact.getType()).orElse("");
    if ("git/repo".equals(artifactType)) {
      return buildBakeRecipeFromGitRepo(env, request, artifact);
    } else {
      throw new IllegalArgumentException("not the right repo");
    }
  }

  private BakeRecipe buildBakeRecipeFromGitRepo(
      BakeManifestEnvironment env, CDK8SBakeManifestRequest request, Artifact artifact)
      throws IOException {
    // This is a redundant check for now, but it's here for when we soon remove the old logic of
    // building from a github/file artifact type and instead, only support the git/repo artifact
    // type
    if (!"git/repo".equals(artifact.getType())) {
      throw new IllegalArgumentException("The inputArtifact should be of type \"git/repo\".");
    }

    InputStream inputStream;
    try {
      inputStream = artifactDownloader.downloadArtifact(artifact);
    } catch (IOException e) {
      throw new IOException("Failed to download git/repo artifact: " + e.getMessage(), e);
    }

    Path outputPath = env.resolvePath("");
    try {
      extractArtifact(inputStream, outputPath);
    } catch (IOException e) {
      throw new IOException("Failed to extract git/repo artifact: " + e.getMessage(), e);
    }

    // TODO - implement better cloning so that we dont have to
    // care about making main.py executable
    String commandString =
        String.format(
            "cd %s && "
                + "chmod +x main.py && "
                + "pipenv install &>/dev/null && "
                + "cdk8s import --language %s &>/dev/null && "
                + "cdk8s synth &>/dev/null && "
                + "cat dist/* ",
            outputPath.toString(), "python");

    log.info(">> executing ... ", commandString);
    List<String> command = new ArrayList<>();
    command.add("bash");
    command.add("-c");
    command.add(commandString);

    BakeRecipe result = new BakeRecipe();
    result.setCommand(command);
    return result;
  }

  // This being here is temporary until we find a better way to abstract it
  private static void extractArtifact(InputStream inputStream, Path outputPath) throws IOException {
    try (TarArchiveInputStream tarArchiveInputStream =
        new TarArchiveInputStream(
            new GzipCompressorInputStream(new BufferedInputStream(inputStream)))) {

      ArchiveEntry archiveEntry;
      while ((archiveEntry = tarArchiveInputStream.getNextEntry()) != null) {
        Path archiveEntryOutput = validateArchiveEntry(archiveEntry.getName(), outputPath);
        log.info("archive output: ", archiveEntryOutput.toString());
        if (archiveEntry.isDirectory()) {
          if (!Files.exists(archiveEntryOutput)) {
            Files.createDirectory(archiveEntryOutput);
          }
        } else {
          Files.copy(tarArchiveInputStream, archiveEntryOutput);
        }
      }
    }
  }

  private static Path validateArchiveEntry(String archiveEntryName, Path outputPath) {
    Path entryPath = outputPath.resolve(archiveEntryName);
    if (!entryPath.normalize().startsWith(outputPath)) {
      throw new IllegalStateException("Attempting to create a file outside of the staging path.");
    }
    return entryPath;
  }
}
