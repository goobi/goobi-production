/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.workflow.model;

import de.sub.goobi.config.ConfigCore;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.file.FileService;
import org.kitodo.workflow.model.beans.Diagram;
import org.kitodo.workflow.model.beans.KitodoScriptTask;
import org.kitodo.workflow.model.beans.KitodoTask;
import org.kitodo.workflow.model.beans.TaskInfo;

public class Reader {

    private String diagramName;
    private BpmnModelInstance modelInstance;
    private ServiceManager serviceManager = new ServiceManager();
    private FileService fileService = serviceManager.getFileService();
    private Collection<FlowNode> followingFlowNodes;
    private Map<Task, TaskInfo> tasks;
    private Diagram workflow;

    /**
     * Constructor with diagram name as parameter. It loads modelInstance from file
     * for given name.
     *
     * @param diagramName
     *            as String
     * @throws IOException
     *             in case if file for given name doesn't exist
     */
    public Reader(String diagramName) throws IOException {
        this.diagramName = diagramName;
        loadProcess();
    }

    /**
     * Convert BPMN process (workflow) to template stored in database.
     *
     * @return Template bean
     */
    public Template convertWorkflowToTemplate() throws DAOException, DataException {
        this.tasks = new HashMap<>();
        this.followingFlowNodes = new ArrayList<>();

        Template template = new Template();
        Workflow workflow = determineWorkflow(this.workflow.getId(), this.diagramName);
        workflow.getTemplates().add(template);
        template.setWorkflow(workflow);

        return prepareTemplate(template);
    }

    /**
     * Update all templates assigned to workflow after diagram was changed.
     *
     * @param changedWorkflow
     *            Workflow bean - instance assigned to diagram which was changed
     */
    public void updateTemplatesAssignedToWorkflowAfterDiagramChange(Workflow changedWorkflow)
            throws DAOException, DataException {
        this.tasks = new HashMap<>();
        this.followingFlowNodes = new ArrayList<>();
        List<Template> templatesToUpdate = changedWorkflow.getTemplates();

        for (Template template : templatesToUpdate) {
            template.getTasks().clear();
            template = prepareTemplate(template);
            serviceManager.getTemplateService().save(template);
        }
    }

    private Template prepareTemplate(Template template) throws DAOException {
        template.setTitle(this.workflow.getTitle());
        template.setOutputName(this.workflow.getOutputName());

        Integer docketId = this.workflow.getDocket();
        if (docketId > 0) {
            template.setDocket(serviceManager.getDocketService().getById(docketId));
        }

        Integer rulesetId = this.workflow.getRuleset();
        if (rulesetId > 0) {
            template.setRuleset(serviceManager.getRulesetService().getById(rulesetId));
        }

        getWorkflowTasks();

        for (Map.Entry<Task, TaskInfo> entry : tasks.entrySet()) {
            org.kitodo.data.database.beans.Task task = getTask(entry.getKey(), entry.getValue());
            task.setTemplate(template);
            template.getTasks().add(task);
        }

        return template;
    }

    /**
     * Read the workflow from diagram.
     */
    private void loadProcess() throws IOException {
        String diagramPath = ConfigCore.getKitodoDiagramDirectory() + this.diagramName + ".bpmn20.xml";
        modelInstance = Bpmn.readModelFromStream(fileService.read(Paths.get(diagramPath).toUri()));
        getWorkflowFromProcess();
    }

    /**
     * Get workflow from process inside the given file.
     */
    private void getWorkflowFromProcess() throws IOException {
        Process process = modelInstance.getModelElementsByType(Process.class).iterator().next();

        if (Objects.isNull(process)) {
            throw new IOException("It looks that given file contains invalid BPMN diagram!");
        }
        this.workflow = new Diagram(process);
    }

    private Workflow determineWorkflow(String title, String file) throws DataException {
        List<Workflow> workflows = serviceManager.getWorkflowService().getWorkflowsForTitleAndFile(title, file);

        if (workflows.size() > 0) {
            if (workflows.size() == 1) {
                return workflows.get(0);
            } else {
                throw new DataException("There is more than one entry with given data!");
            }
        }

        return new Workflow(title, file);
    }

