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

package org.kitodo.service.image;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kitodo.config.ConfigCore;
import org.kitodo.data.database.beans.SubfolderType;
import org.kitodo.data.database.beans.Process;
import org.kitodo.enums.GenerationMode;
import org.kitodo.helper.VariableReplacer;
import org.kitodo.model.Subfolder;
import org.kitodo.services.image.ImageGenerator;

public class ImageGeneratorIT {

    // Test data

    Path tiff = Paths.get("../Kitodo-LongTimePreservationValidationModule/src/test/resources/rose.tif");
    Path jpg = Paths.get("../Kitodo-LongTimePreservationValidationModule/src/test/resources/rose.jpg");

    // These settings are needed in both test variants

    Integer processId = 42;
    String processTitle = "ImagGeTe_1234567X";
    String tiffType = "image/tiff";
    String jpegType = "image/jpeg";
    String metadata = ConfigCore.getKitodoDataDirectory();

    // Settings for the test with separate directories

    String tiffFolder = "(processtitle)_tif";
    String jpgsMaxFolder = "jpgs/max";
    String processTiffFolder = tiffFolder.replace("(processtitle)", processTitle);

    Path inputFileOne = Paths.get(metadata, processId.toString(), processTiffFolder, "00000001.tif");
    Path inputFileTwo = Paths.get(metadata, processId.toString(), processTiffFolder, "00000002.tif");
    Path inputFileThree = Paths.get(metadata, processId.toString(), processTiffFolder, "00000003.tif");

    Path resultFileOne = Paths.get(metadata, processId.toString(), jpgsMaxFolder, "00000001.jpg");
    Path resultFileTwo = Paths.get(metadata, processId.toString(), jpgsMaxFolder, "00000002.jpg");
    Path resultFileThree = Paths.get(metadata, processId.toString(), jpgsMaxFolder, "00000003.jpg");

    // Settings for the test with mixed files in one folder

    String mixedFolder = "images";
    String tiffPathMixed = mixedFolder + File.separatorChar + "*.tif.original.tif";
    String jpegPathMixed = mixedFolder + File.separatorChar + "*.tif.max.jpg";

    Path mixedInputOne = Paths.get(metadata, processId.toString(), mixedFolder, "00000001.tif.original.tif");
    Path mixedInputTwo = Paths.get(metadata, processId.toString(), mixedFolder, "00000002.tif.original.tif");
    Path mixedInputThree = Paths.get(metadata, processId.toString(), mixedFolder, "00000003.tif.original.tif");

    Path mixedResultOne = Paths.get(metadata, processId.toString(), mixedFolder, "00000001.tif.max.jpg");
    Path mixedResultTwo = Paths.get(metadata, processId.toString(), mixedFolder, "00000002.tif.max.jpg");
    Path mixedResultThree = Paths.get(metadata, processId.toString(), mixedFolder, "00000003.tif.max.jpg");

    /**
     * Let the thread sleep for a while.
     * 
     * @param duration
     *            time to sleep
     * @param unit
     *            unit of time to sleep
     */
    private static void justSleep(long duration, TimeUnit unit) {
        long bedtime = System.nanoTime();
        while (System.nanoTime() - bedtime < unit.toNanos(duration)) {
            try {
                Thread.sleep(unit.toMillis(duration));
            } catch (InterruptedException e) {
                // Stop waking me up. I am sleeping.
            }
        }
    }

    /**
     * Determines the modification time for a file. It is used to determine if
     * the file has been modified in the test.
     * 
     * @param path
     *            file path
     * @return an optional with the change time. If the file does not exist, the
     *         optional is empty.
     * @throws IOException
     *             when the hard disk operation is disturbed
     */
    private static Optional<FileTime> lastModifiedTime(Path path) throws IOException {
        if (!path.toFile().exists()) {
            return Optional.empty();
        } else {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return Optional.of(attributes.lastModifiedTime());
        }
    }

    /**
     * Sets a class field by introspection.
     * 
     * @param subject
     *            memory element to be changed
     * @param predicate
     *            field to be changed
     * @param object
     *            new value of the field
     * @throws Exception
     *             if it does not work
     */
    private static void setField(Object subject, String predicate, Object object) throws Exception {
        Field relation = subject.getClass().getDeclaredField(predicate);
        relation.setAccessible(true);
        relation.set(subject, object);
    }

