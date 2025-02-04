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

package org.kitodo.production.forms.createprocess;

import static org.kitodo.constants.StringConstants.CREATE;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import javax.xml.stream.XMLStreamException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.externaldatamanagement.ImportConfigurationType;
import org.kitodo.api.schemaconverter.MetadataFormat;
import org.kitodo.constants.StringConstants;
import org.kitodo.data.database.beans.Client;
import org.kitodo.data.database.beans.ImportConfiguration;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.exceptions.RecordIdentifierMissingDetail;
import org.kitodo.exceptions.RulesetNotFoundException;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.forms.BaseForm;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.ProcessHelper;
import org.kitodo.production.helper.TempProcess;
import org.kitodo.production.helper.XMLUtils;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.interfaces.MetadataTreeTableInterface;
import org.kitodo.production.interfaces.RulesetSetupInterface;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.process.ProcessGenerator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ImportService;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.dataeditor.DataEditorService;
import org.kitodo.production.thread.ImportEadProcessesThread;
import org.primefaces.PrimeFaces;
import org.primefaces.model.TreeNode;

@Named("CreateProcessForm")
@ViewScoped
public class CreateProcessForm extends BaseForm implements MetadataTreeTableInterface, RulesetSetupInterface {

    private static final Logger logger = LogManager.getLogger(CreateProcessForm.class);

    private final CatalogImportDialog catalogImportDialog = new CatalogImportDialog(this);
    private final FileUploadDialog fileUploadDialog = new FileUploadDialog(this);
    private final SearchDialog searchDialog = new SearchDialog(this);
    private final ProcessDataTab processDataTab = new ProcessDataTab(this);
    private final TitleRecordLinkTab titleRecordLinkTab = new TitleRecordLinkTab(this);
    private final AddMetadataDialog addMetadataDialog = new AddMetadataDialog(this);

    private RulesetManagementInterface rulesetManagement;
    private final List<Locale.LanguageRange> priorityList;
    private Project project;
    private Template template;
    private LinkedList<TempProcess> processes = new LinkedList<>();
    private LinkedList<TempProcess> childProcesses = new LinkedList<>();
    private final String processListPath = MessageFormat.format(REDIRECT_PATH, "processes");
    private String referringView = "";
    private int progress;
    private TempProcess currentProcess;
    private Boolean rulesetConfigurationForOpacImportComplete = null;
    private ImportConfiguration currentImportConfiguration;
    static final int TITLE_RECORD_LINK_TAB_INDEX = 1;

    private final List<String> eadLevels = Arrays.asList(StringConstants.FILE, StringConstants.ITEM);
    private final List<String> eadParentLevels = Arrays.asList(StringConstants.COLLECTION, StringConstants.CLASS,
            StringConstants.SERIES);
    private String selectedEadLevel = StringConstants.FILE;
    private String selectedParentEadLevel = StringConstants.COLLECTION;
    private String xmlString;
    private String filename;
    protected int numberOfEadElements;

    public CreateProcessForm() {
        priorityList = ServiceManager.getUserService().getCurrentMetadataLanguage();
    }

    CreateProcessForm(List<Locale.LanguageRange> priorityList) {
        this.priorityList = priorityList;
    }

    /**
     * Calculate number of EAD elements of selected level (e.g. "item", "file" etc.) from "xmlString", containing
     * content of currently imported XML file.
     *
     * @throws XMLStreamException when retrieving EAD from XML data fails
     */
    public void calculateNumberOfEadElements() throws XMLStreamException {
        numberOfEadElements = XMLUtils.getNumberOfEADElements(xmlString, selectedEadLevel);
    }

    /**
     * Returns the ruleset management to access the ruleset.
     *
     * @return the ruleset management
     */
    @Override
    public RulesetManagementInterface getRulesetManagement() {
        return rulesetManagement;
    }

    /**
     * Update ruleset and docType.
     *
     * @param ruleset
     *            as Ruleset
     * @throws IOException
     *             thrown if ruleset could not be read
     */
    public void updateRulesetAndDocType(Ruleset ruleset) throws IOException {
        rulesetManagement = ServiceManager.getRulesetService().openRuleset(ruleset);
        processDataTab.setAllDocTypes(getAllRulesetDivisions());
    }

