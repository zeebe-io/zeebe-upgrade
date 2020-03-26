package io.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.upgrade.workflow.UpgradeWorkflows;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowExpressionUpgradeTest {

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private String sourceFolder;
  private File targetFolder;

  @Before
  public void init() throws IOException {
    sourceFolder = getClass().getResource("/workflows").getFile();
    targetFolder = temp.newFolder("target");

    final var args = new String[] {sourceFolder, targetFolder.toString()};
    UpgradeWorkflows.main(args);
  }

  @Test
  public void conditionExpression() {
    // given
    final var sourceFile = new File(sourceFolder, "wf-with-condition.bpmn");
    final var sourceWorkflow = Bpmn.readModelFromFile(sourceFile);

    assertThat(
            sourceWorkflow.getModelElementsByType(ConditionExpression.class).stream()
                .map(ConditionExpression::getTextContent))
        .hasSize(1)
        .allSatisfy(
            condition -> assertThat(condition).doesNotStartWith("=").contains("||", "&&", "=="));

    // then
    final var targetFile = new File(targetFolder, "wf-with-condition.bpmn");
    final var targetWorkflow = Bpmn.readModelFromFile(targetFile);

    assertThat(
            targetWorkflow.getModelElementsByType(ConditionExpression.class).stream()
                .map(ConditionExpression::getTextContent))
        .hasSize(1)
        .allSatisfy(
            condition ->
                assertThat(condition)
                    .startsWith("=")
                    .contains("or", "and")
                    .doesNotContain("||", "&&", "=="));
  }

  @Test
  public void variableMappingExpression() {
    // given
    final var sourceFile = new File(sourceFolder, "wf-with-mappings.bpmn");
    final var sourceWorkflow = Bpmn.readModelFromFile(sourceFile);

    assertThat(
            sourceWorkflow.getModelElementsByType(ZeebeInput.class).stream()
                .map(ZeebeMapping::getSource))
        .hasSize(2)
        .allSatisfy(source -> assertThat(source).doesNotStartWith("="));

    assertThat(
            sourceWorkflow.getModelElementsByType(ZeebeOutput.class).stream()
                .map(ZeebeMapping::getSource))
        .hasSize(2)
        .allSatisfy(source -> assertThat(source).doesNotStartWith("="));

    // then
    final var targetFile = new File(targetFolder, "wf-with-mappings.bpmn");
    final var targetWorkflow = Bpmn.readModelFromFile(targetFile);

    assertThat(
            targetWorkflow.getModelElementsByType(ZeebeInput.class).stream()
                .map(ZeebeMapping::getSource))
        .hasSize(2)
        .allSatisfy(source -> assertThat(source).startsWith("="));

    assertThat(
            targetWorkflow.getModelElementsByType(ZeebeOutput.class).stream()
                .map(ZeebeMapping::getSource))
        .hasSize(2)
        .allSatisfy(source -> assertThat(source).startsWith("="));
  }

  @Test
  public void messageCorrelationKeyExpression() {
    // given
    final var sourceFile = new File(sourceFolder, "wf-with-message-correlation-key.bpmn");
    final var sourceWorkflow = Bpmn.readModelFromFile(sourceFile);

    assertThat(
            sourceWorkflow.getModelElementsByType(ZeebeSubscription.class).stream()
                .map(ZeebeSubscription::getCorrelationKey))
        .hasSize(3)
        .allSatisfy(correlationKey -> assertThat(correlationKey).doesNotStartWith("="));

    // then
    final var targetFile = new File(targetFolder, "wf-with-message-correlation-key.bpmn");
    final var targetWorkflow = Bpmn.readModelFromFile(targetFile);

    assertThat(
            targetWorkflow.getModelElementsByType(ZeebeSubscription.class).stream()
                .map(ZeebeSubscription::getCorrelationKey))
        .hasSize(3)
        .allSatisfy(correlationKey -> assertThat(correlationKey).startsWith("="));
  }

  @Test
  public void multiInstanceExpressions() {
    // given
    final var sourceFile = new File(sourceFolder, "wf-with-multi-instance.bpmn");
    final var sourceWorkflow = Bpmn.readModelFromFile(sourceFile);

    assertThat(
            sourceWorkflow.getModelElementsByType(ZeebeLoopCharacteristics.class).stream()
                .map(ZeebeLoopCharacteristics::getInputCollection))
        .hasSize(2)
        .allSatisfy(inputCollection -> assertThat(inputCollection).doesNotStartWith("="));

    assertThat(
            sourceWorkflow.getModelElementsByType(ZeebeLoopCharacteristics.class).stream()
                .map(ZeebeLoopCharacteristics::getOutputElement))
        .hasSize(2)
        .allSatisfy(outputElement -> assertThat(outputElement).doesNotStartWith("="));

    // then
    final var targetFile = new File(targetFolder, "wf-with-multi-instance.bpmn");
    final var targetWorkflow = Bpmn.readModelFromFile(targetFile);

    assertThat(
            targetWorkflow.getModelElementsByType(ZeebeLoopCharacteristics.class).stream()
                .map(ZeebeLoopCharacteristics::getInputCollection))
        .hasSize(2)
        .allSatisfy(inputCollection -> assertThat(inputCollection).startsWith("="));

    assertThat(
            targetWorkflow.getModelElementsByType(ZeebeLoopCharacteristics.class).stream()
                .map(ZeebeLoopCharacteristics::getOutputElement))
        .hasSize(2)
        .allSatisfy(outputElement -> assertThat(outputElement).startsWith("="));
  }
}