    /**
     * First, any garbage that might have been left over from a previous test
     * (for example, when the debugger was aborted) is deleted. Then, a task
     * directory is created with three subfolders: one for the input files, one
     * for the results; and a third directory for the mixed data in one folder
     * test. The input folder gets three TIFF files. One already created and one
     * broken image go into the destination directory. This happens twice, for
     * the two test variants.
     */
    @Before
    public void setUp() throws IOException {
        Path processDir = Paths.get(metadata, processId.toString());
        if (processDir.toFile().exists()) {
            Files.walk(processDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }

        Files.createDirectories(Paths.get(metadata, processId.toString(), processTiffFolder));
        Files.createDirectories(Paths.get(metadata, processId.toString(), jpgsMaxFolder));
        Files.createDirectories(Paths.get(metadata, processId.toString(), mixedFolder));

        Files.copy(tiff, inputFileOne, REPLACE_EXISTING);
        Files.copy(tiff, inputFileTwo, REPLACE_EXISTING);
        Files.copy(tiff, inputFileThree, REPLACE_EXISTING);
        Files.copy(jpg, resultFileOne, REPLACE_EXISTING);
        Files.write(resultFileTwo, Arrays.asList("No, this is not a JPG file."));

        Files.copy(tiff, mixedInputOne, REPLACE_EXISTING);
        Files.copy(tiff, mixedInputTwo, REPLACE_EXISTING);
        Files.copy(tiff, mixedInputThree, REPLACE_EXISTING);
        Files.copy(jpg, mixedResultOne, REPLACE_EXISTING);
        Files.write(mixedResultTwo, Arrays.asList("No, this is not a JPG file."));

        justSleep(2, TimeUnit.SECONDS);
    }

    /**
     * Deletes the files created in the test.
     */
    @After
    public void tearDown() throws IOException {
        Path processDir = Paths.get(metadata, processId.toString());
        Files.walk(processDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }

    /**
     * This test simulates the default scenario in which the source files and
     * derivatives reside in different task folders. In the test, all files are
     * to be regenerated, which means that the timestamps of all files have to
     * change.
     * 
     * @throws Exception
     *             if it does not work
     */
    @Test
    public void testTheNewGenerationOfAllImagesorFilesInDifferentFolders() throws Exception {

        Process process = new Process();
        process.setId(processId);
        process.setTitle(processTitle);
        SubfolderType source = new SubfolderType();
        source.setPath(tiffFolder);
        source.setMimeType(tiffType);
        Subfolder sourceFolder = new Subfolder(process, source);
        VariableReplacer variableReplacer = new MockVariableReplacer(processTitle);
        setField(sourceFolder, "variableReplacer", variableReplacer);
        SubfolderType output = new SubfolderType();
        output.setPath(jpgsMaxFolder);
        output.setMimeType(jpegType);
        output.setDerivative(1.0);
        Subfolder outputFolder = new Subfolder(process, output);
        setField(outputFolder, "variableReplacer", variableReplacer);
        Collection<Subfolder> outputs = Arrays.asList(outputFolder);
        ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, GenerationMode.ALL, outputs);

        Optional<FileTime> resultFileOneBefore = lastModifiedTime(resultFileOne);
        Optional<FileTime> resultFileTwoBefore = lastModifiedTime(resultFileTwo);
        final Optional<FileTime> resultFileThreeBefore = lastModifiedTime(resultFileThree);

        imageGenerator.run();

        assertThat(resultFileOne + " was not changed but should have",
            lastModifiedTime(resultFileOne), is(not(equalTo(resultFileOneBefore))));
        assertThat(resultFileTwo + " was not changed but should have",
            lastModifiedTime(resultFileTwo), is(not(equalTo(resultFileTwoBefore))));
        assertThat(resultFileThree + " was not changed but should have",
            lastModifiedTime(resultFileThree), is(not(equalTo(resultFileThreeBefore))));
    }

    /**
     * This test simulates the default scenario in which the source files and
     * derivatives reside in different task folders. In the test, only missing
     * files are to be regenerated, which means that only the time stamp for the
     * third file, which does not yet exist in the destination folder before the
     * test, has to change.
     * 
     * @throws Exception
     *             if it does not work
     */
    @Test
    public void testRecreatingAllMissingImagesForFilesInDifferentFolders() throws Exception {

        Process process = new Process();
        process.setId(processId);
        process.setTitle(processTitle);
        SubfolderType source = new SubfolderType();
        source.setPath(tiffFolder);
        source.setMimeType(tiffType);
        Subfolder sourceFolder = new Subfolder(process, source);
        VariableReplacer variableReplacer = new MockVariableReplacer(processTitle);
        setField(sourceFolder, "variableReplacer", variableReplacer);
        SubfolderType output = new SubfolderType();
        output.setPath(jpgsMaxFolder);
        output.setMimeType(jpegType);
        output.setDerivative(1.0);
        Subfolder outputFolder = new Subfolder(process, output);
        setField(outputFolder, "variableReplacer", variableReplacer);
        Collection<Subfolder> outputs = Arrays.asList(outputFolder);
        ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, GenerationMode.MISSING, outputs);

        Optional<FileTime> resultFileOneBefore = lastModifiedTime(resultFileOne);
        Optional<FileTime> resultFileTwoBefore = lastModifiedTime(resultFileTwo);
        final Optional<FileTime> resultFileThreeBefore = lastModifiedTime(resultFileThree);

        imageGenerator.run();

        assertThat(resultFileOne + " was changed but should not have",
            lastModifiedTime(resultFileOne), is(equalTo(resultFileOneBefore)));
        assertThat(resultFileTwo + " was changed but should not have",
            lastModifiedTime(resultFileTwo), is(equalTo(resultFileTwoBefore)));
        assertThat(resultFileThree + " was not changed but should have",
            lastModifiedTime(resultFileThree), is(not(equalTo(resultFileThreeBefore))));
    }

