package io.zeebe.upgrade.workflow;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class UpgradeWorkflows {

  public static final String BPMN_FILE_EXTENSION = ".bpmn";
  public static final String BPMN_XML_FILE_EXTENSION = ".bpmn20.xml";

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.printf("Invalid arguments: %s\n", Arrays.toString(args));
      System.err.println("Expected 2 arguments: <source-directory>  <target-directory>");
      System.err.println("\t<source-directory> - the directory to read the BPMN files from");
      System.err.println(
          "\t<target-directory> - the directory to write the upgraded BPMN files to");
      System.exit(1);
    }

    final String sourceDir = args[0];
    final String targetDir = args[1];

    final var sourcePath = Paths.get(sourceDir);
    final var targetPath = Paths.get(targetDir);

    validatePath(sourcePath);

    targetPath.toFile().mkdirs();

    System.out.printf(
        "Upgrade workflows from directory '%s' to '%s'\n",
        sourcePath.toAbsolutePath(), targetPath.toAbsolutePath());

    upgradeWorkflows(sourcePath, targetPath);
  }

  private static void upgradeWorkflows(final Path sourcePath, final Path targetPath)
      throws IOException {
    final AtomicInteger updatedWorkflows = new AtomicInteger();
    final List<Path> bpmnWithFailures = new ArrayList<>();

    Files.walk(sourcePath, FileVisitOption.FOLLOW_LINKS)
        .filter(UpgradeWorkflows::isBpmnFile)
        .peek(path -> System.out.printf("> Upgrade BPMN file '%s'\n", path.toAbsolutePath()))
        .forEach(
            path -> {
              try {

                final var workflow = readBpmnFile(path);
                final var upgradedWorkflow = WorkflowExpressionUpgrade.upgradeWorkflow(workflow);

                final var relativePath = sourcePath.relativize(path);
                final var target = targetPath.resolve(relativePath);

                writeWorkflow(upgradedWorkflow, target);

                updatedWorkflows.incrementAndGet();

              } catch (Exception e) {
                e.printStackTrace();

                bpmnWithFailures.add(path);
              }
            });

    System.out.printf("Done. Upgraded %d workflows.\n", updatedWorkflows.get());
    if (!bpmnWithFailures.isEmpty()) {
      final var bpmnFiles =
          bpmnWithFailures.stream()
              .map(path -> path.toAbsolutePath().toString())
              .collect(Collectors.joining("\n"));

      System.out.printf("Unable to upgrade %d workflows:\n%s", bpmnWithFailures.size(), bpmnFiles);
    }
  }

  private static void writeWorkflow(final BpmnModelInstance upgradedWorkflow, final Path target)
      throws IOException {
    // the model API insert a lot of empty new lines
    final var bpmnString = Bpmn.convertToString(upgradedWorkflow);
    final var lines = bpmnString.split("\n");
    final var linesWithoutBlankLines =
        Stream.of(lines).filter(line -> !line.isBlank()).collect(Collectors.toList());

    // create nested directories
    Files.createDirectories(target.getParent());

    Files.write(target, linesWithoutBlankLines);
  }

  private static BpmnModelInstance readBpmnFile(Path path) {
    return Bpmn.readModelFromFile(path.toFile());
  }

  private static boolean isBpmnFile(Path path) {
    final var file = path.toFile();
    if (file.isFile()) {
      final var fileName = file.getName();
      final var normalized = fileName.toLowerCase();
      return normalized.endsWith(BPMN_FILE_EXTENSION)
          || normalized.endsWith(BPMN_XML_FILE_EXTENSION);
    } else {
      return false;
    }
  }

  private static void validatePath(final Path path) {
    if (!path.toFile().exists()) {
      System.err.printf("The given path '%s' doesn't exist.\n", path.toAbsolutePath());
      System.exit(1);
    }

    if (!path.toFile().isDirectory()) {
      System.err.printf("The given path '%s' is no directory.\n", path.toAbsolutePath());
      System.exit(1);
    }
  }
}
