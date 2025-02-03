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

package org.kitodo.production.services.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kitodo.MockDatabase;
import org.kitodo.SecurityTestUtils;
import org.kitodo.data.database.beans.Template;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.dto.TemplateDTO;
import org.kitodo.production.services.ServiceManager;

public class TemplateServiceIT {

    private static final TemplateService templateService = ServiceManager.getTemplateService();

    @BeforeAll
    public static void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
        SecurityTestUtils.addUserDataToSecurityContext(ServiceManager.getUserService().getById(1), 1);
    }

    @AfterAll
    public static void cleanDatabase() throws Exception {
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    @Test
    public void shouldCountAllTemplates() throws Exception {
        Long amount = templateService.count();
        assertEquals(Long.valueOf(4), amount, "Templates were not counted correctly!");
    }

    @Test
    public void shouldFindAll() throws Exception {
        List<TemplateDTO> templates = templateService.findAll();
        assertEquals(4, templates.size(), "Found incorrect amount of templates!");
    }

    @Test
    public void shouldGetTemplate() throws Exception {
        Template template = templateService.getById(1);
        boolean condition = template.getTitle().equals("First template") && template.getId().equals(1);
        assertTrue(condition, "Template was not found in database!");

        assertEquals(2, template.getProcesses().size(), "Template was found but processes were not inserted!");
        assertEquals(5, template.getTasks().size(), "Template was found but tasks were not inserted!");
    }

    @Test
    public void shouldGetTemplates() throws Exception {
        List<Template> templates = templateService.getAll();
        assertEquals(4, templates.size(), "Found incorrect amount of templates!");
    }

    @Test
    public void shouldGetTemplatesWithTitle() {
        List<Template> templates = templateService.getProcessTemplatesWithTitle("First template");
        assertEquals(1, templates.size(), "Incorrect size of templates with given title!");
    }

    @Test
    public void shouldCheckForUnreachableTasks() throws Exception {
        Template template = templateService.getById(1);
        assertDoesNotThrow(() -> templateService.checkForUnreachableTasks(template.getTasks()));
    }

    @Test
    public void shouldCheckForUnreachableTasksWithException() throws Exception {
        Template template = templateService.getById(3);
        assertThrows(ProcessGenerationException.class, () -> templateService.checkForUnreachableTasks(template.getTasks()));
    }

    @Test
    public void shouldHasCompleteTasks() throws Exception {
        TemplateDTO templateDTO = templateService.findById(1);
        boolean condition = templateService.hasCompleteTasks(templateDTO.getTasks());
        assertTrue(condition, "Process DTO doesn't have complete tasks!");

        templateDTO = templateService.findById(3);
        condition = templateService.hasCompleteTasks(templateDTO.getTasks());
        assertFalse(condition, "Process DTO has complete tasks!");
    }

    @Test
    public void shouldCorrectlyDetermineTemplateUsage() throws Exception {
        Map<Integer, Boolean> templateUsageMap = templateService.getTemplateUsageMap();
        Map<Integer, Boolean> expectedMap = Map.of(
                1, true,
                2, false,
                3, false,
                4, false
        );
        // Assert that the generated map matches the expected map
        assertEquals(expectedMap, templateUsageMap, "The template usage map does not match expected results.");
    }
}
