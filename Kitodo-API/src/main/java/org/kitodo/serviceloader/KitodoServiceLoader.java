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

package org.kitodo.serviceloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.KitodoConfig;

public class KitodoServiceLoader<T> {
    private Class<T> clazz;
    private String modulePath = "";

    /**
     * <p>The class loader chain keeps track of the respective newest class loader
     * created for loading new jar files. Previously loaded jar files are
     * found by delegating requests to each parent class loader, and finally,
     * to the webapp and system class loader. See:</p>
     *
     * <p>http://tomcat.apache.org/tomcat-9.0-doc/class-loader-howto.html</p>
     *
     * <p>Module/Plugin classes loaded from jar files can only be accessed through
     * this class loader.</p>
     *
     * <p>In the future, a refresh mechanism could be implemented by throwing away
     * this chain, starting a new one and reloading all jars.</p>
     */
    private static ClassLoader classLoaderChain = Thread.currentThread().getContextClassLoader();

    /**
     * Already loaded jars are remembered by their file path, and thus, not
     * loaded multiple times during runtime.
     */
    private static final Set<String> loadedJars = new HashSet<>();

    private static final String POM_PROPERTIES_FILE = "pom.properties";
    private static final String ARTIFACT_ID_PROPERTY = "artifactId";
    private static final String TEMP_DIR_PREFIX = "kitodo_";
    private static final String META_INF_FOLDER = "META-INF";
    private static final String RESOURCES_FOLDER = "resources";
    private static final String PAGES_FOLDER = "pages";
    private static final String JAR = "*.jar";
    private static final String ERROR = "Classpath could not be accessed";

    private static final Path SYSTEM_TEMP_FOLDER = FileSystems.getDefault()
            .getPath(System.getProperty("java.io.tmpdir"));

    private static final Logger logger = LogManager.getLogger(KitodoServiceLoader.class);

    /**
     * Constructor for KitodoServiceLoader.
     *
     * @param clazz
     *            interface class of module to load
     */
    public KitodoServiceLoader(Class<T> clazz) {
        String modulesDirectory = KitodoConfig.getKitodoModulesDirectory();
        this.clazz = clazz;
        File kitodoModules = new File(modulesDirectory).getAbsoluteFile();
        if (kitodoModules.exists()) {
            this.modulePath = modulesDirectory;
        } else {
            logger.error("Specified module folder does not exist: {}", kitodoModules);
        }
    }

    private ServiceLoader<T> getClassLoader() {
        loadModulesIntoClasspath();
        loadBeans();
        loadFrontendFilesIntoCore();
        // services and their classes need to be loaded from the class loader
        // chain instead of the default class loader
        return ServiceLoader.load(clazz, KitodoServiceLoader.classLoaderChain);
    }

    /**
     * Loads a module from the classpath which implements the constructed clazz.
     * Frontend files of all modules will be loaded into the core module.
     *
     * @return A module with type T.
     */
    public T loadModule() {
        ServiceLoader<T> loader = getClassLoader();
        Iterator<T> loaderIterator = loader.iterator();
        if (!loaderIterator.hasNext()) {
            logger.error("Couldn't find a module for {}!", clazz);
        }
        return loaderIterator.next();
    }

    /**
     * Loads and returns all modules from the classpath which implement the constructed clazz.
     * @return List of modules with type T
     */
    public List<T> loadModules() {
        ServiceLoader<T> loader = getClassLoader();
        LinkedList<T> modules = new LinkedList<>();
        loader.iterator().forEachRemaining(modules::add);
        return modules;
    }