    private List<SelectItem> getAllRulesetDivisions() {
        List<SelectItem> allDocTypes = rulesetManagement
                .getStructuralElements(priorityList).entrySet()
                .stream().map(entry -> new SelectItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        if (allDocTypes.isEmpty()) {
            Helper.setErrorMessage("errorLoadingDocTypes");
        }
        return allDocTypes;
    }

    /**
     * Returns the current acquisition stage to adapt the displaying of fields
     * accordingly.
     *
     * @return the current acquisition stage
     */
    @Override
    public String getAcquisitionStage() {
        return CREATE;
    }

    /**
     * Returns the language preference list of the editing user to display
     * labels and options in the user-preferred language.
     *
     * @return the language preference list
     */
    @Override
    public List<Locale.LanguageRange> getPriorityList() {
        return priorityList;
    }

    /**
     * Get importTab.
     *
     * @return value of catalogImportDialog
     */
    public CatalogImportDialog getCatalogImportDialog() {
        return catalogImportDialog;
    }

    /**
     * Get fileUploadDialog.
     *
     * @return value of fileUploadDialog
     */
    public FileUploadDialog getFileUploadDialog() {
        return fileUploadDialog;
    }

    /**
     * Get processDataTab.
     *
     * @return value of processDataTab
     */
    public ProcessDataTab getProcessDataTab() {
        return processDataTab;
    }

    /**
     * Get processMetadata.
     *
     * @return value of processMetadata
     */
    public ProcessMetadata getProcessMetadata() {
        return currentProcess.getProcessMetadata();
    }

    /**
     * Get searchDialog.
     *
     * @return value of searchDialog
     */
    public SearchDialog getSearchDialog() {
        return searchDialog;
    }

    /**
     * Get titleRecordLinkTab.
     *
     * @return value of titleRecordLinkTab
     */
    public TitleRecordLinkTab getTitleRecordLinkTab() {
        return titleRecordLinkTab;
    }

    /**
     * Get addMetadataDialog.
     * @return addMetadataDialog
     */
    public AddMetadataDialog getAddMetadataDialog() {
        return addMetadataDialog;
    }

    /**
     * Get newProcesses.
     *
     * @return value of newProcesses
     */
    public List<TempProcess> getProcesses() {
        return processes;
    }

    /**
     * Set newProcesses.
     *
     * @param processes as java.util.List of Process
     */
    public void setProcesses(LinkedList<TempProcess> processes) {
        this.processes = processes;
    }

    /**
     * Get child processes.
     *
     * @return childProcesses
     */
    public List<TempProcess> getChildProcesses() {
        return this.childProcesses;
    }

    /**
     * Set childProcesses.
     *
     * @param childProcesses as java.util.LinkedList of TempProcess
     */
    public void setChildProcesses(LinkedList<TempProcess> childProcesses) {
        this.childProcesses = childProcesses;
    }

    /**
     * Get the main Process that want to be created.
     *
     * @return value of first element in newProcesses
     */
    public Process getMainProcess() {
        if (processes.isEmpty()) {
            try {
                ProcessGenerator processGenerator = new ProcessGenerator();
                processGenerator.generateProcess(template.getId(), project.getId());
                processes.add(new TempProcess(processGenerator.getGeneratedProcess(), new Workpiece()));
            } catch (ProcessGenerationException exception) {
                Helper.setErrorMessage(exception.getLocalizedMessage(), logger, exception);
            }
        }
        return processes.get(0).getProcess();
    }

    /**
     * Get template.
     *
     * @return value of template
     */
    public Template getTemplate() {
        return template;
    }

    /**
     * Set template.
     *
     * @param template as org.kitodo.data.database.beans.Template
     */
    public void setTemplate(Template template) {
        this.template = template;
    }

    /**
     * Get project.
     *
     * @return value of project
     */
    public Project getProject() {
        return project;
    }

    /**
     * Set project.
     *
     * @param project as org.kitodo.data.database.beans.Project
     */
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Create the process and save the metadata.
     */
    public String createNewProcess() {
        try {
            if (!canCreateProcess()) {
                return this.stayOnCurrentPage;
            }
            createProcessHierarchy();
            if (Objects.nonNull(PrimeFaces.current()) && Objects.nonNull(FacesContext.getCurrentInstance())) {
                PrimeFaces.current().executeScript("PF('sticky-notifications').renderMessage({'summary':'"
                        + Helper.getTranslation("processSaving") + "','detail':'"
                        + Helper.getTranslation( "youWillBeRedirected") + "','severity':'info'});");
            }
            return processListPath;
        } catch (DataException e) {
            Helper.setErrorMessage("errorSaving", new Object[] {ObjectType.PROCESS.getTranslationSingular() },
                    logger, e);
        } catch (RulesetNotFoundException e) {
            String rulesetFile = "Process list is empty";
            if (!this.processes.isEmpty() && Objects.nonNull(getMainProcess().getRuleset())) {
                rulesetFile = getMainProcess().getRuleset().getFile();
            }
            Helper.setErrorMessage("rulesetNotFound", new Object[] {rulesetFile }, logger, e);
        } catch (IOException | ProcessGenerationException e) {
            logger.error(e.getLocalizedMessage(), e);
        }
        return this.stayOnCurrentPage;
    }

    /**
     * Create new Process and reload current view to add another Process.
     *
     * @return path to reload current view
     */
    public String createNewProcessAndContinue() {
        String destination = createNewProcess();
        if (!destination.equals(processListPath)) {
            return destination;
        }
        Process parentProcess = titleRecordLinkTab.getTitleRecordProcess();
        return FacesContext.getCurrentInstance().getExternalContext().getRequestServletPath()
                + "?referrer=" + referringView
                + "&templateId=" + template.getId()
                + "&projectId=" + project.getId()
                + (Objects.nonNull(parentProcess) ? "&parentId=" + parentProcess.getId() : "")
                + "&faces-redirect=true";
    }

    private boolean canCreateProcess() throws IOException {
        if (Objects.nonNull(titleRecordLinkTab.getTitleRecordProcess())) {
            if ((Objects.isNull(titleRecordLinkTab.getSelectedInsertionPosition())
                    || titleRecordLinkTab.getSelectedInsertionPosition().isEmpty())) {
                Helper.setErrorMessage("createProcessForm.createNewProcess.noInsertionPositionSelected");
                return false;
            }
            String forbiddenParentType = parentTypeIfForbidden();
            if (Objects.nonNull(forbiddenParentType)) {
                Helper.setErrorMessage(Helper.getTranslation("dataEditor.forbiddenChildElement",
                    processDataTab.getDocType(), forbiddenParentType));
                return false;
            }
        }
        return true;
    }

    private String parentTypeIfForbidden() throws IOException {
        URI metadataFileUri = ServiceManager.getProcessService()
                .getMetadataFileUri(titleRecordLinkTab.getTitleRecordProcess());
        Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metadataFileUri);
        List<String> indices = Arrays.asList(titleRecordLinkTab.getSelectedInsertionPosition()
                .split(Pattern.quote(MetadataEditor.INSERTION_POSITION_SEPARATOR)));
        LogicalDivision logicalDivision = workpiece.getLogicalStructure();
        for (int index = 0; index < indices.size(); index++) {
            if (index < indices.size() - 1) {
                logicalDivision = logicalDivision.getChildren().get(Integer.parseInt(indices.get(index)));
            } else {
                String parentType = logicalDivision.getType();
                StructuralElementViewInterface divisionView = rulesetManagement.getStructuralElementView(parentType,
                    CREATE, priorityList);
                if (divisionView.getAllowedSubstructuralElements().containsKey(processDataTab.getDocType())) {
                    return null;
                } else {
                    return parentType;
                }
            }
        }
        return "";
    }

