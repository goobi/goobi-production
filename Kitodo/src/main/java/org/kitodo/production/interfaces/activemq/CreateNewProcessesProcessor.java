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

package org.kitodo.production.interfaces.activemq;

import static org.kitodo.constants.StringConstants.CREATE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Objects;
import java.util.Set;

import javax.jms.JMSException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoRecordFoundException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.exceptions.ProcessorException;
import org.kitodo.exceptions.UnsupportedFormatException;
import org.kitodo.production.forms.createprocess.ProcessFieldedMetadata;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.ProcessHelper;
import org.kitodo.production.helper.TempProcess;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.process.ProcessGenerator;
import org.kitodo.production.process.ProcessValidator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ImportService;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.data.RulesetService;
import org.kitodo.production.services.data.TaskService;
import org.kitodo.production.services.dataformat.MetsService;
import org.kitodo.production.services.file.FileService;
import org.xml.sax.SAXException;

/**
 * An Active MQ service interface to create new processes.
 */
public class CreateNewProcessesProcessor extends ActiveMQProcessor {
    private static final Logger logger = LogManager.getLogger(CreateNewProcessesProcessor.class);

    private static final int IMPORT_WITHOUT_ANY_HIERARCHY = 1;
    private static final String LAST_CHILD = Integer.toString(-1);
    private static final List<LanguageRange> METADATA_LANGUAGE = Locale.LanguageRange.parse("en");

    private final FileService fileService = ServiceManager.getFileService();
    private final ImportService importService = ServiceManager.getImportService();
    private final MetsService metsService = ServiceManager.getMetsService();
    private final ProcessService processService = ServiceManager.getProcessService();
    private final RulesetService rulesetService = ServiceManager.getRulesetService();
    private final TaskService taskService = ServiceManager.getTaskService();

    private RulesetManagementInterface rulesetManagement;

    /**
     * The default constructor looks up the queue name to use in
     * kitodo_config.properties. If that is not configured and “null” is passed
     * to the super constructor, this will prevent
     * ActiveMQDirector.registerListeners() from starting this service.
     */
    public CreateNewProcessesProcessor() {
        super(ConfigCore.getOptionalString(ParameterCore.ACTIVE_MQ_CREATE_NEW_PROCESSES_QUEUE).orElse(null));
    }

    /* The main routine processing incoming tickets. The function has been
     * divided into three parts so that it is not too long. */
    @Override
    protected void process(MapMessageObjectReader ticket) throws ProcessorException, JMSException {
        try {
            CreateNewProcessOrder order = new CreateNewProcessOrder(ticket);
            rulesetManagement = rulesetService.openRuleset(order.getTemplate().getRuleset());
            TempProcess tempProcess = obtainTempProcess(order);
            Process process = tempProcess.getProcess();
            Process parentProcess = formProcessTitle(order, tempProcess, process);
            createProcess(tempProcess, process, parentProcess);

        } catch (CommandException | CustomResponseException | DataException | DAOException
                | InvalidMetadataValueException | IOException | NoRecordFoundException | NoSuchMetadataFieldException
                | ParserConfigurationException | ProcessGenerationException | SAXException | TransformerException
                | UnsupportedFormatException | URISyntaxException | XPathExpressionException e) {
            throw new ProcessorException(e.getMessage());
        }
    }

    /* In the first part of the processing routine, the process is either
     * created without import, or it is imported. The existing data is then
     * added. */
    private TempProcess obtainTempProcess(CreateNewProcessOrder order) throws DAOException,
            InvalidMetadataValueException,
            IOException, NoRecordFoundException, NoSuchMetadataFieldException, ParserConfigurationException,
            ProcessGenerationException, ProcessorException, SAXException, TransformerException,
            UnsupportedFormatException, URISyntaxException, XPathExpressionException {

        if (order.getImports().isEmpty()) {
            ProcessGenerator processGenerator = new ProcessGenerator();
            processGenerator.generateProcess(order.getTemplateId(), order.getProjectId());
            TempProcess tempProcess = new TempProcess(processGenerator.getGeneratedProcess(), new Workpiece());
            tempProcess.getWorkpiece().getLogicalStructure().getMetadata().addAll(order.getMetadata());
            tempProcess.verifyDocType();
            return tempProcess;
        } else {
            TempProcess tempProcess = importProcess(order, 0);
            tempProcess.getWorkpiece().getLogicalStructure().getMetadata().addAll(order.getMetadata());
            tempProcess.verifyDocType();
            for (int which = 1; which < order.getImports().size(); which++) {
                TempProcess repeatedImport = importProcess(order, which);
                Set<Metadata> metadata = repeatedImport.getWorkpiece().getLogicalStructure().getMetadata();
                rulesetManagement.updateMetadata(tempProcess.getWorkpiece().getLogicalStructure().getType(),
                    tempProcess.getWorkpiece().getLogicalStructure().getMetadata(), CREATE, metadata);
            }
            return tempProcess;
        }
    }