    /**
     * Loads bean classes and registers them to the FacesContext. Afterwards
     * they can be used in all frontend files
     */
    private void loadBeans() {
        Path moduleFolder = FileSystems.getDefault().getPath(modulePath);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleFolder, JAR)) {
            for (Path f : stream) {
                try (JarFile jarFile = new JarFile(f.toString())) {
                    if (hasFrontendFiles(jarFile)) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        URL[] urls = {new URL("jar:file:" + f + "!/") };
                        try (URLClassLoader cl = URLClassLoader.newInstance(urls)) {
                            while (entries.hasMoreElements()) {
                                JarEntry je = entries.nextElement();
                                /*
                                 * IMPORTANT: Naming convention: the name of the
                                 * java class has to be in upper camel case or
                                 * "pascal case" and must be equal to the file
                                 * name of the corresponding facelet file
                                 * concatenated with the word "Form".
                                 *
                                 * Example: template filename "sample.xhtml" =>
                                 * "SampleForm.java"
                                 *
                                 * That is the reason for the following check
                                 * (e.g. whether the JarEntry name ends with
                                 * "Form.class")
                                 */
                                if (je.isDirectory() || !je.getName().endsWith("Form.class")) {
                                    continue;
                                }

                                String className = je.getName().substring(0, je.getName().length() - 6);
                                className = className.replace('/', '.');
                                Class<?> aClass = cl.loadClass(className);
                                String beanName = className.substring(className.lastIndexOf('.') + 1).trim();

                                FacesContext facesContext = FacesContext.getCurrentInstance();
                                HttpSession session = (HttpSession) facesContext.getExternalContext().getSession(false);
                                Object newInstance = aClass.getDeclaredConstructor().newInstance();
                                session.getServletContext().setAttribute(beanName, newInstance);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(ERROR, e.getMessage());
        }
    }

    /**
     * If the found jar files have frontend files, they will be extracted and
     * copied into the frontend folder of the core module. Before copying,
     * existing frontend files of the same module will be deleted from the core
     * module. Afterwards the created temporary folder will be deleted as well.
     */
    private void loadFrontendFilesIntoCore() {

        Path moduleFolder = FileSystems.getDefault().getPath(modulePath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleFolder, JAR)) {

            for (Path f : stream) {
                File loc = new File(f.toString());
                try (JarFile jarFile = new JarFile(loc)) {

                    if (hasFrontendFiles(jarFile)) {

                        Path temporaryFolder = Files.createTempDirectory(SYSTEM_TEMP_FOLDER, TEMP_DIR_PREFIX);

                        File tempDir = new File(Paths.get(temporaryFolder.toUri()).toAbsolutePath().toString());

                        extractFrontEndFiles(loc.getAbsolutePath(), tempDir);

                        String moduleName = extractModuleName(tempDir);
                        if (moduleName.isEmpty()) {
                            logger.info("No module found in JarFile '{}'.", jarFile.getName());

                        } else {
                            FacesContext facesContext = FacesContext.getCurrentInstance();
                            HttpSession session = (HttpSession) facesContext.getExternalContext().getSession(false);

                            String filePath = session.getServletContext().getRealPath(File.separator + PAGES_FOLDER)
                                    + File.separator + moduleName;
                            FileUtils.deleteDirectory(new File(filePath));

                            String resourceFolder = String.join(File.separator,
                                    Arrays.asList(tempDir.getAbsolutePath(), META_INF_FOLDER, RESOURCES_FOLDER));
                            copyFrontEndFiles(resourceFolder, filePath);
                        }
                        FileUtils.deleteDirectory(tempDir);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(ERROR, e.getMessage());
        }
    }

    /**
     * Extracts the module name of the current module by finding the
     * pom.properties in the given temporary folder
     *
     * @param temporaryFolder
     *            folder in which the pom.properties file will be searched for
     *
     * @return String
     */
    private String extractModuleName(File temporaryFolder) throws IOException {
        String moduleName = "";
        File properties = findFile(POM_PROPERTIES_FILE, temporaryFolder);
        try (InputStream input = new FileInputStream(properties)) {
            Properties prop = new Properties();
            prop.load(input);
            moduleName = prop.getProperty(ARTIFACT_ID_PROPERTY);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage());
        }
        return moduleName;
    }

    /**
     * Copies extracted frontend files by a given source folder name to the
     * destination Folder given by a destination folder name.
     *
     * @param sourceFolder
     *            copies all extracted frontend files
     * @param destinationFolder
     *            jarFile that will be checked for frontend files
     */
    private void copyFrontEndFiles(String sourceFolder, String destinationFolder) throws IOException {
        FileUtils.copyDirectory(new File(sourceFolder), new File(destinationFolder));
    }

    /**
     * Checks, whether a passed jarFile has frontend files or not. Returns true,
     * when the jar contains a folder with the name "resources".
     *
     * @param jarPath
     *            jarFile that will be checked for frontend files
     * @param destinationFolder
     *            destination path, where the frontend files will be extracted
     *            to
     *
     */
    private void extractFrontEndFiles(String jarPath, File destinationFolder) throws IOException {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdir();
        }

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> jarEntries = jar.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry currentJarEntry = jarEntries.nextElement();

                if (currentJarEntry.getName().contains(RESOURCES_FOLDER)
                        || currentJarEntry.getName().contains(POM_PROPERTIES_FILE)) {
                    File resourceFile = new File(destinationFolder + File.separator + currentJarEntry.getName());
                    if (!resourceFile.toPath().normalize().startsWith(destinationFolder.toPath())) {
                        throw new IOException("ZIP file damaged! Invalid entry: " + currentJarEntry.getName());
                    }
                    if (currentJarEntry.isDirectory()) {
                        resourceFile.mkdirs();
                        continue;
                    }
                    if (currentJarEntry.getName().contains(POM_PROPERTIES_FILE)) {
                        resourceFile.getParentFile().mkdirs();
                    }

                    try (InputStream inputStream = jar.getInputStream(currentJarEntry);
                            FileOutputStream fos = new FileOutputStream(resourceFile)) {
                        while (inputStream.available() > 0) {
                            fos.write(inputStream.read());
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks, whether a passed jarFile has frontend files or not. Returns true,
     * when the jar contains a folder with the name "resources"
     *
     * @param jarFile
     *            jarFile that will be checked for frontend files
     *
     * @return boolean
     */
    private boolean hasFrontendFiles(JarFile jarFile) {
        Enumeration<JarEntry> enums = jarFile.entries();
        while (enums.hasMoreElements()) {
            JarEntry jarEntry = enums.nextElement();
            if (jarEntry.getName().contains(RESOURCES_FOLDER) && jarEntry.isDirectory()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to find a file by a given file name in a folder by folder name.
     *
     * @param name
     *            file name that will be searched for
     * @param folder
     *            folder that will be searched
     *
     * @return File
     *
     * @throws FileNotFoundException
     *             when File with given name could not be found in given folder
     *
     */
    private File findFile(String name, File folder) throws FileNotFoundException {
        Collection<File> files = FileUtils.listFiles(folder, null, true);
        for (File currentFile : files) {
            if (currentFile.getName().equals(name)) {
                return currentFile;
            }
        }
        throw new FileNotFoundException(
                "ERROR: file '" + name + "' not found in folder '" + folder.getAbsolutePath() + "'!");
    }

    /**
     * <p>Loads jars from the modules directory by creating a separate class
     * loader each time jars are loaded, connected in a chain of class loaders
     * through their parent relationship.
     * A ServiceLoader can find them when using the most recent class loader
     * added to the chain of class loaders.</p>
     *
     * <p>If used inappropriately, this may lead to unexpected behaviour, e.g.,
     * when referring to the same singleton from multiple modules, since
     * classes could be loaded twice.</p>
     *
     * <p>If several modules depend on each other (load classes from another
     * module), both modules have to be present at the same time when loading
     * happens. Otherwise, the order at which jars are loaded could break
     * things, since new classes will not be visible to jars loaded by an
     * earlier class loader created at an earlier time.</p>
     */
    private void loadModulesIntoClasspath() {
        Path moduleFolder = FileSystems.getDefault().getPath(modulePath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleFolder, JAR)) {

            // collect urls of new jars present in the module directory
            Set<URL> jarsToBeAdded = new HashSet<>();
            for (Path f : stream) {

                File loc = new File(f.toString());
                URL url = loc.toURI().toURL();

                if (!KitodoServiceLoader.loadedJars.contains(url.toString())) {
                    jarsToBeAdded.add(url);
                }
            }

            // create a single URL class loader with all jars
            // such that plugins can load classes from each other
            if (!jarsToBeAdded.isEmpty()) {

                for (URL url : jarsToBeAdded) {
                    logger.info("Loading module jar file from path " + url.toString());
                    KitodoServiceLoader.loadedJars.add(url.toString());
                }
                URL[] urls = new URL[jarsToBeAdded.size()];
                jarsToBeAdded.toArray(urls);
                classLoaderChain = new URLClassLoader(urls, KitodoServiceLoader.classLoaderChain);
            }
        } catch (IOException e) {
            logger.error(ERROR, e.getMessage());
        }
    }

}