    private org.kitodo.data.database.beans.Task getTask(Task workflowTask, TaskInfo taskInfo) {
        org.kitodo.data.database.beans.Task task = new org.kitodo.data.database.beans.Task();
        KitodoTask kitodoTask = new KitodoTask(workflowTask, taskInfo.getOrdering());
        task.setWorkflowId(kitodoTask.getWorkflowId());
        task.setTitle(kitodoTask.getTitle());
        task.setOrdering(kitodoTask.getOrdering());
        task.setPriority(kitodoTask.getPriority());
        task.setEditType(kitodoTask.getEditType());
        task.setBatchStep(kitodoTask.getBatchStep());
        task.setTypeAutomatic(kitodoTask.getTypeAutomatic());
        task.setTypeExportDMS(kitodoTask.getTypeExportDms());
        task.setTypeExportRussian(kitodoTask.getTypeExportRussian());
        task.setTypeMetadata(kitodoTask.getTypeMetadata());
        task.setTypeImportFileUpload(kitodoTask.getTypeImportFileUpload());
        task.setTypeImagesRead(kitodoTask.getTypeImagesRead());
        task.setTypeImagesWrite(kitodoTask.getTypeImagesWrite());
        task.setTypeAcceptClose(kitodoTask.getTypeAcceptClose());
        task.setTypeCloseVerify(kitodoTask.getTypeCloseVerify());
        task.setWorkflowCondition(taskInfo.getCondition());

        if (workflowTask instanceof ScriptTask) {
            KitodoScriptTask kitodoScriptTask = new KitodoScriptTask((ScriptTask) workflowTask, taskInfo.getOrdering());
            task.setScriptName(kitodoScriptTask.getScriptName());
            task.setScriptPath(kitodoScriptTask.getScriptPath());
        }

        return task;
    }

    private void getWorkflowTasks() {
        StartEvent startEvent = modelInstance.getModelElementsByType(StartEvent.class).iterator().next();
        getFlowingFlowNodes(startEvent);

        int i = 1;
        Iterator<FlowNode> sequenceFlowIterator = this.followingFlowNodes.iterator();
        while (sequenceFlowIterator.hasNext()) {
            FlowNode flowNode = sequenceFlowIterator.next();

            if (flowNode instanceof Gateway) {
                addTasksBranch((Gateway) flowNode, sequenceFlowIterator, i);
            } else {
                addTask(flowNode, new TaskInfo(i, "default"));
                i++;
            }
        }
    }

    /**
     * Create sequence flow for given diagrams.
     *
     * @param node
     *            add description
     */
    private void getFlowingFlowNodes(FlowNode node) {
        Collection<SequenceFlow> sequenceFlow = node.getOutgoing();
        Iterator<SequenceFlow> sequenceFlowIterator = sequenceFlow.iterator();

        if (sequenceFlowIterator.hasNext()) {
            FlowNode flowNode = sequenceFlowIterator.next().getTarget();

            if (flowNode instanceof Gateway) {
                getFlowingFlowNodesWithConditions((Gateway) flowNode);
            } else if (flowNode instanceof Task) {
                this.followingFlowNodes.add(flowNode);
                getFlowingFlowNodes(flowNode);
            }
        }
    }

    /**
     * Add flowing nodes after gateway.
     *
     * @param gateway
     *            add description
     */
    private void getFlowingFlowNodesWithConditions(Gateway gateway) {
        Collection<SequenceFlow> conditionedSequencedFlow = gateway.getOutgoing();
        for (SequenceFlow sequenceFlow : conditionedSequencedFlow) {
            this.followingFlowNodes.add(gateway);
            this.followingFlowNodes.add(sequenceFlow.getTarget());
            getFlowingFlowNodes(sequenceFlow.getTarget());
        }
    }

    /**
     * Add all tasks in exact branch - following given gateway.
     *
     * @param gateway
     *            which is followed by tasks
     * @param sequenceFlowIterator
     *            iterator containing tasks following given gateway
     * @param ordering
     *            which is going to be assigned to tasks in this branch
     */
    private void addTasksBranch(Gateway gateway, Iterator<FlowNode> sequenceFlowIterator, int ordering) {
        int j = ordering;
        ConditionExpression conditionExpression = null;

        while (sequenceFlowIterator.hasNext()) {
            FlowNode flowNode = sequenceFlowIterator.next();
            if (Objects.isNull(conditionExpression)) {
                conditionExpression = flowNode.getIncoming().iterator().next().getConditionExpression();
            }
            if (Objects.equals(gateway, flowNode)) {
                break;
            } else {
                TaskInfo taskInfo = new TaskInfo(j, conditionExpression.getTextContent());
                addTask(flowNode, taskInfo);
                j++;
            }
        }
    }

    private void addTask(FlowNode flowNode, TaskInfo taskInfo) {
        if (flowNode instanceof Task) {
            if (tasks.containsKey(flowNode)) {
                String currentCondition = tasks.get(flowNode).getCondition();
                String appendCondition = taskInfo.getCondition();
                taskInfo.setCondition(currentCondition + " " + appendCondition);
            }
            tasks.put((Task) flowNode, taskInfo);
        }
    }

    /**
     * Get modelInstance.
     *
     * @return value of modelInstance
     */
    public BpmnModelInstance getModelInstance() {
        return modelInstance;
    }

    /**
     * Get tasks.
     *
     * @return value of tasks
     */
    public Map<Task, TaskInfo> getTasks() {
        return tasks;
    }

    /**
     * Set tasks.
     *
     * @param tasks
     *            as list of BPMN Task objects
     */
    public void setTasks(Map<Task, TaskInfo> tasks) {
        this.tasks = tasks;
    }

    /**
     * Get workflow.
     *
     * @return value of workflow
     */
    public Diagram getWorkflow() {
        return workflow;
    }
}