    /**
     * Imports a dataset with an import configuration.
     * 
     * @param order
     *            order for creating the process
     * @param which
     *            which dataset should be imported
     * @return the imported dataset
     */
    private TempProcess importProcess(CreateNewProcessOrder order, int which) throws DAOException,
            InvalidMetadataValueException, IOException, NoRecordFoundException, NoSuchMetadataFieldException,
            ParserConfigurationException, ProcessGenerationException, ProcessorException, SAXException,
            TransformerException, UnsupportedFormatException, URISyntaxException, XPathExpressionException {

        List<TempProcess> processHierarchy = importService.importProcessHierarchy(
                order.getImports().get(which).getValue(), order.getImports().get(which).getKey(),
                order.getProjectId(), order.getTemplateId(), IMPORT_WITHOUT_ANY_HIERARCHY,
                rulesetManagement.getFunctionalKeys(FunctionalMetadata.HIGHERLEVEL_IDENTIFIER));
        if (processHierarchy.size() == 0) {
            throw new ProcessorException("Process was not imported");
        } else if (processHierarchy.size() > 1) {
            throw new ProcessorException(processHierarchy.size() + " processes were imported");
        }
        return processHierarchy.get(0);
    }

    /* In the second and middle part of the processing routine, the process
     * title is generated. */
    private Process formProcessTitle(CreateNewProcessOrder order, TempProcess tempProcess, Process process)
            throws DAOException, ProcessGenerationException, ProcessorException {

        ProcessFieldedMetadata processDetails = ProcessHelper.initializeProcessDetails(tempProcess.getWorkpiece()
                .getLogicalStructure(), rulesetManagement, CREATE, METADATA_LANGUAGE);
        Process parentProcess = order.getParent();
        ProcessHelper.generateAtstslFields(tempProcess, processDetails.getRows(), Collections.emptyList(), tempProcess
                .getWorkpiece().getLogicalStructure().getType(), rulesetManagement, CREATE, METADATA_LANGUAGE,
                parentProcess, true);
        if (order.getTitle().isPresent()) {
            process.setTitle(order.getTitle().get());
        }
        if (!ProcessValidator.isProcessTitleCorrect(process.getTitle())) {
            throw new ProcessorException(Helper.getTranslation("processTitleAlreadyInUse", process.getTitle()));
        }
        return parentProcess;
    }

    /* In the third and final part of the processing routine, the process is
     * created and saved. */
    private void createProcess(TempProcess tempProcess, Process process, Process parentProcess) throws DataException,
            CustomResponseException, IOException, CommandException {

        saveProcess(process);
        fileService.createProcessLocation(process);
        metsService.saveWorkpiece(tempProcess.getWorkpiece(), processService.getMetadataFileUri(process));
        if (Objects.nonNull(parentProcess)) {
            MetadataEditor.addLink(parentProcess, LAST_CHILD, process.getId());
            process.setParent(parentProcess);
            parentProcess.getChildren().add(process);
            saveProcess(process);
            saveProcess(parentProcess);
        }
    }

    /**
     * When the process is saved, the tasks are also indexed.
     * 
     * @param process
     *            process to be saved
     */
    private void saveProcess(Process process) throws DataException, CustomResponseException, IOException {
        processService.save(process, true);
        for (Task task : process.getTasks()) {
            taskService.saveToIndex(task, true);
        }
    }
}
