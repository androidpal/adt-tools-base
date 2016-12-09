package com.android.tools.maven;

import com.android.tools.utils.WorkspaceUtils;
import com.google.common.collect.ImmutableSet;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

/**
 * Binary that generates a BUILD file with a single java_import for every {@code *.pom} file in a
 * local m2 repository.
 */
public class JavaImportGenerator {

    static final String JAR_RULE_NAME = "jar";
    static final String AAR_RULE_NAME = "aar";
    static final String POM_RULE_NAME = "pom";
    static final String EXE_RULE_NAME = "exe";
    private static final Set<String> SUPPORTED_EXTENSIONS = ImmutableSet.of("jar", "aar");
    private static final String GENERATED_WARNING =
            "# This BUILD file was generated by //tools/base/bazel:java_import_generator, please do not edit.";

    public static void main(String[] args) throws IOException, ArtifactDescriptorException {
        Path repoDirectory;

        if (args.length == 1) {
            repoDirectory = Paths.get(args[0]);
        } else {
            repoDirectory = WorkspaceUtils.findPrebuiltsRepository();
        }

        if (!Files.isDirectory(repoDirectory)) {
            usage();
        }

        new JavaImportGenerator(new MavenRepository(repoDirectory)).processPomFiles();
        System.out.println("Done.");
    }

    private static void usage() {
        System.err.println("Usage: java_import_generator [path/to/m2/repository]");
        System.err.println("");
        System.err.println(
                "If the path to m2 repo is omitted, the one from current WORKSPACE will be used.");
        System.exit(1);
    }

    private final MavenRepository mRepo;

    public JavaImportGenerator(MavenRepository repo) {
        mRepo = repo;
    }

    private void processPomFiles() throws IOException, ArtifactDescriptorException {
        Files.walk(mRepo.getDirectory())
                .filter(path -> path.toString().endsWith(".pom"))
                .forEach(
                        pom -> {
                            try {
                                processPomFile(pom, true);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });

        System.out.println();
    }

    /**
     * Processes a pom file generating all the rules needed for this pom.
     *
     * @return the path to a parent pom file if it exists.
     */
    private Path processPomFile(Path pomFile, boolean delete) throws IOException {
        Model pomModel = mRepo.getPomEffectiveModel(pomFile);
        if (pomModel == null) {
            return null;
        }
        Path artifact = null;
        if (SUPPORTED_EXTENSIONS.contains(MavenRepository.getArtifactExtension(pomModel))) {
            artifact = mRepo.getArtifactPath(pomModel);
            if (!Files.exists(artifact)) {
                System.err.println("Missing artifact: " + mRepo.relativize(artifact));
            }
        }

        Path parentPom = null;
        if (pomModel.getParent() != null) {
            parentPom = mRepo.getParentPomPath(pomModel);
        }
        Map<String, Path> exes = mRepo.getExecutables(pomModel);
        generateImportRules(artifact, pomFile, parentPom, delete, exes);
        return parentPom;
    }

    public void generateImportRules(Artifact artifact) throws IOException {
        Path pomFile = mRepo.getPomPath(artifact);
        while (pomFile != null) {
            pomFile = processPomFile(pomFile, false);
        }
    }

    private void generateImportRules(
            @Nullable Path artifact,
            @Nonnull Path pomFile,
            @Nullable Path parentPath,
            boolean delete,
            @Nonnull Map<String, Path> exes)
            throws IOException {
        Path directory = pomFile.getParent();
        Path buildFile = directory.resolve("BUILD");
        if (Files.exists(buildFile)) {
            if (delete) {
                Files.delete(buildFile);
            } else {
                return;
            }
        }

        try (FileWriter fileWriter = new FileWriter(buildFile.toFile())) {
            fileWriter.append(GENERATED_WARNING);
            fileWriter.append(System.lineSeparator());
            fileWriter.append(
                    "load(\"//tools/base/bazel:maven.bzl\", \"maven_java_import\", \"maven_pom\", \"maven_aar\")");
            fileWriter.append(System.lineSeparator());
            fileWriter.append(System.lineSeparator());

            if (artifact != null && Files.exists(artifact)) {
                if (artifact.toString().endsWith(".jar")) {
                    fileWriter.append(
                            String.format(
                                    "maven_java_import(name = \""
                                            + JAR_RULE_NAME
                                            + "\", jars = [\"%s\"], pom = \":%s\", "
                                            + "visibility = [\"//visibility:public\"])",
                                    artifact.getFileName(),
                                    POM_RULE_NAME));
                    fileWriter.append(System.lineSeparator());
                } else if (artifact.toString().endsWith(".aar")) {
                    fileWriter.append(
                            String.format(
                                    "maven_aar(name = \""
                                            + AAR_RULE_NAME
                                            + "\", aar = \"%s\", pom = \":%s\", "
                                            + "visibility = [\"//visibility:public\"])",
                                    artifact.getFileName(),
                                    POM_RULE_NAME));
                    fileWriter.append(System.lineSeparator());
                } else {
                    throw new IllegalArgumentException(
                            "Don't know how to generate import for " + artifact);
                }
            }
            String parent = "";
            if (parentPath != null) {
                String label = mRepo.relativize(parentPath).toString()
                        .replaceAll("/[^/]+.pom$", ":" + POM_RULE_NAME);
                label = "//prebuilts/tools/common/m2/repository/" + label;
                parent = ", parent =\"" + label + "\"";
            }
            fileWriter.append(
                    String.format(
                            "maven_pom(name = \""
                                    + POM_RULE_NAME
                                    + "\", source = \"%s\" %s, visibility = [\"//visibility:public\"])",
                            pomFile.getFileName(),
                            parent));
            fileWriter.append(System.lineSeparator());

            String srcs = "select({";
            boolean exist = true;
            for (Map.Entry<String, Path> exe : exes.entrySet()) {
                exist = exist && Files.exists(exe.getValue());
                srcs +=
                        "\"//prebuilts/tools/common/m2:"
                                + exe.getKey()
                                + "\": [\""
                                + exe.getValue().getFileName()
                                + "\"],";
            }
            srcs += "})";
            if (exist) {
                fileWriter.append(
                        String.format(
                                "filegroup(name = \""
                                        + EXE_RULE_NAME
                                        + "\", srcs = %s,visibility = [\"//visibility:public\"])",
                                srcs));
                fileWriter.append(System.lineSeparator());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
