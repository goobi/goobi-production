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

package org.kitodo.dataeditor.ruleset;

import static org.kitodo.constants.StringConstants.CREATE;
import static org.kitodo.constants.StringConstants.EDIT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.kitodo.api.MdSec;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.ComplexMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.DatesSimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.Domain;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalDivision;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.InputType;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewWithValuesInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;

/**
 * Here, the ruleset management is put through its paces.
 */
public class RulesetManagementIT {

    private static final String BOOK = "book";
    private static final String HELLO_WORLD = "Hello World!";
    private static final String OPT = "opt1";
    private static final String TEST = "test";

    /**
     * English as the only language. This is the default case we use all the
     * time, except for tests that explicitly refer to the language.
     */
    private static final List<LanguageRange> ENGL = LanguageRange.parse("en");

    /**
     * This test checks if a ruleset can be loaded from a file. It loads a
     * complex rule set that contains all sorts of possible cases to test
     * whether the parser has been implemented correctly. The only purpose of
     * this test is that the load command does not throw an exception.
     *
     * <p>
     * The ruleset for this test is more practice-oriented and also includes
     * documentation, if you are looking for it.
     */
    @Test
    public void testAnExtensiveRulesetCanBeLoaded() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testAnExtensiveRulesetCanBeLoaded.xml"));
    }

    /**
     * The ruleset contains presets. This test checks if the presets in the view
     * can be retrieved. That needs to be tested for the different codomain
     * types.
     */
    @Test
    public void testAvailabilityOfPresets() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testAvailabilityOfPresets.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Arrays.asList("defaultStringKey", "anyURIKey", "booleanKey", "dateKey", "namespaceDefaultAnyURIKey",
                "namespaceStringKey"));

        MetadataViewInterface booleanMvi = mvwviList.get(0).getMetadata().get();
        assertFalse(booleanMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, booleanMvi);
        SimpleMetadataViewInterface booleanSmvi = (SimpleMetadataViewInterface) booleanMvi;
        assertTrue(booleanSmvi.getBooleanDefaultValue());

        MetadataViewInterface dateMvi = mvwviList.get(1).getMetadata().get();
        assertFalse(dateMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, dateMvi);
        SimpleMetadataViewInterface dateSmvi = (SimpleMetadataViewInterface) dateMvi;
        assertEquals("1993-09-01", dateSmvi.getDefaultValue());

        MetadataViewInterface defaultStringMvi = mvwviList.get(2).getMetadata().get();
        assertFalse(defaultStringMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, defaultStringMvi);
        SimpleMetadataViewInterface defaultStringSmvi = (SimpleMetadataViewInterface) defaultStringMvi;
        assertEquals(HELLO_WORLD, defaultStringSmvi.getDefaultValue());

        MetadataViewInterface namespaceDefaultAnyURIMvi = mvwviList.get(3).getMetadata().get();
        assertFalse(namespaceDefaultAnyURIMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, namespaceDefaultAnyURIMvi);
        SimpleMetadataViewInterface namespaceDefaultAnyURISmvi = (SimpleMetadataViewInterface) namespaceDefaultAnyURIMvi;
        assertEquals("http://test.example/non-existent-namespace/", namespaceDefaultAnyURISmvi.getDefaultValue());

        MetadataViewInterface namespaceStringMvi = mvwviList.get(4).getMetadata().get();
        assertFalse(namespaceStringMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, namespaceStringMvi);
        SimpleMetadataViewInterface namespaceStringSmvi = (SimpleMetadataViewInterface) namespaceStringMvi;
        assertEquals("http://test.example/non-existent-namespace/", namespaceStringSmvi.getDefaultValue());

        MetadataViewInterface anyURIMvi = mvwviList.get(5).getMetadata().get();
        assertFalse(anyURIMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, anyURIMvi);
        SimpleMetadataViewInterface anyURISmvi = (SimpleMetadataViewInterface) anyURIMvi;
        assertEquals("https://en.wikipedia.org/wiki/%22Hello,_World!%22_program", anyURISmvi.getDefaultValue());
    }

    /**
     * Check that a subkey view is available for subkeys.
     */
    @Test
    public void testAvailabilityOfSubkeyViews() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testAvailabilityOfSubkeyViews.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.singletonList("contributor"));

        MetadataViewInterface contributorMvi = mvwviList.get(0).getMetadata().get();
        assertEquals("contributor", contributorMvi.getId());
        assertEquals("Contributor ‹person›", contributorMvi.getLabel());
        assertTrue(contributorMvi.isComplex());
        assertInstanceOf(ComplexMetadataViewInterface.class, contributorMvi);
        ComplexMetadataViewInterface contributorCmvi = (ComplexMetadataViewInterface) contributorMvi;

        Collection<MetadataViewInterface> mvic = contributorCmvi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());

        Iterator<MetadataViewInterface> mvici = mvic.iterator();
        for (int i = 0; i < mvic.size(); i++) {
            if (!mvici.hasNext()) {
                break;
            }
            MetadataViewInterface mvi = mvici.next();

            assertFalse(mvi.isComplex());
            assertInstanceOf(SimpleMetadataViewInterface.class, mvi);
            SimpleMetadataViewInterface smvi = (SimpleMetadataViewInterface) mvi;

            /*
             * We do not have a restriction rule here, so the subkeys appear in
             * alphabetical order after the translated label. That’s wanted.
             */
            switch (i) {
                case 0:
                    assertEquals("givenName", smvi.getId());
                    assertEquals("Given name", smvi.getLabel());
                    break;
                case 1:
                    assertEquals("role", smvi.getId());
                    assertEquals("Role", smvi.getLabel());
                    assertThat(smvi.getSelectItems(Collections.emptyList()), hasEntry("aut", "Author"));
                    assertThat(smvi.getSelectItems(Collections.emptyList()), hasEntry("edt", "Editor"));
                    break;
                case 2:
                    assertEquals("surname", smvi.getId());
                    assertEquals("Surname", smvi.getLabel());
                    break;
                default:
                    fail("Too many elements in the view!");
            }
        }
    }

    /**
     * Checks if the options are returned correctly. On the one hand there is
     * the translation, on the other hand the DisplayMode has to be set to
     * MULTI_LINE_SINGLE_SELECTION, MULTIPLE_SELECTION or
     * ONE_LINE_SINGLE_SELECTION accordingly. In cases
     * MULTI_LINE_SINGLE_SELECTION and ONE_LINE_SINGLE_SELECTION, the
     * distinction is made as to whether minOccurs is 1 or not, if not, there
     * must not be an empty selection element for no input, otherwise not.
     */
    @Test
    public void testCorrectReturnOfOptions() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testCorrectReturnOfOptions.xml"));

        // 1. options are sorted as to their labels alphabetically
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.emptyList());

        StructuralElementViewInterface seviDe = underTest.getStructuralElementView(BOOK, "",
            LanguageRange.parse("de"));
        List<MetadataViewWithValuesInterface> mvwviListDe = seviDe.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.emptyList());

        assertThat(
            ((SimpleMetadataViewInterface) mvwviList.get(0).getMetadata().get()).getSelectItems(Collections.emptyList())
                    .keySet(),
            contains("dan", "dut", "eng", "fre", "ger"));
        assertThat(
            ((SimpleMetadataViewInterface) mvwviListDe.get(0).getMetadata().get())
                    .getSelectItems(Collections.emptyList()).keySet(),
            contains("dan", "ger", "eng", "fre", "dut"));

        // 2. The input elements that have a minimum occurrence greater than
        // zero will appear on the display by themselves, the others may be
        // added.
        assertThat(ids(mvwviList), contains("mandatoryMultiLineSingleSelection", "mandatoryOneLineSingleSelection",
            "mandatoryMultipleSelection"));

        Collection<MetadataViewInterface> mviColl = sevi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());

        assertThat(ids(mviColl), contains("optionalMultiLineSingleSelection", "optionalOneLineSingleSelection",
            "CONTENTIDS", "LABEL", "ORDERLABEL", "optionalMultipleSelection"));

        // 3. The input types have been calculated correctly
        assertThat(
            mvwviList.stream().map(mvwvi -> ((SimpleMetadataViewInterface) mvwvi.getMetadata().get()).getInputType())
                    .collect(Collectors.toList()),
            contains(InputType.MULTI_LINE_SINGLE_SELECTION, InputType.ONE_LINE_SINGLE_SELECTION,
                InputType.MULTIPLE_SELECTION));

        assertThat(
            mviColl.stream().map(mvi -> ((SimpleMetadataViewInterface) mvi).getInputType())
                    .collect(Collectors.toList()),
            contains(InputType.MULTI_LINE_SINGLE_SELECTION, InputType.ONE_LINE_SINGLE_SELECTION,
                InputType.ONE_LINE_TEXT, InputType.ONE_LINE_TEXT, InputType.ONE_LINE_TEXT,
                InputType.MULTIPLE_SELECTION));
    }

    /**
     * The test verifies that only the options that are valid according to the
     * ruleset are valid.
     */
    @Test
    public void testCorrectValidationOfOptions() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testCorrectValidationOfOptions.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        Collection<MetadataViewInterface> mviColl = sevi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());
        SimpleMetadataViewInterface smvi = (SimpleMetadataViewInterface) mviColl.iterator().next();
        assertTrue(smvi.isValid(OPT, Collections.emptyList()));
        assertFalse(smvi.isValid(StringUtils.capitalize(OPT), Collections.emptyList()));
    }

    /**
     * The test checks if the validation by regular expressions works.
     */
    @Test
    public void testCorrectValidationOfRegularExpressions() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testCorrectValidationOfRegularExpressions.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Arrays.asList("defaultStringKey", "anyURIKey", "booleanKey", "dateKey", "namespaceDefaultAnyURIKey",
                "namespaceStringKey", "integerKey", "optionsKey"));

        MetadataViewInterface booleanMvi = mvwviList.get(0).getMetadata().get();
        assertFalse(booleanMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, booleanMvi);
        SimpleMetadataViewInterface booleanSmvi = (SimpleMetadataViewInterface) booleanMvi;
        assertTrue(booleanSmvi.isValid(booleanSmvi.convertBoolean(true).get(), Collections.emptyList()));
        assertFalse(booleanSmvi.isValid("", Collections.emptyList()));
        assertFalse(booleanSmvi.isValid("botch", Collections.emptyList()));

        MetadataViewInterface dateMvi = mvwviList.get(1).getMetadata().get();
        assertFalse(dateMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, dateMvi);
        SimpleMetadataViewInterface dateSmvi = (SimpleMetadataViewInterface) dateMvi;
        assertFalse(dateSmvi.isValid("", Collections.emptyList()));
        assertFalse(dateSmvi.isValid("1803-05-12", Collections.emptyList()));
        assertTrue(dateSmvi.isValid("1993-09-01", Collections.emptyList()));

        MetadataViewInterface defaultStringMvi = mvwviList.get(2).getMetadata().get();
        assertFalse(defaultStringMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, defaultStringMvi);
        SimpleMetadataViewInterface defaultStringSmvi = (SimpleMetadataViewInterface) defaultStringMvi;
        assertFalse(defaultStringSmvi.isValid(HELLO_WORLD, Collections.emptyList()));
        assertTrue(defaultStringSmvi.isValid("1234567X", Collections.emptyList()));

        MetadataViewInterface integerMvi = mvwviList.get(3).getMetadata().get();
        assertFalse(integerMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, integerMvi);
        SimpleMetadataViewInterface integerSmvi = (SimpleMetadataViewInterface) integerMvi;
        assertFalse(integerSmvi.isValid("22", Collections.emptyList()));
        assertTrue(integerSmvi.isValid("1748", Collections.emptyList()));

        MetadataViewInterface namespaceDefaultAnyURIMvi = mvwviList.get(4).getMetadata().get();
        assertFalse(namespaceDefaultAnyURIMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, namespaceDefaultAnyURIMvi);
        SimpleMetadataViewInterface namespaceDefaultAnyURISmvi = (SimpleMetadataViewInterface) namespaceDefaultAnyURIMvi;
        assertFalse(namespaceDefaultAnyURISmvi
                .isValid("http://test.example/non-existent-namespace/%22Hello,_World!%22_program",
                    Collections.emptyList()));
        assertTrue(
            namespaceDefaultAnyURISmvi.isValid("http://test.example/non-existent-namespace/Hello_World_program",
                Collections.emptyList()));

        MetadataViewInterface namespaceStringMvi = mvwviList.get(5).getMetadata().get();
        assertFalse(namespaceStringMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, namespaceStringMvi);
        SimpleMetadataViewInterface namespaceStringSmvi = (SimpleMetadataViewInterface) namespaceStringMvi;
        assertFalse(
            namespaceStringSmvi.isValid("http://test.example/non-existent-namespace/%22Hello,_World!%22_program",
                Collections.emptyList()));
        assertTrue(namespaceStringSmvi.isValid("http://test.example/non-existent-namespace/Hello_World_program",
            Collections.emptyList()));

        MetadataViewInterface optionsMvi = mvwviList.get(6).getMetadata().get();
        assertFalse(optionsMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, optionsMvi);
        SimpleMetadataViewInterface optionsSmvi = (SimpleMetadataViewInterface) optionsMvi;
        assertFalse(optionsSmvi.isValid("opt88", Collections.emptyList()));
        assertTrue(optionsSmvi.isValid("opt2", Collections.emptyList()));

        MetadataViewInterface anyURIMvi = mvwviList.get(7).getMetadata().get();
        assertFalse(anyURIMvi.isComplex());
        assertInstanceOf(SimpleMetadataViewInterface.class, anyURIMvi);
        SimpleMetadataViewInterface anyURISmvi = (SimpleMetadataViewInterface) anyURIMvi;
        assertFalse(anyURISmvi.isValid("mailto:e-mail@example.org", Collections.emptyList()));
        assertTrue(
            anyURISmvi.isValid("https://en.wikipedia.org/wiki/%22Hello,_World!%22_program", Collections.emptyList()));
    }

    /**
     * This test verifies that acquisition stage edit settings correctly
     * override the common edit settings.
     */
    @Test
    public void testDisplaySettingsWithAcquisitionStage() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testDisplaySettingsWithAcquisitionStage.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "The acquisition stage", ENGL);

        // always showing
        List<MetadataViewWithValuesInterface> mvwviListAlwaysShowing = sevi
                .getSortedVisibleMetadata(Collections.emptyList(), Collections.emptyList());
        assertThat(
            mvwviListAlwaysShowing.stream().map(mvwvi -> mvwvi.getMetadata().get().getId())
                    .collect(Collectors.toList()),
            containsInAnyOrder("alwaysShowingUnchangedTrue", "alwaysShowingTrueUnchanged",
                "alwaysShowingTrueOtherchanges", "alwaysShowingTrueTrue", "multilineTrueOtherchanges"));

        // excluded
        Collection<Metadata> metadataForExcluded = new ArrayList<>();
        MetadataEntry metadataOne = new MetadataEntry();
        metadataOne.setKey("excludedUnchangedTrue");
        metadataOne.setValue("exclude1");
        metadataForExcluded.add(metadataOne);
        MetadataEntry metadataTwo = new MetadataEntry();
        metadataTwo.setKey("excludedTrueUnchanged");
        metadataTwo.setValue("exclude2");
        metadataForExcluded.add(metadataTwo);
        MetadataEntry metadataThree = new MetadataEntry();
        metadataThree.setKey("excludedTrueOtherchanges");
        metadataThree.setValue("exclude3");
        metadataForExcluded.add(metadataThree);
        MetadataEntry metadataFour = new MetadataEntry();
        metadataFour.setKey("excludedTrueTrue");
        metadataFour.setValue("exclude4");
        metadataForExcluded.add(metadataFour);
        Metadata metadataFive = new MetadataEntry();
        metadataFive.setKey("excludedTrueFalse");
        metadataForExcluded.add(metadataFive);
        List<MetadataViewWithValuesInterface> mvwviListExcluded = sevi
                .getSortedVisibleMetadata(metadataForExcluded, Collections.emptyList());
        assertTrue(mvwviListExcluded.stream().filter(mvwvi -> mvwvi.getMetadata().isPresent())
                .map(mvwvi -> mvwvi.getMetadata().get().getId()).filter(keyId -> keyId.startsWith("excluded"))
                .collect(Collectors.toList()).contains("excludedTrueFalse"));
        assertThat(
            mvwviListExcluded.stream().filter(mvwvi -> !mvwvi.getMetadata().isPresent())
                    .flatMap(
                        mvwvi -> mvwvi.getValues().stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue))
                    .collect(Collectors.toList()),
            containsInAnyOrder("exclude1", "exclude2", "exclude3", "exclude4"));
        Collection<MetadataViewInterface> mviCollExcluded = sevi.getAddableMetadata(metadataForExcluded,
            Collections.emptyList());
        assertTrue(mviCollExcluded.stream().map(mvi -> mvi.getId()).filter(keyId -> keyId.startsWith("excluded"))
                .collect(Collectors.toList()).contains("excludedTrueFalse"));

        // editable
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Arrays.asList("editableUnchangedFalse", "editableFalseUnchanged", "editableFalseOtherchanges",
                "editableFalseFalse", "editableFalseTrue", "multilineUnchangedTrue", "multilineTrueUnchanged",
                "multilineTrueOtherchanges", "multilineTrueTrue", "multilineTrueFalse"));

        SimpleMetadataViewInterface editableUnchangedFalse = getSmvi(mvwviList, "editableUnchangedFalse");
        assertFalse(editableUnchangedFalse.isEditable());
        SimpleMetadataViewInterface editableFalseUnchanged = getSmvi(mvwviList, "editableFalseUnchanged");
        assertFalse(editableFalseUnchanged.isEditable());
        SimpleMetadataViewInterface editableFalseOtherChanges = getSmvi(mvwviList, "editableFalseOtherchanges");
        assertFalse(editableFalseOtherChanges.isEditable());
        SimpleMetadataViewInterface editableFalseFalse = getSmvi(mvwviList, "editableFalseFalse");
        assertFalse(editableFalseFalse.isEditable());
        SimpleMetadataViewInterface editableFalseTrue = getSmvi(mvwviList, "editableFalseTrue");
        assertTrue(editableFalseTrue.isEditable());

        // multi-line
        SimpleMetadataViewInterface multilineUnchangedTrue = getSmvi(mvwviList, "multilineUnchangedTrue");
        assertEquals(InputType.MULTI_LINE_TEXT, multilineUnchangedTrue.getInputType());
        SimpleMetadataViewInterface multilineTrueUnchanged = getSmvi(mvwviList, "multilineTrueUnchanged");
        assertEquals(InputType.MULTI_LINE_TEXT, multilineTrueUnchanged.getInputType());
        SimpleMetadataViewInterface multilineTrueOtherchanges = getSmvi(mvwviList, "multilineTrueOtherchanges");
        assertEquals(InputType.MULTI_LINE_TEXT, multilineTrueOtherchanges.getInputType());
        SimpleMetadataViewInterface multilineTrueTrue = getSmvi(mvwviList, "multilineTrueTrue");
        assertEquals(InputType.MULTI_LINE_TEXT, multilineTrueTrue.getInputType());
        SimpleMetadataViewInterface multilineTrueFalse = getSmvi(mvwviList, "multilineTrueFalse");
        assertEquals(InputType.ONE_LINE_TEXT, multilineTrueFalse.getInputType());
    }

    /**
     * This test verifies the working of the editing settings, including their
     * interaction and nesting.
     */
    @Test
    public void testDisplaySettingsWithoutAcquisitionStage() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testDisplaySettingsWithoutAcquisitionStage.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);

        // 1. Without metadata, and without additional fields being selected,
        // you should see exactly the fields that are always showing, minus
        // those that are excluded (excluded overrules always showing).
        List<MetadataViewWithValuesInterface> mvwviListNoMetadata = sevi
                .getSortedVisibleMetadata(Collections.emptyList(), Collections.emptyList());
        assertThat(ids(mvwviListNoMetadata),
            containsInAnyOrder("testAlwaysShowing", "testAlwaysShowingEditable", "testAlwaysShowingMultiline"));

        // 2. All fields except those that are excluded should be allowed to be
        // added.
        Collection<MetadataViewInterface> mviCollNoMetadata = sevi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());
        assertThat(ids(mviCollNoMetadata), containsInAnyOrder("testAlwaysShowing", "testEditable", "testMultiline",
            "testAlwaysShowingEditable", "testAlwaysShowingMultiline", "testEditableMultiline", "testNestedSettings",
            "CONTENTIDS", "LABEL", "ORDERLABEL"));

        // 1a. In the nested metadata field, the only value that should be
        // visible is the one marked as always showing. The field has to be
        // added first:
        List<MetadataViewWithValuesInterface> mvwviListNestedSettings = sevi
                .getSortedVisibleMetadata(Collections.emptyList(), Collections.singletonList("testNestedSettings"));
        ComplexMetadataViewInterface nestedSettings = (ComplexMetadataViewInterface) mvwviListNestedSettings.stream()
                .filter(mvwvi -> mvwvi.getMetadata().get().getId().equals("testNestedSettings")).findAny().get()
                .getMetadata().get();
        List<MetadataViewWithValuesInterface> nestedMvwviList = nestedSettings
                .getSortedVisibleMetadata(Collections.emptyList(), Collections.emptyList());
        assertEquals(1, nestedMvwviList.size());
        assertEquals("testAlwaysShowing", nestedMvwviList.get(0).getMetadata().get().getId());

        // 2a. Also with nested metadata, all fields except those that are
        // excluded should be allowed to be added.
        Collection<MetadataViewInterface> nestedMviColl = nestedSettings.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());
        assertThat(ids(nestedMviColl), containsInAnyOrder("testAlwaysShowing", "testEditable", "testMultiline"));

        // 3. With metadata, all fields should be visible except those that are
        // excluded. There should be an entry without a key in the list
        // containing the values of the excluded keys.
        Collection<Metadata> metadata = new ArrayList<>();
        Metadata metadataOne = new MetadataEntry();
        metadataOne.setKey("testAlwaysShowing");
        metadata.add(metadataOne);
        Metadata metadataTwo = new MetadataEntry();
        metadataTwo.setKey("testEditable");
        metadata.add(metadataTwo);
        MetadataEntry metadataThree = new MetadataEntry();
        metadataThree.setKey("testExcluded");
        metadataThree.setValue("exclude1");
        metadata.add(metadataThree);
        Metadata metadataFour = new MetadataEntry();
        metadataFour.setKey("testMultiline");
        metadata.add(metadataFour);
        Metadata metadataFive = new MetadataEntry();
        metadataFive.setKey("testAlwaysShowingEditable");
        metadata.add(metadataFive);
        MetadataEntry metadataSix = new MetadataEntry();
        metadataSix.setKey("testAlwaysShowingExcluded");
        metadataSix.setValue("exclude2");
        metadata.add(metadataSix);
        Metadata metadataSeven = new MetadataEntry();
        metadataSeven.setKey("testAlwaysShowingMultiline");
        metadata.add(metadataSeven);
        MetadataEntry metadataEight = new MetadataEntry();
        metadataEight.setKey("testEditableExcluded");
        metadataEight.setValue("exclude3");
        metadata.add(metadataEight);
        Metadata metadataNine = new MetadataEntry();
        metadataNine.setKey("testEditableMultiline");
        metadata.add(metadataNine);
        MetadataEntry metadataTen = new MetadataEntry();
        metadataTen.setKey("testExcludedMultiline");
        metadataTen.setValue("exclude4");
        metadata.add(metadataTen);
        Metadata metadataEleven = new MetadataEntry();
        metadataEleven.setKey("testNestedSettings");
        metadata.add(metadataEleven);

        List<MetadataViewWithValuesInterface> mvwviListWithMetadata = sevi.getSortedVisibleMetadata(metadata,
            Collections.emptyList());
        assertThat(ids(mvwviListWithMetadata), containsInAnyOrder("testAlwaysShowing", "testEditable", "testMultiline",
            "testAlwaysShowingEditable", "testAlwaysShowingMultiline", "testEditableMultiline", "testNestedSettings"));
        assertThat(
            mvwviListWithMetadata.stream().filter(mvwvi -> !mvwvi.getMetadata().isPresent())
                    .flatMap(
                        mvwvi -> mvwvi.getValues().stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue))
                    .collect(Collectors.toList()),
            containsInAnyOrder("exclude1", "exclude2", "exclude3", "exclude4"));

        // 3a. Also with nested metadata, that should work that way.
        Collection<Metadata> nestedMetadata = new ArrayList<>();
        nestedMetadata.add(metadataOne);
        nestedMetadata.add(metadataTwo);
        nestedMetadata.add(metadataThree);
        nestedMetadata.add(metadataFour);

        List<MetadataViewWithValuesInterface> nestedMvwviListWithMetadata = nestedSettings
                .getSortedVisibleMetadata(nestedMetadata, Collections.emptyList());
        assertThat(ids(nestedMvwviListWithMetadata),
            containsInAnyOrder("testAlwaysShowing", "testEditable", "testMultiline"));
        assertTrue(nestedMvwviListWithMetadata.stream().filter(mvwvi -> !mvwvi.getMetadata().isPresent())
                .flatMap(
                    mvwvi -> mvwvi.getValues().stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue))
                .collect(Collectors.toList()).contains("exclude1"));

        // 4. The property ‘multiline’ should manipulate the input type
        SimpleMetadataViewInterface testAlwaysShowing = getSmvi(mvwviListWithMetadata, "testAlwaysShowing");
        assertEquals(InputType.ONE_LINE_TEXT, testAlwaysShowing.getInputType());
        SimpleMetadataViewInterface testEditable = getSmvi(mvwviListWithMetadata, "testEditable");
        assertEquals(InputType.ONE_LINE_TEXT, testEditable.getInputType());
        SimpleMetadataViewInterface testMultiline = getSmvi(mvwviListWithMetadata, "testMultiline");
        assertEquals(InputType.MULTI_LINE_TEXT, testMultiline.getInputType());
        SimpleMetadataViewInterface testAlwaysShowingEditable = getSmvi(mvwviListWithMetadata,
            "testAlwaysShowingEditable");
        assertEquals(InputType.ONE_LINE_TEXT, testAlwaysShowingEditable.getInputType());
        SimpleMetadataViewInterface testAlwaysShowingMultiline = getSmvi(mvwviListWithMetadata,
            "testAlwaysShowingMultiline");
        assertEquals(InputType.MULTI_LINE_TEXT, testAlwaysShowingMultiline.getInputType());
        SimpleMetadataViewInterface testEditableMultiline = getSmvi(mvwviListWithMetadata, "testEditableMultiline");
        assertEquals(InputType.MULTI_LINE_TEXT, testEditableMultiline.getInputType());

        SimpleMetadataViewInterface nestedTestAlwaysShowing = getSmvi(nestedMvwviListWithMetadata, "testAlwaysShowing");
        assertEquals(InputType.ONE_LINE_TEXT, nestedTestAlwaysShowing.getInputType());
        SimpleMetadataViewInterface nestedTestEditable = getSmvi(nestedMvwviListWithMetadata, "testEditable");
        assertEquals(InputType.ONE_LINE_TEXT, nestedTestEditable.getInputType());
        SimpleMetadataViewInterface nestedTestMultiline = getSmvi(nestedMvwviListWithMetadata, "testMultiline");
        assertEquals(InputType.MULTI_LINE_TEXT, nestedTestMultiline.getInputType());

        // 5. The property ‘editable’ should be reflected in the value of
        // isEditable()
        assertTrue(testAlwaysShowing.isEditable());
        assertFalse(testEditable.isEditable());
        assertTrue(testMultiline.isEditable());
        assertFalse(testAlwaysShowingEditable.isEditable());
        assertTrue(testAlwaysShowingMultiline.isEditable());
        assertFalse(testEditableMultiline.isEditable());

        assertTrue(nestedTestAlwaysShowing.isEditable());
        assertFalse(nestedTestEditable.isEditable());
        assertTrue(nestedTestMultiline.isEditable());
    }

    /**
     * This test verifies that the labels for divisions are translated
     * correctly. For this purpose, various cases with different language
     * requirements (existing, nonexistent and overspecified) are performed.
     */
    @Test
    public void testDivisionsAreCorrectlyTranslated() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testDivisionsAreCorrectlyTranslated.xml"));
        assertTrue(underTest.getAcquisitionStages().isEmpty());

        // Here we test the translation, whether the expected language always
        // comes.
        Map<String, String> divisionsNoLanguage = underTest.getStructuralElements(Collections.emptyList());
        assertEquals(1, divisionsNoLanguage.entrySet().size());
        assertTrue(divisionsNoLanguage.containsKey(BOOK));
        assertTrue(divisionsNoLanguage.containsValue(StringUtils.capitalize(BOOK)));

        Map<String, String> divisionsDe = underTest
                .getStructuralElements(LanguageRange.parse("de;q=1.0,cn;q=0.75,fr;q=0.5,ru;q=0.25"));
        assertEquals(1, divisionsDe.entrySet().size());
        assertTrue(divisionsDe.containsKey(BOOK));
        assertTrue(divisionsDe.containsValue("Buch"));

        Map<String, String> divisionsDeDe = underTest
                .getStructuralElements(LanguageRange.parse("de-DE;q=1.0,cn;q=0.75,fr;q=0.5,ru;q=0.25"));
        assertEquals(1, divisionsDeDe.entrySet().size());
        assertTrue(divisionsDeDe.containsKey(BOOK));
        assertTrue(divisionsDeDe.containsValue("Buch"));

        Map<String, String> divisionsEn = underTest
                .getStructuralElements(LanguageRange.parse("en;q=1.0,fr;q=0.75,de;q=0.5,cn;q=0.25"));
        assertEquals(1, divisionsEn.entrySet().size());
        assertTrue(divisionsEn.containsKey(BOOK));
        assertTrue(divisionsEn.containsValue(StringUtils.capitalize(BOOK)));

        Map<String, String> divisionsEnUs = underTest
                .getStructuralElements(LanguageRange.parse("en-US;q=1.0,de;q=0.667,fr;q=0.333"));
        assertEquals(1, divisionsEnUs.entrySet().size());
        assertTrue(divisionsEnUs.containsKey(BOOK));
        assertTrue(divisionsEnUs.containsValue(StringUtils.capitalize(BOOK)));

        Map<String, String> divisionsCnRu = underTest.getStructuralElements(LanguageRange.parse("cn;q=1.0,ru;q=0.5"));
        assertEquals(1, divisionsCnRu.entrySet().size());
        assertTrue(divisionsCnRu.containsKey(BOOK));
        assertTrue(divisionsCnRu.containsValue(StringUtils.capitalize(BOOK)));

        // Now a first view on a book
        StructuralElementViewInterface view = underTest.getStructuralElementView(BOOK, "", ENGL);
        assertEquals(1, view.getAllowedSubstructuralElements().entrySet().size());
        assertEquals(5 + 3, view.getAddableMetadata(Collections.emptyList(), Collections.emptyList()).size());
        assertTrue(view.isComplex());
        assertFalse(view.isUndefined());

        // Now a nonsense view
        StructuralElementViewInterface nonsenseView = underTest.getStructuralElementView("bosh", "", ENGL);
        assertEquals(1, nonsenseView.getAllowedSubstructuralElements().entrySet().size());
        assertEquals(5 + 3, nonsenseView.getAddableMetadata(Collections.emptyList(), Collections.emptyList()).size());
        assertTrue(nonsenseView.isUndefined());
    }

    /**
     * This test verifies that the selection of child divisions in divisions by
     * date works correctly.
     */
    @Test
    public void testDivisionsSubdividedByDateHaveOnlyTheCorrectChildren() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testDivisionsSubdividedByDateHaveOnlyTheCorrectChildren.xml"));

        // Also the divisions structuring by date are to come out in the total
        // list:
        assertThat(underTest.getStructuralElements(ENGL).keySet(),
            containsInAnyOrder("newspaper", "newspaperYear", "newspaperMonth", "newspaperLimitedTest",
                "newspaperYearLimitedTest", "newspaperMonthLimitedTest"));
        assertThat(underTest.getStructuralElements(ENGL).values(),
            containsInAnyOrder("Newspaper ‹complete edition›", "Year’s issues ‹newspaper›",
                "Month’s issues ‹newspaper›",
                "Newspaper ‹complete edition›—limited test", "Year’s issues ‹newspaper›—limited test",
                "Month’s issues ‹newspaper›—limited test"));

        // It should always come the right children:
        StructuralElementViewInterface newspaperSevi = underTest.getStructuralElementView("newspaper", "", ENGL);
        Map<String, String> newspaperAse = newspaperSevi.getAllowedSubstructuralElements();
        assertEquals(1, newspaperAse.entrySet().size());
        assertThat(newspaperAse, hasEntry("newspaperYear", "Year’s issues ‹newspaper›"));

        StructuralElementViewInterface yearSevi = underTest.getStructuralElementView("newspaperYear", "", ENGL);
        Map<String, String> yearAse = yearSevi.getAllowedSubstructuralElements();
        assertEquals(1, yearAse.entrySet().size());
        assertThat(yearAse, hasEntry("newspaperMonth", "Month’s issues ‹newspaper›"));

        StructuralElementViewInterface monthSevi = underTest.getStructuralElementView("newspaperMonth", "", ENGL);
        Map<String, String> monthAse = monthSevi.getAllowedSubstructuralElements();
        assertEquals(1, monthAse.entrySet().size());
        assertThat(monthAse, hasEntry("newspaperDay", "Day’s issues ‹newspaper›"));

        StructuralElementViewInterface daySevi = underTest.getStructuralElementView("newspaperDay", "", ENGL);
        Map<String, String> dayAse = daySevi.getAllowedSubstructuralElements();
        assertEquals(3, dayAse.entrySet().size());
        assertThat(dayAse, hasEntry("newspaper", "Newspaper ‹complete edition›"));
        assertThat(dayAse, hasEntry("newspaperLimitedTest", "Newspaper ‹complete edition›—limited test"));
        assertThat(dayAse, hasEntry("newspaperIssue", "Issue ‹newspaper›"));

        StructuralElementViewInterface newspaperLimitedTestSevi = underTest
                .getStructuralElementView("newspaperDayLimitedTest", "", ENGL);
        Map<String, String> newspaperLimitedTestAse = newspaperLimitedTestSevi.getAllowedSubstructuralElements();
        assertEquals(1, newspaperLimitedTestAse.entrySet().size());
        assertThat(newspaperLimitedTestAse, hasEntry("newspaperIssue", "Issue ‹newspaper›"));
    }

    /**
     * This test checks whether the date scheme and the field are correctly read
     * out for divisions that are subdivided by date.
     */
    @Test
    public void testDivisionsSubdividedByDateReportDateSchemeAndField() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testDivisionsSubdividedByDateReportDateSchemeAndField.xml"));

        StructuralElementViewInterface playtimeSevi = underTest.getStructuralElementView("playtime", "", ENGL);
        assertThat(playtimeSevi.getDatesSimpleMetadata(), is(Optional.empty()));

        StructuralElementViewInterface yearSevi = underTest.getStructuralElementView("playtimeYear", "", ENGL);
        assertThat(yearSevi.getDatesSimpleMetadata(), is(not(Optional.empty())));
        DatesSimpleMetadataViewInterface yearDsmvi = yearSevi.getDatesSimpleMetadata().get();
        assertEquals("ORDERLABEL", yearDsmvi.getId());
        assertEquals("yyyy/yyyy", yearDsmvi.getScheme());
        assertEquals(MonthDay.of(Month.AUGUST, 1), yearDsmvi.getYearBegin());

        StructuralElementViewInterface monthSevi = underTest.getStructuralElementView("playtimeMonth", "", ENGL);
        assertThat(monthSevi.getDatesSimpleMetadata(), is(not(Optional.empty())));
        DatesSimpleMetadataViewInterface monthDsmvi = monthSevi.getDatesSimpleMetadata().get();
        assertEquals("ORDERLABEL", monthDsmvi.getId());
        assertEquals("yyyy-MM", monthDsmvi.getScheme());

        StructuralElementViewInterface daySevi = underTest.getStructuralElementView("playtimeDay", "", ENGL);
        assertThat(daySevi.getDatesSimpleMetadata(), is(not(Optional.empty())));
        DatesSimpleMetadataViewInterface dayDsmvi = daySevi.getDatesSimpleMetadata().get();
        assertEquals("ORDERLABEL", dayDsmvi.getId());
        assertEquals("yyyy-MM-dd", dayDsmvi.getScheme());
    }

    /**
     * The test verifies that fields with minOccurs greater than zero appear at
     * least as often as they need to be completed. An exception exists for
     * multiple selection, which of course nevertheless only once may be
     * displayed (here only several must be selected).
     */
    @Test
    public void testFieldsWithMinOccursGreaterZeroAreAlwaysShown() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testFieldsWithMinOccursGreaterZeroAreAlwaysShown.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.emptyList());

        assertThat(ids(mvwviList), contains("test1", "test2", "test2", "test2options"));
    }

    @Test
    public void testGettingOfSpecialFields() throws Exception {
        RulesetManagement rulesetManagement= new RulesetManagement();
        rulesetManagement.load(new File("src/test/resources/testAnExtensiveRulesetCanBeLoaded.xml"));

        assertThat("TitleDocMain was not found!",
            rulesetManagement.getFunctionalKeys(FunctionalMetadata.TITLE), contains("TitleDocMain"));
        assertThat("Person@LastName was not found!",
            rulesetManagement.getFunctionalKeys(FunctionalMetadata.AUTHOR_LAST_NAME),
            contains("Person@LastName"));
        assertThat("Periodical was not found!",
                rulesetManagement.getFunctionalDivisions(FunctionalDivision.CREATE_CHILDREN_FROM_PARENT),
                contains("Periodical"));
        assertThat("structureTreeTitle was not found!", 
            rulesetManagement.getFunctionalKeys(FunctionalMetadata.STRUCTURE_TREE_TITLE),
            contains("LABEL"));

        // not existing uses
        assertThat("Something was found!",
            rulesetManagement.getFunctionalKeys(FunctionalMetadata.DATA_SOURCE), is(empty()));

        // multiple uses of one key
        assertThat("shelfmarksource was not found!",
            rulesetManagement.getFunctionalKeys(FunctionalMetadata.RECORD_IDENTIFIER),
            contains("shelfmarksource"));
        assertThat("shelfmarksource was not found!",
            rulesetManagement.getFunctionalKeys(FunctionalMetadata.HIGHERLEVEL_IDENTIFIER),
            contains("shelfmarksource"));
    }

    /**
     * The test verifies that all keys pass the domain correctly.
     */
    @Test
    public void testKeysReturnTheSpecifiedDomain() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testKeysReturnTheSpecifiedDomain.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        Collection<Metadata> metadata = new ArrayList<>();
        Metadata metadataOne = new MetadataEntry();
        metadataOne.setKey("unspecifiedKey");
        metadata.add(metadataOne);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(metadata, Arrays.asList(
            "description", "digitalProvenance", "noDomainSpecified", "rights", "source", "technical", "metsDiv"));

        SimpleMetadataViewInterface description = getSmvi(mvwviList, "description");
        assertEquals(Domain.DESCRIPTION, description.getDomain().get());

        SimpleMetadataViewInterface digitalProvenance = getSmvi(mvwviList, "digitalProvenance");
        assertEquals(Domain.DIGITAL_PROVENANCE, digitalProvenance.getDomain().get());

        SimpleMetadataViewInterface metsDiv = getSmvi(mvwviList, "metsDiv");
        assertEquals(Domain.METS_DIV, metsDiv.getDomain().get());

        SimpleMetadataViewInterface noDomainSpecified = getSmvi(mvwviList, "noDomainSpecified");
        assertThat(noDomainSpecified.getDomain(), is(Optional.empty()));

        SimpleMetadataViewInterface rights = getSmvi(mvwviList, "rights");
        assertEquals(Domain.RIGHTS, rights.getDomain().get());

        SimpleMetadataViewInterface source = getSmvi(mvwviList, "source");
        assertEquals(Domain.SOURCE, source.getDomain().get());

        SimpleMetadataViewInterface technical = getSmvi(mvwviList, "technical");
        assertEquals(Domain.TECHNICAL, technical.getDomain().get());

        SimpleMetadataViewInterface unspecifiedKey = getSmvi(mvwviList, "unspecifiedKey");
        assertThat(unspecifiedKey.getDomain(), is(Optional.empty()));
    }

    /**
     * This test verifies that different rules (key-based and division-based)
     * are merged correctly.
     */
    @Test
    public void testRulesAreCorrectlyMerged() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testRulesAreCorrectlyMerged.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.singletonList("personContributor"));
        ComplexMetadataViewInterface personContributor = getCmvi(mvwviList, "personContributor");
        List<MetadataViewWithValuesInterface> visible = personContributor
                .getSortedVisibleMetadata(Collections.emptyList(), Collections.emptyList());
        assertThat(ids(visible), contains("role", "gndRecord", "givenName", "surname"));
        assertThat(getSmvi(visible, "role").getSelectItems(Collections.emptyList()).keySet(),
            contains("author", "editor"));
    }

    /**
     * This test verifies that unspecified unrestricted rules remove keys that
     * have a maxOccurs of zero.
     */
    @Test
    public void testRulesRemoveKeysWithZeroMaxOccurs() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testRulesRemoveKeysWithZeroMaxOccurs.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        Collection<MetadataViewInterface> mviColl = sevi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());
        assertTrue(ids(mviColl).contains("keep"));
    }

    /**
     * This test verifies that the input type is set depending on the codomain.
     */
    @Test
    public void testTheDisplayModeIsSetUsingTheCodomain() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testTheDisplayModeIsSetUsingTheCodomain.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Arrays.asList("defaultString", "anyURI", "boolean", "date", "integer", "namespace", "namespaceString"));

        assertEquals(InputType.ONE_LINE_TEXT, getSmvi(mvwviList, "defaultString").getInputType());
        assertEquals(InputType.ONE_LINE_TEXT, getSmvi(mvwviList, "anyURI").getInputType());
        assertEquals(InputType.BOOLEAN, getSmvi(mvwviList, "boolean").getInputType());
        assertEquals(InputType.DATE, getSmvi(mvwviList, "date").getInputType());
        assertEquals(InputType.INTEGER, getSmvi(mvwviList, "integer").getInputType());
        assertEquals(InputType.ONE_LINE_TEXT, getSmvi(mvwviList, "namespace").getInputType());
        assertEquals(InputType.ONE_LINE_TEXT, getSmvi(mvwviList, "namespaceString").getInputType());
    }

    /**
     * This test verifies that unspecified forbidden rules restrict the allowed
     * divisions.
     */
    @Test
    public void testUnspecifiedForbiddenRulesRestrictDivisions() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testUnspecifiedForbiddenRulesRestrictDivisions.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        assertTrue(sevi.getAllowedSubstructuralElements().keySet().contains("chapter"));
    }

    /**
     * This test verifies that unspecified forbidden rules restrict the allowed
     * keys.
     */
    @Test
    public void testUnspecifiedForbiddenRulesRestrictKeys() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testUnspecifiedForbiddenRulesRestrictKeys.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        Collection<MetadataViewInterface> mviColl = sevi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());
        assertTrue(ids(mviColl).contains("allowed"));
    }

    /**
     * This test verifies that unspecified forbidden rules restrict the allowed
     * options.
     */
    @Test
    public void testUnspecifiedForbiddenRulesRestrictOptions() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testUnspecifiedForbiddenRulesRestrictOptions.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);

        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.singletonList(TEST));
        SimpleMetadataViewInterface test = getSmvi(mvwviList, TEST);
        assertThat(test.getSelectItems(Collections.emptyList()).keySet(), contains(OPT, "opt3", "opt5", "opt7"));

    }

    /**
     * This test verifies that unspecified forbidden rules restrict the allowed
     * options during validation.
     */
    @Test
    public void testUnspecifiedForbiddenRulesRestrictOptionsAlsoInTheValidation() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(
            new File("src/test/resources/testUnspecifiedForbiddenRulesRestrictOptionsAlsoInTheValidation.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);

        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.singletonList(TEST));
        SimpleMetadataViewInterface test = getSmvi(mvwviList, TEST);
        assertTrue(test.isValid(OPT, Collections.emptyList()));
        assertFalse(test.isValid("opt2", Collections.emptyList()));
        assertTrue(test.isValid("opt3", Collections.emptyList()));
        assertFalse(test.isValid("opt4", Collections.emptyList()));
        assertTrue(test.isValid("opt5", Collections.emptyList()));
        assertFalse(test.isValid("opt6", Collections.emptyList()));
        assertTrue(test.isValid("opt7", Collections.emptyList()));
        assertFalse(test.isValid("mischief", Collections.emptyList()));
    }

    /**
     * This test verifies that unspecified unrestricted rules are resorting but
     * not restricting the divisions.
     */
    @Test
    public void testUnspecifiedUnrestrictedRulesSortDivisionsWithoutRestrictingThem() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(
            new File("src/test/resources/testUnspecifiedUnrestrictedRulesSortDivisionsWithoutRestrictingThem.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView("box", "", ENGL);
        assertThat(sevi.getAllowedSubstructuralElements().keySet(), contains(
            // in order as in the ruleset
            "phonographicRecord", "cassette", "cd", "gramophoneRecord", "pianoRoll",

            // the rest in alphabetical order by its label
            "article", BOOK, "box", "chalcography", "colorChart", "dvd", "frontCover", "film", "multiVolume",
            "serial", "side", "videoTape", "wireRecording"));
    }

    /**
     * This test verifies that unspecified unrestricted rules are resorting but
     * not restricting the keys.
     */
    @Test
    public void testUnspecifiedUnrestrictedRulesSortKeysWithoutRestrictingThem() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(
            new File("src/test/resources/testUnspecifiedUnrestrictedRulesSortKeysWithoutRestrictingThem.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView("article", "", ENGL);
        Collection<MetadataViewInterface> mviColl = sevi.getAddableMetadata(Collections.emptyList(),
            Collections.emptyList());

        assertThat(ids(mviColl), contains("author", "year", "title", "journal", "journalAbbr", "issue", "abstract",
            "CONTENTIDS", "LABEL", "ORDERLABEL"));
    }

    /**
     * The test verifies that unspecified unrestricted rules are resorting but
     * not restricting the options.
     */
    @Test
    public void testUnspecifiedUnrestrictedRulesSortOptionsWithoutRestrictingThem() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(
            new File("src/test/resources/testUnspecifiedUnrestrictedRulesSortOptionsWithoutRestrictingThem.xml"));
        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Collections.singletonList(TEST));
        SimpleMetadataViewInterface test = getSmvi(mvwviList, TEST);
        assertThat(test.getSelectItems(Collections.emptyList()).keySet(),
            contains("opt4", "opt7", OPT, "opt2", "opt3", "opt5", "opt6"));
    }

    /**
     * The test verifies that the codomain is validated correctly.
     */
    @Test
    public void testValidationByCodomain() throws IOException {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testValidationByCodomain.xml"));

        StructuralElementViewInterface sevi = underTest.getStructuralElementView(BOOK, "", ENGL);
        List<MetadataViewWithValuesInterface> mvwviList = sevi.getSortedVisibleMetadata(Collections.emptyList(),
            Arrays.asList("default", "defaultOpt", "anyUri", "boolean", "date", "integer", "namespaceDefault",
                "namespaceString", "namespaceDefaultOpt", "namespaceStringOpt", "namespaceDefaultExternal",
                "namespaceStringExternal"));

        SimpleMetadataViewInterface defaultMv = getSmvi(mvwviList, "default");
        assertTrue(defaultMv.isValid(HELLO_WORLD, Collections.emptyList()));

        SimpleMetadataViewInterface defaultOpt = getSmvi(mvwviList, "defaultOpt");
        assertTrue(defaultOpt.isValid("val1", Collections.emptyList()));
        assertFalse(defaultOpt.isValid("val9", Collections.emptyList()));

        SimpleMetadataViewInterface anyUri = getSmvi(mvwviList, "anyUri");
        assertTrue(anyUri.isValid("https://www.kitodo.org/software/kitodoproduction/", Collections.emptyList()));
        assertTrue(anyUri.isValid("mailto:contact@kitodo.org", Collections.emptyList()));
        assertTrue(anyUri.isValid("urn:nbn:de-9999-12345678X", Collections.emptyList()));
        assertFalse(anyUri.isValid(HELLO_WORLD, Collections.emptyList()));

        SimpleMetadataViewInterface booleanMv = getSmvi(mvwviList, "boolean");
        assertTrue(booleanMv.isValid("on", Collections.emptyList()));
        assertFalse(booleanMv.isValid(HELLO_WORLD, Collections.emptyList()));

        SimpleMetadataViewInterface date = getSmvi(mvwviList, "date");
        assertTrue(date.isValid("1492-10-12", Collections.emptyList()));
        assertFalse(date.isValid("1900-02-29", Collections.emptyList()));

        SimpleMetadataViewInterface integer = getSmvi(mvwviList, "integer");
        assertTrue(integer.isValid("1234567", Collections.emptyList()));
        assertFalse(integer.isValid("1 + 1i", Collections.emptyList()));

        SimpleMetadataViewInterface namespaceDefault = getSmvi(mvwviList, "namespaceDefault");
        assertTrue(namespaceDefault.isValid("http://test.example/testValidation/alice", Collections.emptyList()));
        assertFalse(namespaceDefault.isValid("http://test.example/testValidation#bob", Collections.emptyList()));
        assertFalse(namespaceDefault.isValid("https://www.wdrmaus.de/", Collections.emptyList()));

        SimpleMetadataViewInterface namespaceString = getSmvi(mvwviList, "namespaceString");
        assertTrue(namespaceString.isValid("http://test.example/testValidation/alice", Collections.emptyList()));
        assertTrue(namespaceString.isValid("{http://test.example/testValidation/}bob", Collections.emptyList()));
        assertFalse(namespaceString.isValid("https://www.wdrmaus.de/", Collections.emptyList()));

        SimpleMetadataViewInterface namespaceOpt = getSmvi(mvwviList, "namespaceDefaultOpt");
        assertTrue(namespaceOpt.isValid("http://test.example/testValidation/value1", Collections.emptyList()));
        assertFalse(namespaceOpt.isValid("http://test.example/testValidation/value4, Collections.emptyList()",
            Collections.emptyList()));

        SimpleMetadataViewInterface namespaceStrOpt = getSmvi(mvwviList, "namespaceStringOpt");
        assertTrue(namespaceStrOpt.isValid("http://test.example/testValidation/value1", Collections.emptyList()));
        assertFalse(namespaceStrOpt.isValid("http://test.example/testValidation/value4", Collections.emptyList()));

        SimpleMetadataViewInterface namespaceExt = getSmvi(mvwviList, "namespaceDefaultExternal");
        assertTrue(namespaceExt.isValid("http://test.example/testValidationByCodomainNamespace#val1",
            Collections.emptyList()));
        assertFalse(namespaceExt.isValid("http://test.example/testValidationByCodomainNamespace#val4",
            Collections.emptyList()));

        SimpleMetadataViewInterface namespaceStrExt = getSmvi(mvwviList, "namespaceStringExternal");
        assertTrue(namespaceStrExt.isValid("http://test.example/testValidationByCodomainNamespace#val1",
            Collections.emptyList()));
        assertFalse(namespaceStrExt.isValid("http://test.example/testValidationByCodomainNamespace#val4",
            Collections.emptyList()));
    }

    @Test
    public void testReimportOfMetadataModesCreate() throws Exception {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testReimportOfMetadataModes.xml"));

        Collection<Metadata> metadata = new HashSet<>();
        metadata.add(newMetadataEntry("metadataToReplaceByDefault", "value to replace"));
        metadata.add(newMetadataEntry("metadataToReplaceExplicitly", "value to replace"));
        metadata.add(newMetadataEntry("metadataToAdd", "value 1"));
        metadata.add(newMetadataEntry("metadataToAddWithLimit", "value 1"));
        metadata.add(newMetadataEntry("metadataToAddDuringCreationAndKeepLater", "value 1"));
        metadata.add(newMetadataEntry("metadataToKeep", "value not to replace"));
        metadata.add(newMetadataEntry("metadataToKeepExceptInEditing", "value not to replace"));

        Collection<Metadata> imported = new HashSet<>();
        imported.add(newMetadataEntry("metadataToReplaceByDefault", "replaced value")); // 0
        imported.add(newMetadataEntry("metadataToReplaceExplicitly", "replaced value")); // 0
        imported.add(newMetadataEntry("metadataToAdd", "value 1")); // 0
        imported.add(newMetadataEntry("metadataToAdd", "value 2")); // 1
        imported.add(newMetadataEntry("metadataToAdd", "value 3")); // 1
        imported.add(newMetadataEntry("metadataToAddWithLimit", "value 1")); // 0
        imported.add(newMetadataEntry("metadataToAddWithLimit", "value 2")); // 0.5
        imported.add(newMetadataEntry("metadataToAddWithLimit", "value 3")); // 0.5
        imported.add(newMetadataEntry("metadataToAddDuringCreationAndKeepLater", "value 2")); // 1
        imported.add(newMetadataEntry("metadataToKeep", "value must not appear in result")); // 0
        imported.add(newMetadataEntry("metadataToKeepExceptInEditing", "value not to replace")); // 0
        imported.add(newMetadataEntry("metadataThatIsNew", "new value")); // 1

        int numAdded = underTest.updateMetadata("division", metadata, CREATE, imported);
        assertEquals(5, numAdded);

        List<Metadata> defaultReplace = metadata.stream()
                .filter(element -> element.getKey().equals("metadataToReplaceByDefault")).collect(Collectors.toList());
        assertEquals(1, defaultReplace.size());
        assertEquals("replaced value", ((MetadataEntry) defaultReplace.get(0)).getValue());

        List<Metadata> explicitReplace = metadata.stream()
                .filter(element -> element.getKey().equals("metadataToReplaceExplicitly")).collect(Collectors.toList());
        assertEquals(1, explicitReplace.size());
        assertEquals("replaced value", ((MetadataEntry) explicitReplace.get(0)).getValue());

        List<Metadata> add = metadata.stream().filter(element -> element.getKey().equals("metadataToAdd"))
                .collect(Collectors.toList());
        assertEquals(3, add.size());
        assertThat(
            add.stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue).collect(Collectors.toList()),
            containsInAnyOrder("value 1", "value 2", "value 3"));

        List<Metadata> limitedAdd = metadata.stream().filter(element -> element.getKey().equals("metadataToAddWithLimit"))
                .collect(Collectors.toList());
        assertEquals(2, limitedAdd.size());
        assertThat(
            limitedAdd.stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue).collect(Collectors.toList()),
            anyOf(containsInAnyOrder("value 1", "value 2"), containsInAnyOrder("value 1", "value 3")));

        List<Metadata> addCreate = metadata.stream()
                .filter(element -> element.getKey().equals("metadataToAddDuringCreationAndKeepLater"))
                .collect(Collectors.toList());
        assertEquals(2, addCreate.size());
        assertThat(
            addCreate.stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue).collect(Collectors.toList()),
            containsInAnyOrder("value 1", "value 2"));

        List<Metadata> keep = metadata.stream().filter(element -> element.getKey().equals("metadataToKeep"))
                .collect(Collectors.toList());
        assertEquals(1, keep.size());
        assertEquals("value not to replace", ((MetadataEntry) keep.get(0)).getValue());

        List<Metadata> keepNoEdit = metadata.stream()
                .filter(element -> element.getKey().equals("metadataToKeepExceptInEditing"))
                .collect(Collectors.toList());
        assertEquals(1, keepNoEdit.size());
        assertEquals("value not to replace", ((MetadataEntry) keepNoEdit.get(0)).getValue());
    }

    @Test
    public void testReimportOfMetadataModesEdit() throws Exception {
        RulesetManagement underTest = new RulesetManagement();
        underTest.load(new File("src/test/resources/testReimportOfMetadataModes.xml"));

        Collection<Metadata> metadata = new HashSet<>();
        metadata.add(newMetadataEntry("metadataToAddDuringCreationAndKeepLater", "value 1"));
        metadata.add(newMetadataEntry("metadataToAddDuringCreationAndKeepLater", "value 2"));
        metadata.add(newMetadataEntry("metadataToKeepExceptInEditing", "value 1"));
        metadata.add(newMetadataEntry("metadataToKeepExceptInEditing", "value 2"));

        Collection<Metadata> imported = new HashSet<>();
        imported.add(newMetadataEntry("metadataToAddDuringCreationAndKeepLater", "value not to replace")); // 0
        imported.add(newMetadataEntry("metadataToKeepExceptInEditing", "replaced value")); // -1

        int numAdded = underTest.updateMetadata("division", metadata, EDIT, imported);
        assertEquals(-1, numAdded);

        List<Metadata> keepLater = metadata.stream()
                .filter(element -> element.getKey().equals("metadataToAddDuringCreationAndKeepLater"))
                .collect(Collectors.toList());
        assertEquals(2, keepLater.size());
        assertThat(
            keepLater.stream().map(MetadataEntry.class::cast).map(MetadataEntry::getValue).collect(Collectors.toList()),
            containsInAnyOrder("value 1", "value 2"));

        List<Metadata> replaceInEdit = metadata.stream()
                .filter(element -> element.getKey().equals("metadataToKeepExceptInEditing"))
                .collect(Collectors.toList());
        assertEquals(1, replaceInEdit.size());
        assertEquals("replaced value", ((MetadataEntry) replaceInEdit.get(0)).getValue());
    }

    /**
     * The method provides a simple access to a metadata key in a list of
     * MetadataViewWithValuesInterface.
     *
     * @param mvwviList
     *            list of MetadataViewWithValuesInterface to extract from
     * @param keyId
     *            ID of key to extract
     * @return metadata key
     */
    private SimpleMetadataViewInterface getSmvi(List<MetadataViewWithValuesInterface> mvwviList, String keyId) {
        return (SimpleMetadataViewInterface) mvwviList.stream().filter(mvwvi -> mvwvi.getMetadata().isPresent())
                .filter(mvwvi -> keyId.equals(mvwvi.getMetadata().get().getId())).findAny().get().getMetadata().get();
    }

    /**
     * The method provides a simple access to a metadata key in a list of
     * MetadataViewWithValuesInterface.
     *
     * @param metadataViewWithValuesInterfaceList
     *            list of MetadataViewWithValuesInterface to extract from
     * @param keyId
     *            ID of key to extract
     * @return metadata key
     */
    private ComplexMetadataViewInterface getCmvi(
            List<MetadataViewWithValuesInterface> metadataViewWithValuesInterfaceList, String keyId) {
        return (ComplexMetadataViewInterface) metadataViewWithValuesInterfaceList.stream()
                .filter(mvwvi -> mvwvi.getMetadata().isPresent())
                .filter(metadataViewWithValuesInterface -> keyId
                        .equals(metadataViewWithValuesInterface.getMetadata().get().getId()))
                .findAny().get().getMetadata().get();
    }

    /**
     * Returns the IDs of the metadata keys in a collection of metadata view
     * interfaces.
     *
     * @param mviColl
     *            collection of metadata view interfaces to return the IDs of
     *            the metadata keys from
     * @return the IDs of the metadata keys
     */
    private List<String> ids(Collection<MetadataViewInterface> mviColl) {
        return mviColl.stream().map(MetadataViewInterface::getId).collect(Collectors.toList());
    }

    /**
     * Returns the IDs of the metadata keys in a metadata view with values
     * interface list.
     *
     * @param metadataViewWithValuesInterfaceList
     *            metadata view with values interface list to return the IDs of
     *            the metadata keys from
     * @return the IDs of the metadata keys
     */
    private List<String> ids(List<MetadataViewWithValuesInterface> metadataViewWithValuesInterfaceList) {
        return metadataViewWithValuesInterfaceList.stream()
                .filter(metadataViewWithValuesInterface -> metadataViewWithValuesInterface.getMetadata().isPresent())
                .map(metadataViewWithValuesInterface -> metadataViewWithValuesInterface.getMetadata().get().getId())
                .collect(Collectors.toList());
    }

    private MetadataEntry newMetadataEntry(String key, String value) {
        MetadataEntry newMetadataEntry = new MetadataEntry();
        newMetadataEntry.setKey(key);
        newMetadataEntry.setValue(value);
        newMetadataEntry.setDomain(MdSec.DMD_SEC);
        return newMetadataEntry;
    }
}