    /**
     * This test simulates the default scenario in which the source files and
     * derivatives reside in different task folders. In the test missing and
     * damaged files are to be regenerated, which means that the time stamp for
     * the second and third file must change, while the first one must not be
     * touched.
     * 
     * @throws Exception
     *             if it does not work
     */
    @Test
    public void testRecreatingAllMissingOrDamagedImagesForFilesInDifferentFolders() throws Exception {

        Process process = new Process();
        process.setId(processId);
        process.setTitle(processTitle);
        SubfolderType source = new SubfolderType();
        source.setPath(tiffFolder);
        source.setMimeType(tiffType);
        Subfolder sourceFolder = new Subfolder(process, source);
        VariableReplacer variableReplacer = new MockVariableReplacer(processTitle);
        setField(sourceFolder, "variableReplacer", variableReplacer);
        SubfolderType output = new SubfolderType();
        output.setPath(jpgsMaxFolder);
        output.setMimeType(jpegType);
        output.setDerivative(1.0);
        Subfolder outputFolder = new Subfolder(process, output);
        setField(outputFolder, "variableReplacer", variableReplacer);
        Collection<Subfolder> outputs = Arrays.asList(outputFolder);
        ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, GenerationMode.MISSING_OR_DAMAGED, outputs);

        Optional<FileTime> resultFileOneBefore = lastModifiedTime(resultFileOne);
        Optional<FileTime> resultFileTwoBefore = lastModifiedTime(resultFileTwo);
        final Optional<FileTime> resultFileThreeBefore = lastModifiedTime(resultFileThree);

        imageGenerator.run();

