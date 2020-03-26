package io.zeebe.upgrade.workflow;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public final class WorkflowExpressionUpgrade {

  private static final String EXPRESSION_PREFIX = "=";

  public static BpmnModelInstance upgradeWorkflow(final BpmnModelInstance workflow) {
    final var targetWorkflow = workflow.clone();

    targetWorkflow
        .getModelElementsByType(ConditionExpression.class)
        .forEach(WorkflowExpressionUpgrade::upgradeConditionExpression);

    targetWorkflow
        .getModelElementsByType(ZeebeIoMapping.class)
        .forEach(WorkflowExpressionUpgrade::upgradeVariableMappingExpression);

    targetWorkflow
        .getModelElementsByType(ZeebeSubscription.class)
        .forEach(WorkflowExpressionUpgrade::upgradeMessageSubscription);

    targetWorkflow
        .getModelElementsByType(ZeebeLoopCharacteristics.class)
        .forEach(WorkflowExpressionUpgrade::upgradeMultiInstance);

    return targetWorkflow;
  }

  private static void upgradeVariableMappingExpression(final ZeebeIoMapping mapping) {
    mapping
        .getInputs()
        .forEach(
            input ->
                migrateExpression(
                    input::getSource,
                    input::setSource,
                    logChange(input, "source of input mapping")));

    mapping
        .getOutputs()
        .forEach(
            output ->
                migrateExpression(
                    output::getSource,
                    output::setSource,
                    logChange(output, "source of output mapping")));
  }

  private static void upgradeMessageSubscription(final ZeebeSubscription subscription) {
    migrateExpression(
        subscription::getCorrelationKey,
        subscription::setCorrelationKey,
        logChange(subscription, "correlation key"));
  }

  private static void upgradeMultiInstance(final ZeebeLoopCharacteristics loopCharacteristics) {
    migrateExpression(
        loopCharacteristics::getInputCollection,
        loopCharacteristics::setInputCollection,
        logChange(loopCharacteristics, "multi-instance input collection"));

    migrateExpression(
        loopCharacteristics::getOutputElement,
        loopCharacteristics::setOutputElement,
        logChange(loopCharacteristics, "multi-instance output element"));
  }

  private static void upgradeConditionExpression(final ConditionExpression conditionExpression) {
    final Function<String, String> expressionPrefix =
        WorkflowExpressionUpgrade::addExpressionPrefix;

    final Function<String, String> operatorReplacement =
        expression ->
            expression.replaceAll("&&", "and").replaceAll("\\|\\|", "or").replaceAll("==", "=");

    migrate(
        conditionExpression::getTextContent,
        expressionPrefix.andThen(operatorReplacement),
        conditionExpression::setTextContent,
        logChange(conditionExpression, "condition"));
  }

  private static void migrateExpression(
      Supplier<String> expressionGetter,
      Consumer<String> expressionSetter,
      BiConsumer<String, String> onChange) {

    migrate(
        expressionGetter,
        WorkflowExpressionUpgrade::addExpressionPrefix,
        expressionSetter,
        onChange);
  }

  private static void migrate(
      Supplier<String> expressionGetter,
      Function<String, String> migrateExpression,
      Consumer<String> expressionSetter,
      BiConsumer<String, String> onChange) {

    final var expression = expressionGetter.get();
    var newExpression = migrateExpression.apply(expression);

    if (!expression.equals(newExpression)) {
      expressionSetter.accept(newExpression);
      onChange.accept(expression, newExpression);
    }
  }

  private static String addExpressionPrefix(String expression) {
    if (!expression.startsWith(EXPRESSION_PREFIX)) {
      return EXPRESSION_PREFIX + expression;
    } else {
      return expression;
    }
  }

  private static BiConsumer<String, String> logChange(
      ModelElementInstance elementInstance, String expressionName) {
    return (before, after) ->
        System.out.printf(
            "\t* Migrate %s [id: %s] from '%s' to '%s'\n",
            expressionName, getElementId(elementInstance), before, after);
  }

  private static String getElementId(ModelElementInstance elementInstance) {
    final var id = elementInstance.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID);
    if (id != null && !id.isEmpty()) {
      return id;
    }

    final var parentElement = elementInstance.getParentElement();
    if (parentElement != null) {
      return getElementId(parentElement);
    }

    return "?";
  }
}