    /**
     * Prepare new process which will be created.
     *
     * @param templateId
     *            id of template to query from database
     * @param projectId
     *            id of project to query from database
     * @param referringView
     *            view the user was coming from
     */
    public void prepareProcess(int templateId, int projectId, String referringView, Integer parentId) {
        this.referringView = referringView;
        ProcessGenerator processGenerator = new ProcessGenerator();
        try {
            boolean generated = processGenerator.generateProcess(templateId, projectId);
            if (generated) {
                Workpiece workpiece = new Workpiece();
                processes = new LinkedList<>(Collections.singletonList(new TempProcess(
                        processGenerator.getGeneratedProcess(), workpiece)));
                currentProcess = processes.get(0);
                project = processGenerator.getProject();
                template = processGenerator.getTemplate();
                updateRulesetAndDocType(getMainProcess().getRuleset());
                if (Objects.nonNull(project)) {
                    setCurrentImportConfiguration(project.getDefaultImportConfiguration());
                }
                if (Objects.nonNull(parentId) && parentId != 0) {
                    ProcessDTO parentProcess = ServiceManager.getProcessService().findById(parentId);
                    RulesetManagementInterface rulesetManagement = ServiceManager.getRulesetService()
                            .openRuleset(ServiceManager.getRulesetService().getById(parentProcess.getRuleset().getId()));
                    Map<String, String> allowedSubstructuralElements = rulesetManagement
                            .getStructuralElementView(parentProcess.getBaseType(), "", priorityList)
                            .getAllowedSubstructuralElements();
                    List<SelectItem> docTypes = allowedSubstructuralElements.entrySet()
                            .stream().map(entry -> new SelectItem(entry.getKey(), entry.getValue()))
                            .collect(Collectors.toList());
                    processDataTab.setAllDocTypes(docTypes);
                    titleRecordLinkTab.setChosenParentProcess(String.valueOf(parentId));
                    titleRecordLinkTab.chooseParentProcess();
                    if (Objects.nonNull(project.getDefaultChildProcessImportConfiguration())) {
                        setCurrentImportConfiguration(project.getDefaultChildProcessImportConfiguration());
                    }
                    if (setChildCount(titleRecordLinkTab.getTitleRecordProcess(), rulesetManagement, workpiece)) {
                        updateRulesetAndDocType(getMainProcess().getRuleset());
                    }
                }
                processDataTab.prepare();
                showDialogForImportConfiguration(currentImportConfiguration);
            }
        } catch (ProcessGenerationException | DataException | DAOException | IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    private void showDialogForImportConfiguration(ImportConfiguration importConfiguration) {
        if (Objects.nonNull(importConfiguration)) {
            if (ImportConfigurationType.OPAC_SEARCH.name().equals(importConfiguration.getConfigurationType())) {
                PrimeFaces.current().executeScript("PF('catalogSearchDialog').show();");
            } else if (ImportConfigurationType.PROCESS_TEMPLATE.name().equals(importConfiguration.getConfigurationType())) {
                PrimeFaces.current().executeScript("PF('searchEditDialog').show();");
            } else if (ImportConfigurationType.FILE_UPLOAD.name().equals(importConfiguration.getConfigurationType())) {
                PrimeFaces.current().executeScript("PF('fileUploadDialog').show();");
            }
        }
    }

    static boolean setChildCount(Process parent, RulesetManagementInterface ruleset, Workpiece workpiece) throws IOException {
        Collection<String> childCountKeys = ruleset.getFunctionalKeys(FunctionalMetadata.CHILD_COUND);
        if (childCountKeys.isEmpty()) {
            return false;
        }
        String childCount = Integer.toString(parent.getChildren().size() + 1);
        for (String childCountKey : childCountKeys) {
            MetadataEntry entry = new MetadataEntry();
            entry.setKey(childCountKey);
            entry.setValue(childCount);
            workpiece.getLogicalStructure().getMetadata().add(entry);
        }
        return true;
    }

    /**
     * Create process hierarchy.
     */
    public void createProcessHierarchy()
            throws DataException, ProcessGenerationException, IOException {
        // discard all processes in hierarchy except the first if parent process in
        // title record link tab is selected!
        if (this.processes.size() > 1 && Objects.nonNull(this.titleRecordLinkTab.getTitleRecordProcess())
                && Objects.nonNull(this.titleRecordLinkTab.getSelectedInsertionPosition())
                && !this.titleRecordLinkTab.getSelectedInsertionPosition().isEmpty()) {
            this.processes = new LinkedList<>(Collections.singletonList(this.processes.get(0)));
        }
        processTempProcess(this.processes.get(0));
        processAncestors();
        processChildren();
        // main process and it's ancestors need to be saved, so they have IDs before creating their process directories
        ServiceManager.getProcessService().save(getMainProcess(), true);
        if (!createProcessesLocation(this.processes)) {
            throw new IOException("Unable to create directories for process hierarchy!");
        }

        if (saveChildProcesses() && !createProcessesLocation(this.childProcesses)) {
            throw new IOException("Unable to create directories for child processes!");
        }
        saveProcessHierarchyMetadata();
        // TODO: do the same 'ensureNonEmptyTitles' for child processes?
        if (ImportService.ensureNonEmptyTitles(this.processes)) {
            // saving the main process automatically saves its parent and ancestor processes as well!
            ServiceManager.getProcessService().save(getMainProcess(), true);
        }

        // add links between child processes and main process
        this.saveChildProcessLinks();

        // if a process is selected in 'TitleRecordLinkTab' link it as parent with the first process in the list
        if (!this.processes.isEmpty() && Objects.nonNull(titleRecordLinkTab.getTitleRecordProcess())) {
            MetadataEditor.addLink(titleRecordLinkTab.getTitleRecordProcess(),
                titleRecordLinkTab.getSelectedInsertionPosition(), this.processes.get(0).getProcess().getId());
            ProcessService.setParentRelations(titleRecordLinkTab.getTitleRecordProcess(),
                processes.get(0).getProcess());
            String summary = Helper.getTranslation("newProcess.catalogueSearch.linkedToExistingProcessSummary");
            String detail = Helper.getTranslation("newProcess.catalogueSearch.linkedToExistingProcessDetail",
                titleRecordLinkTab.getTitleRecordProcess().getTitle());
            catalogImportDialog.showGrowlMessage(summary, detail);
        } else {
            // add links between consecutive processes in list
            for (int i = 0; i < this.processes.size() - 1; i++) {
                TempProcess tempProcess = this.processes.get(i);
                MetadataEditor.addLink(this.processes.get(i + 1).getProcess(), "0", tempProcess.getProcess().getId());
            }
        }
        ServiceManager.getProcessService().save(getMainProcess(), true);
    }

    /**
     * Save links between child processes and main process.
     *
     * @throws DataException thrown if child process could not be saved
     * @throws IOException thrown if link between child and parent process could not be added
     */
    private void saveChildProcessLinks() throws IOException, DataException {
        this.progress = 0;
        if (Objects.nonNull(PrimeFaces.current()) && Objects.nonNull(FacesContext.getCurrentInstance())) {
            PrimeFaces.current().executeScript("PF('progressDialog')");
            PrimeFaces.current().ajax().update("progressForm:progressBar");
        }
        for (TempProcess childProcess : this.childProcesses) {
            int currentIndex = childProcesses.indexOf(childProcess);
            MetadataEditor.addLink(getMainProcess(), String.valueOf(currentIndex),
                    childProcess.getProcess().getId());
            ServiceManager.getProcessService().save(childProcess.getProcess());
            this.progress = (currentIndex + 1) * 100 / this.childProcesses.size();
            if (Objects.nonNull(PrimeFaces.current()) && Objects.nonNull(FacesContext.getCurrentInstance())) {
                PrimeFaces.current().ajax().update("progressForm:progressBar");
            }
        }
        if (Objects.nonNull(PrimeFaces.current()) && Objects.nonNull(FacesContext.getCurrentInstance())) {
            PrimeFaces.current().executeScript("PF('progressDialog')");
        }
    }

    private void processChildren() {
        // set parent relations between main process and its imported child processes!
        try {
            ImportService.processProcessChildren(getMainProcess(), childProcesses, rulesetManagement, CREATE,
                    priorityList);
        } catch (DataException | InvalidMetadataValueException | NoSuchMetadataFieldException
                | ProcessGenerationException | IOException e) {
            Helper.setErrorMessage("Unable to attach child documents to process: " + e.getMessage());
        }
    }

    private void processAncestors() throws ProcessGenerationException {
        for (TempProcess tempProcess : this.processes) {
            Process process = tempProcess.getProcess();
            // set parent relations between all consecutive process pairs
            int index = processes.indexOf(tempProcess);
            if (index < processes.size() - 1) {
                ProcessService.setParentRelations(processes.get(index + 1).getProcess(), process);
            }
            if (Objects.nonNull(tempProcess.getMetadataNodes())) {
                processTempProcess(tempProcess);
            }
        }
    }

    private void processTempProcess(TempProcess tempProcess) throws ProcessGenerationException {
        try {
            tempProcess.getProcessMetadata().preserve();
            ImportService.processTempProcess(tempProcess, rulesetManagement, CREATE, priorityList, null);
        } catch (InvalidMetadataValueException | NoSuchMetadataFieldException e) {
            throw new ProcessGenerationException("Error creating process hierarchy: invalid metadata found!");
        } catch (RulesetNotFoundException e) {
            throw new ProcessGenerationException(
                    "Ruleset not found:" + tempProcess.getProcess().getRuleset().getTitle());
        } catch (IOException e) {
            throw new ProcessGenerationException("Error reading Ruleset: " + tempProcess.getProcess().getRuleset().getTitle());
        }
    }

    private void saveProcessHierarchyMetadata() {
        // save ancestor processes meta.xml files
        for (TempProcess tempProcess : this.processes) {
            if (this.processes.indexOf(tempProcess) == 0) {
                tempProcess.getProcessMetadata().preserve();
            }
            ProcessHelper.saveTempProcessMetadata(tempProcess, rulesetManagement, CREATE, priorityList);
        }
        // save child processes meta.xml files
        for (TempProcess tempProcess : this.childProcesses) {
            ProcessHelper.saveTempProcessMetadata(tempProcess, rulesetManagement, CREATE, priorityList);
        }
    }

    private boolean createProcessesLocation(List<TempProcess> processes) {
        for (TempProcess tempProcess : processes) {
            if (processes.indexOf(tempProcess) > 0 && Objects.isNull(tempProcess.getMetadataNodes())) {
                // skip creating directories for processes that already exist!
                continue;
            }
            try {
                URI processBaseUri = ServiceManager.getFileService().createProcessLocation(tempProcess.getProcess());
                tempProcess.getProcess().setProcessBaseUri(processBaseUri);
            } catch (IOException | CommandException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
                try {
                    ServiceManager.getProcessService().remove(tempProcess.getProcess());
                } catch (DataException ex) {
                    Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
                }
                return false;
            }
        }
        return true;
    }

    public int getProgress() {
        return this.progress;
    }

    /**
     * Return referring view.
     *
     * @return referring view
     */
    public String getReferringView() {
        return this.referringView;
    }

    /**
     * Check and return whether the given ProcessDetail 'processDetail' is contained in the current list of addable
     * metadata types in the addDocStrucTypeDialog.
     *
     * @param treeNode treeNode to be added
     * @return whether the given ProcessDetail can be added or not
     */
    @Override
    public boolean canBeAdded(TreeNode treeNode) throws InvalidMetadataValueException {
        if (Objects.isNull(treeNode.getParent().getParent())) {
            if (Objects.nonNull(currentProcess.getProcessMetadata().getSelectedMetadataTreeNode())
                    || Objects.isNull(addMetadataDialog.getAddableMetadata())) {
                this.addMetadataDialog.prepareAddableMetadataForStructure();
            }
        } else if (!Objects.equals(currentProcess.getProcessMetadata().getSelectedMetadataTreeNode(),
                treeNode.getParent()) || Objects.isNull(addMetadataDialog.getAddableMetadata())) {
            prepareAddableMetadataForGroup(treeNode.getParent());
        }
        if (Objects.nonNull(addMetadataDialog.getAddableMetadata())) {
            return addMetadataDialog.getAddableMetadata()
                    .stream()
                    .map(SelectItem::getValue)
                    .collect(Collectors.toList())
                    .contains(((ProcessDetail) treeNode.getData()).getMetadataID());
        }
        return false;
    }

    @Override
    public boolean canBeDeleted(ProcessDetail processDetail) {
        return processDetail.getOccurrences() > 1 && processDetail.getOccurrences() > processDetail.getMinOccurs()
                || (!processDetail.isRequired() && !this.rulesetManagement.isAlwaysShowingForKey(processDetail.getMetadataID()));
    }

    /**
     * Check and return whether given TreeNode contains ProcessFieldedMetadata and if any further metadata can
     * be added to it or not.
     *
     * @param metadataNode TreeNode for which the check is performed
     * @return whether given TreeNode contains ProcessFieldedMetadata and if any further metadata can be added to it
     */
    @Override
    public boolean metadataAddableToGroup(TreeNode metadataNode) {
        if (metadataNode.getData() instanceof ProcessFieldedMetadata) {
            return !(DataEditorService.getAddableMetadataForGroup(getMainProcess().getRuleset(), metadataNode).isEmpty());
        }
        return false;
    }

    /**
     * Prepare addable metadata for metadata group.
     * @param treeNode metadataGroup treeNode
     */
    @Override
    public void prepareAddableMetadataForGroup(TreeNode treeNode) {
        addMetadataDialog.prepareAddableMetadataForGroup(getMainProcess().getRuleset(), treeNode);
    }

    /**
     * Get process ancestors.
     *
     * @return process ancestors
     */
    public List<TempProcess> getProcessAncestors() {
        return this.processes;
    }

    /**
     * Get process children.
     *
     * @return process children
     */
    public List<TempProcess> getProcessChildren() {
        return this.childProcesses;
    }

    /**
     * Get value of metadata configured to hold the catalog ID, e.g. "identifierMetadata"
     * (see OPACConfig.getIdentifierMetadata for details).
     *
     * @param tempProcess TempProcess whose ID metadata is returned
     * @return ID metadata
     */
    public String getCatalogId(TempProcess tempProcess) {
        if (Objects.nonNull(tempProcess)) {
            return tempProcess.getCatalogId(rulesetManagement.getFunctionalKeys(FunctionalMetadata.RECORD_IDENTIFIER));
        }
        return " - ";
    }

    /**
     * Fill metadata fields in metadata tab with metadata values of given temp process on successful import.
     * @param tempProcess TempProcess for which metadata is displayed
     */
    public void fillCreateProcessForm(TempProcess tempProcess)
            throws ProcessGenerationException, IOException {
        if (Objects.nonNull(tempProcess)) {
            currentProcess = tempProcess;
            if (Objects.nonNull(tempProcess.getWorkpiece())
                    && Objects.nonNull(tempProcess.getWorkpiece().getLogicalStructure())
                    && Objects.nonNull(tempProcess.getWorkpiece().getLogicalStructure().getType())) {
                tempProcess.verifyDocType();
                processDataTab.setDocType(tempProcess.getWorkpiece().getLogicalStructure().getType());
                processDataTab.updateProcessMetadata();
            }
        }
    }

    /**
     * Get currentProcess.
     *
     * @return value of currentProcess
     */
    public TempProcess getCurrentProcess() {
        return currentProcess;
    }

    /**
     * Set currentProcess.
     *
     * @param currentProcess as org.kitodo.production.helper.TempProcess
     */
    public void setCurrentProcess(TempProcess currentProcess) {
        this.currentProcess = currentProcess;
    }

    /**
     * Check whether ruleset configuration is complete for OPAC import, e.g. if functional metadata of type
     * 'recordIdentifier' has been configured for all document types in this ruleset.
     * If configuration is complete, the import dialog is shown. Otherwise, a warning dialog is shown to inform the user
     * about missing ruleset configurations.
     */
    public void checkRulesetConfiguration() {
        if (Objects.isNull(rulesetConfigurationForOpacImportComplete)) {
            rulesetConfigurationForOpacImportComplete = ServiceManager.getImportService()
                    .isRecordIdentifierMetadataConfigured(rulesetManagement);
        }
        if (rulesetConfigurationForOpacImportComplete) {
            PrimeFaces.current().executeScript("PF('catalogSearchDialog').show();");
        } else {
            PrimeFaces.current().executeScript("PF('recordIdentifierMissingDialog').show();");
        }
    }
    
    /**
     * Returns the details of the missing record identifier error.
     * 
     * @return the details as a list of error description
     */
    public Collection<RecordIdentifierMissingDetail> getDetailsOfRecordIdentifierMissingError() {
        return ServiceManager.getImportService().getDetailsOfRecordIdentifierMissingError();
    }

    /**
     * Set the current import configuration.
     *
     * @param currentImportConfiguration current import configuration
     */
    public void setCurrentImportConfiguration(ImportConfiguration currentImportConfiguration) {
        this.currentImportConfiguration = currentImportConfiguration;
        if (Objects.nonNull(currentImportConfiguration)) {
            if (ImportConfigurationType.OPAC_SEARCH.name().equals(currentImportConfiguration.getConfigurationType())) {
                catalogImportDialog.setImportDepth(ImportService.getDefaultImportDepth(currentImportConfiguration));
                catalogImportDialog.setSelectedField(ImportService.getDefaultSearchField(currentImportConfiguration));
            }
            else if (ImportConfigurationType.PROCESS_TEMPLATE.name().equals(currentImportConfiguration.getConfigurationType())) {
                searchDialog.setOriginalProcess(currentImportConfiguration.getDefaultTemplateProcess());
            }
        }
    }

    /**
     * Get the current ImportConfiguration.
     *
     * @return current ImportConfiguration
     */
    public ImportConfiguration getCurrentImportConfiguration() {
        return currentImportConfiguration;
    }

    private boolean saveChildProcesses() {
        return ((Objects.nonNull(currentImportConfiguration)
                && MetadataFormat.EAD.name().equals(currentImportConfiguration.getMetadataFormat()))
                || catalogImportDialog.isImportChildren());
    }

    /**
     * Get selected ead level.
     *
     * @return selected ead level
     */
    public String getSelectedEadLevel() {
        return selectedEadLevel;
    }

    /**
     * Set selected ead level.
     *
     * @param selectedEadLevel as String
     */
    public void setSelectedEadLevel(String selectedEadLevel) {
        this.selectedEadLevel = selectedEadLevel;
    }

    /**
     * Get selected parent ead level.
     *
     * @return selected parent ead level
     */
    public String getSelectedParentEadLevel() {
        return selectedParentEadLevel;
    }

    /**
     * Set selected parent ead level.
     *
     * @param selectedParentEadLevel as String
     */
    public void setSelectedParentEadLevel(String selectedParentEadLevel) {
        this.selectedParentEadLevel = selectedParentEadLevel;
    }

    /**
     * Get ead levels.
     *
     * @return ead levels
     */
    public List<String> getEadLevels() {
        return eadLevels;
    }

    /**
     * Get parent ead levels.
     *
     * @return parent ead levels
     */
    public List<String> getEadParentLevels() {
        return eadParentLevels;
    }

    /**
     * Get xmlString.
     *
     * @return xmlString
     */
    public String getXmlString() {
        return xmlString;
    }

    /**
     * Set xmlString.
     *
     * @param xmlString as String
     */
    public void setXmlString(String xmlString) {
        this.xmlString = xmlString;
    }

    /**
     * Get filename.
     *
     * @return filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Set filename.
     *
     * @param filename as String
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    protected boolean limitExceeded(String xmlString) throws XMLStreamException {
        return MetadataFormat.EAD.name().equals(currentImportConfiguration.getMetadataFormat())
                && ServiceManager.getImportService().isMaxNumberOfRecordsExceeded(xmlString, selectedEadLevel);
    }

    /**
     * Get and return message that informs the user that the maximum number of records that can be processed in the GUI
     * has been exceeded.
     *
     * @return maximum number exceeded message
     */
    public String getMaxNumberOfRecordsExceededMessage() {
        return ImportService.getMaximumNumberOfRecordsExceededMessage(selectedEadLevel, numberOfEadElements);
    }

    /**
     * Start background task importing processes from uploaded XML file and redirect either to task manager or desktop,
     * depending on user permissions.
     *
     * @return String containing URL of either task manager or desktop page, depending on user permissions
     */
    public String importRecordsInBackground() {
        User user = ServiceManager.getUserService().getAuthenticatedUser();
        Client client = ServiceManager.getUserService().getSessionClientOfAuthenticatedUser();
        TaskManager.addTask(new ImportEadProcessesThread(this, user, client));
        if (ServiceManager.getSecurityAccessService().hasAuthorityToViewTaskManagerPage()) {
            return "system.jsf?tabIndex=0&faces-redirect=true";
        } else {
            return "desktop.jsf?faces-redirect=true";
        }
    }
}