        assertThat(resultFileOne + " was changed but should not have",
            lastModifiedTime(resultFileOne), is(equalTo(resultFileOneBefore)));
        assertThat(resultFileTwo + " was not changed but should have",
            lastModifiedTime(resultFileTwo), is(not(equalTo(resultFileTwoBefore))));
        assertThat(resultFileThree + " was not changed but should have",
            lastModifiedTime(resultFileThree), is(not(equalTo(resultFileThreeBefore))));
    }

    /**
     * This test simulates the scenario in which the source files and
     * derivatives are in the same subfolder. In the test, all files are to be
     * regenerated, which means that the timestamps of all files have to change.
     * 
     * @throws Exception
     *             if it does not work
     */
    @Test
    public void testTheNewGenerationOfAllImagesorFilesInTheSameFolder() throws Exception {

        Process process = new Process();
        process.setId(processId);
        process.setTitle(processTitle);
        SubfolderType source = new SubfolderType();
        source.setPath(tiffPathMixed);
        source.setMimeType(tiffType);
        Subfolder sourceFolder = new Subfolder(process, source);
        VariableReplacer variableReplacer = new MockVariableReplacer(processTitle);
        setField(sourceFolder, "variableReplacer", variableReplacer);
        SubfolderType output = new SubfolderType();
        output.setPath(jpegPathMixed);
        output.setMimeType(jpegType);
        output.setDerivative(1.0);
        Subfolder outputFolder = new Subfolder(process, output);
        setField(outputFolder, "variableReplacer", variableReplacer);
        Collection<Subfolder> outputs = Arrays.asList(outputFolder);
        ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, GenerationMode.ALL, outputs);

        Optional<FileTime> resultFileOneBefore = lastModifiedTime(mixedResultOne);
        Optional<FileTime> resultFileTwoBefore = lastModifiedTime(mixedResultTwo);
        final Optional<FileTime> resultFileThreeBefore = lastModifiedTime(mixedResultThree);

        imageGenerator.run();

        assertThat(mixedResultOne + " was not changed but should have",
            lastModifiedTime(mixedResultOne), is(not(equalTo(resultFileOneBefore))));
        assertThat(mixedResultTwo + " was not changed but should have",
            lastModifiedTime(mixedResultTwo), is(not(equalTo(resultFileTwoBefore))));
        assertThat(mixedResultThree + " was not changed but should have",
            lastModifiedTime(mixedResultThree), is(not(equalTo(resultFileThreeBefore))));
    }

    /**
     * This test simulates the scenario in which the source files and
     * derivatives are in the same subfolder. In the test, only missing files
     * are to be regenerated, which means that only the time stamp for the third
     * file, which does not yet exist in the destination folder before the test,
     * has to change.
     * 
     * @throws Exception
     *             if it does not work
     */
    @Test
    public void testRecreatingAllMissingImagesForFilesInTheSameFolder() throws Exception {

        Process process = new Process();
        process.setId(processId);
        process.setTitle(processTitle);
        SubfolderType source = new SubfolderType();
        source.setPath(tiffPathMixed);
        source.setMimeType(tiffType);
        Subfolder sourceFolder = new Subfolder(process, source);
        VariableReplacer variableReplacer = new MockVariableReplacer(processTitle);
        setField(sourceFolder, "variableReplacer", variableReplacer);
        SubfolderType output = new SubfolderType();
        output.setPath(jpegPathMixed);
        output.setMimeType(jpegType);
        output.setDerivative(1.0);
        Subfolder outputFolder = new Subfolder(process, output);
        setField(outputFolder, "variableReplacer", variableReplacer);
        Collection<Subfolder> outputs = Arrays.asList(outputFolder);
        ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, GenerationMode.MISSING, outputs);

        Optional<FileTime> resultFileOneBefore = lastModifiedTime(mixedResultOne);
        Optional<FileTime> resultFileTwoBefore = lastModifiedTime(mixedResultTwo);
        final Optional<FileTime> resultFileThreeBefore = lastModifiedTime(mixedResultThree);

        imageGenerator.run();

        assertThat(mixedResultOne + " was changed but should not have",
            lastModifiedTime(mixedResultOne), is(equalTo(resultFileOneBefore)));
        assertThat(mixedResultTwo + " was changed but should not have",
            lastModifiedTime(mixedResultTwo), is(equalTo(resultFileTwoBefore)));
        assertThat(mixedResultThree + " was not changed but should have",
            lastModifiedTime(mixedResultThree), is(not(equalTo(resultFileThreeBefore))));
    }

    /**
     * This test simulates the scenario in which the source files and
     * derivatives are in the same subfolder. In the test missing and damaged
     * files are to be regenerated, which means that the time stamp for the
     * second and third file must change, while the first one must not be
     * touched.
     * 
     * @throws Exception
     *             if it does not work
     */
    @Test
    public void testRecreatingAllMissingOrDamagedImagesForFilesInTheSameFolder() throws Exception {

        Process process = new Process();
        process.setId(processId);
        process.setTitle(processTitle);
        SubfolderType source = new SubfolderType();
        source.setPath(tiffPathMixed);
        source.setMimeType(tiffType);
        Subfolder sourceFolder = new Subfolder(process, source);
        VariableReplacer variableReplacer = new MockVariableReplacer(processTitle);
        setField(sourceFolder, "variableReplacer", variableReplacer);
        SubfolderType output = new SubfolderType();
        output.setPath(jpegPathMixed);
        output.setMimeType(jpegType);
        output.setDerivative(1.0);
        Subfolder outputFolder = new Subfolder(process, output);
        setField(outputFolder, "variableReplacer", variableReplacer);
        Collection<Subfolder> outputs = Arrays.asList(outputFolder);
        ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, GenerationMode.MISSING_OR_DAMAGED, outputs);

        Optional<FileTime> resultFileOneBefore = lastModifiedTime(mixedResultOne);
        Optional<FileTime> resultFileTwoBefore = lastModifiedTime(mixedResultTwo);
        final Optional<FileTime> resultFileThreeBefore = lastModifiedTime(mixedResultThree);

        imageGenerator.run();

        assertThat(mixedResultOne + " was changed but should not have",
            lastModifiedTime(mixedResultOne), is(equalTo(resultFileOneBefore)));
        assertThat(mixedResultTwo + " was not changed but should have",
            lastModifiedTime(mixedResultTwo), is(not(equalTo(resultFileTwoBefore))));
        assertThat(mixedResultThree + " was not changed but should have",
            lastModifiedTime(mixedResultThree), is(not(equalTo(resultFileThreeBefore))));
    }
}
