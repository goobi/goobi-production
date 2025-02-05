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

package org.kitodo.data.database.beans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.PostLoad;
import javax.persistence.PostUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.LazyInitializationException;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.kitodo.data.database.enums.PreviewHoverMode;
import org.kitodo.data.database.persistence.ProjectDAO;

@Entity
@Table(name = "project")
public class Project extends BaseBean implements Comparable<Project> {

    @Column(name = "title", nullable = false, unique = true)
    private String title;

    @Column(name = "dmsImportRootPath")
    private String dmsImportRootPath;

    @Column(name = "metsRightsOwner")
    private String metsRightsOwner = "";

    @Column(name = "metsRightsOwnerLogo")
    private String metsRightsOwnerLogo = "";

    @Column(name = "metsRightsOwnerSite")
    private String metsRightsOwnerSite = "";

    @Column(name = "metsRightsOwnerMail")
    private String metsRightsOwnerMail = "";

    @Column(name = "metsDigiprovReference")
    private String metsDigiprovReference = "";

    @Column(name = "metsDigiprovPresentation")
    private String metsDigiprovPresentation = "";

    @Column(name = "metsPointerPath")
    private String metsPointerPath = "";

    @Column(name = "metsPurl")
    private String metsPurl = "";

    @Column(name = "metsContentId")
    private String metsContentIDs = "";

    @Column(name = "startDate")
    private Date startDate;

    @Column(name = "endDate")
    private Date endDate;

    @Column(name = "numberOfPages")
    private Integer numberOfPages;

    @Column(name = "numberOfVolumes")
    private Integer numberOfVolumes;

    @Column(name = "active")
    private Boolean active = true;

    @ManyToMany(mappedBy = "projects", cascade = CascadeType.PERSIST)
    private List<User> users;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Process> processes;

    @Transient
    private boolean hasProcesses;

    @LazyCollection(LazyCollectionOption.FALSE)
    @ManyToMany(mappedBy = "projects", cascade = CascadeType.PERSIST)
    private List<Template> templates;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_importconfiguration_id",
            foreignKey = @ForeignKey(name = "FK_project_default_importconfiguration_id"))
    private ImportConfiguration defaultImportConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_child_process_importconfiguration_id",
            foreignKey = @ForeignKey(name = "FK_project_default_child_process_importconfiguration_id"))
    private ImportConfiguration defaultChildProcessImportConfiguration;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> folders;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", foreignKey = @ForeignKey(name = "FK_project_client_id"))
    private Client client;

    /**
     * Folder to use as source for generation of derived resources.
     */
    @ManyToOne
    @JoinColumn(name = "generatorSource_folder_id", foreignKey = @ForeignKey(name = "FK_project_generatorSource_folder_id"))
    private Folder generatorSource;

    /**
     * Folder with media to use for the viewer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mediaView_folder_id", foreignKey = @ForeignKey(name = "FK_project_mediaView_folder_id"))
    private Folder mediaView;

    /**
     * Folder with media to use for the preview.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preview_folder_id", foreignKey = @ForeignKey(name = "FK_project_preview_folder_id"))
    private Folder preview;

    /**
     * Field to define mode of hover in preview.
     */
    @Column(name = "preview_hover_mode")
    @Enumerated(EnumType.STRING)
    private PreviewHoverMode previewHoverMode = PreviewHoverMode.OVERLAY;

    /**
     * Folder with media to use for the audio preview.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preview_audio_folder_id", foreignKey = @ForeignKey(name = "FK_project_preview_audio_folder_id"))
    private Folder audioPreview;

    /**
     * Folder with media to use for the audio viewer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mediaView_audio_folder_id", foreignKey = @ForeignKey(name = "FK_project_mediaView_audio_folder_id"))
    private Folder audioMediaView;

    /**
     * Field to define the status of the audio media view waveform.
     */
    @Column(name = "mediaView_audio_waveform")
    private Boolean audioMediaViewWaveform = false;

    /**
     * Folder with media to use for the video preview.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preview_video_folder_id", foreignKey = @ForeignKey(name = "FK_project_preview_video_folder_id"))
    private Folder videoPreview;

    /**
     * Folder with media to use for the video viewer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mediaView_video_folder_id", foreignKey = @ForeignKey(name = "FK_project_mediaView_video_folder_id"))
    private Folder videoMediaView;

    /**
     * Filename length for renaming media files of processes in this project.
     */
    @Column(name = "filename_length")
    private Integer filenameLength;

    /**
     * Constructor.
     */
    public Project() {
        this.processes = new ArrayList<>();
        this.users = new ArrayList<>();
        this.folders = new ArrayList<>();
        this.dmsImportRootPath = "";
    }

    @PostLoad
    @PostUpdate
    private void onPostLoad() {
        this.hasProcesses = CollectionUtils.isNotEmpty(processes);
    }

    /**
     * Returns the name of the project.
     *
     * @return the name of the project
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the name of the project.
     *
     * @param title
     *            the name of the project
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the users contributing to this project.
     *
     * @return the users contributing to this project
     */
    public List<User> getUsers() {
        initialize(new ProjectDAO(), this.users);
        if (Objects.isNull(this.users)) {
            this.users = new ArrayList<>();
        }
        return this.users;
    }

    /**
     * Specifies the users who will contribute to this project.
     *
     * @param users
     *            the users contributing to this project
     */
    public void setUsers(List<User> users) {
        this.users = users;
    }

    /**
     * Returns the list of processes of this project.
     *
     * @return the folders
     */
    public List<Process> getProcesses() {
        initialize(new ProjectDAO(), this.processes);
        if (Objects.isNull(this.processes)) {
            this.processes = new ArrayList<>();
        }
        return this.processes;
    }

    public void setProcesses(List<Process> processes) {
        this.processes = processes;
    }

    /**
     * Get templates.
     *
     * @return value of templates
     */
    public List<Template> getTemplates() {
        initialize(new ProjectDAO(), this.templates);
        if (Objects.isNull(this.templates)) {
            this.templates = new ArrayList<>();
        }
        return this.templates;
    }

    /**
     * Set templates.
     *
     * @param templates
     *            as list of templates
     */
    public void setTemplates(List<Template> templates) {
        this.templates = templates;
    }

    public String getDmsImportRootPath() {
        return this.dmsImportRootPath;
    }

    public void setDmsImportRootPath(String dmsImportRootPath) {
        this.dmsImportRootPath = dmsImportRootPath;
    }

    /**
     * Returns the list of folders of this project.
     *
     * @return the folders
     */
    public List<Folder> getFolders() {
        initialize(new ProjectDAO(), this.folders);
        if (Objects.isNull(this.folders)) {
            this.folders = new ArrayList<>();
        }
        return this.folders;
    }

    /**
     * Sets the list of folders of this project.
     *
     * @param folders
     *            list of folders to set
     */
    public void setFolders(List<Folder> folders) {
        this.folders = folders;
    }

    /**
     * Returns the name of the copyright holder of the business objects. These
     * are often the digitizing institution and also the sponsor.
     *
     * @return metsRightsOwner the name of the copyright holder
     */
    public String getMetsRightsOwner() {
        return this.metsRightsOwner;
    }

    /**
     * Sets the name of the copyright owner of the business objects. The
     * effective maximum length of this VARCHAR field is subject to the maximum
     * row size of 64k shared by all columns, and the charset.
     *
     * @param metsRightsOwner
     *            the name of the copyright holder
     */
    public void setMetsRightsOwner(String metsRightsOwner) {
        this.metsRightsOwner = metsRightsOwner;
    }

    public String getMetsRightsOwnerLogo() {
        return this.metsRightsOwnerLogo;
    }

    public void setMetsRightsOwnerLogo(String metsRightsOwnerLogo) {
        this.metsRightsOwnerLogo = metsRightsOwnerLogo;
    }

    public String getMetsRightsOwnerSite() {
        return this.metsRightsOwnerSite;
    }

    public void setMetsRightsOwnerSite(String metsRightsOwnerSite) {
        this.metsRightsOwnerSite = metsRightsOwnerSite;
    }

    public String getMetsRightsOwnerMail() {
        return this.metsRightsOwnerMail;
    }

    public void setMetsRightsOwnerMail(String metsRigthsOwnerMail) {
        this.metsRightsOwnerMail = metsRigthsOwnerMail;
    }

    public String getMetsDigiprovReference() {
        return this.metsDigiprovReference;
    }

    public void setMetsDigiprovReference(String metsDigiprovReference) {
        this.metsDigiprovReference = metsDigiprovReference;
    }

    public String getMetsDigiprovPresentation() {
        return this.metsDigiprovPresentation;
    }

    public void setMetsDigiprovPresentation(String metsDigiprovPresentation) {
        this.metsDigiprovPresentation = metsDigiprovPresentation;
    }

    public String getMetsPointerPath() {
        return this.metsPointerPath;
    }

    public void setMetsPointerPath(String metsPointerPath) {
        this.metsPointerPath = metsPointerPath;
    }

    public void setMetsPurl(String metsPurl) {
        this.metsPurl = metsPurl;
    }

    public String getMetsPurl() {
        return this.metsPurl;
    }

    public void setMetsContentIDs(String contentIDs) {
        this.metsContentIDs = contentIDs;
    }

    public String getMetsContentIDs() {
        return this.metsContentIDs;
    }

    /**
     * Returns the number of physical archival items to be digitized. This is a
     * freely configurable value, not a collected statistical value. The value
     * can be used to monitor whether the project is on time.
     *
     * @return number of volumes as Integer
     */
    public Integer getNumberOfVolumes() {
        if (this.numberOfVolumes == null) {
            this.numberOfVolumes = 0;
        }
        return this.numberOfVolumes;
    }

    /**
     * Sets the number of physical archival materials to be digitized. This is
     * usually roughly determined as part of project planning and can be stored
     * here as a reminder.
     *
     * @param numberOfVolumes
     *            as Integer
     */
    public void setNumberOfVolumes(Integer numberOfVolumes) {
        this.numberOfVolumes = numberOfVolumes;
    }

    /**
     * Returns the number of media objects to produce. This is a freely
     * configurable value, not a determined statistic. The value can be used to
     * monitor whether the project is on time.
     *
     * @return the number of media objects to produce
     */
    public Integer getNumberOfPages() {
        if (this.numberOfPages == null) {
            this.numberOfPages = 0;
        }
        return this.numberOfPages;
    }

    /**
     * Sets the number of media objects to produce. This is usually roughly
     * determined as part of project planning and can be stored here as a
     * reminder.
     *
     * @param numberOfPages
     *            the number of media objects to produce
     */
    public void setNumberOfPages(Integer numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    /**
     * Returns the start time of the project. {@link Date} is a specific instant
     * in time, with millisecond precision. It is a freely configurable value,
     * not the date the project object was created in the database. This can be,
     * for example, the start of the funding period.
     *
     * @return the start time
     */
    public Date getStartDate() {
        if (this.startDate == null) {
            this.startDate = new Date();
        }
        return this.startDate;
    }

    /**
     * Sets the start time of the project.
     *
     * @param startDate
     *            the start time
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Returns the project end time. {@link Date} is a specific instant in time,
     * with millisecond precision. This is a freely configurable value,
     * regardless of when the project was last actually worked on. For example,
     * this can be the time at which the project must be completed in order to
     * be billed. The timing can be used to monitor that the project is on time.
     *
     * @return the end time
     */
    public Date getEndDate() {
        if (this.endDate == null) {
            this.endDate = new Date();
        }
        return this.endDate;
    }

    /**
     * Sets the project end time.
     *
     * @param endDate
     *            the end time
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Sets whether the project is active. Deactivated projects are hidden from
     * general operation.
     *
     * @param active
     *            whether project is active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns whether the project is active. Completed projects are typically
     * not hard deleted, but are simply marked as completed. This means that
     * even in the event of complaints, the old stocks can still be accessed and
     * corrected.
     *
     * @return whether the project is active
     */
    public boolean isActive() {
        return this.active;
    }

    /**
     * Returns the client running this project.
     *
     * @return the client
     */
    public Client getClient() {
        return client;
    }

    /**
     * Specifies the tenant that is executing this project.
     *
     * @param client
     *            the client
     */
    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * Returns the folder to use as source for generation of derived resources
     * of this project.
     *
     * @return the source folder for generation
     */
    public Folder getGeneratorSource() {
        return generatorSource;
    }

    /**
     * Sets the folder to use as source for generation of derived resources of
     * this project.
     *
     * @param generatorSource
     *            source folder for generation to set
     */
    public void setGeneratorSource(Folder generatorSource) {
        this.generatorSource = generatorSource;
    }

    /**
     * Returns the folder to use for the media view.
     *
     * @return media view folder
     */
    public Folder getMediaView() {
        return mediaView;
    }

    /**
     * Sets the folder to use for the media view.
     *
     * @param mediaView
     *            media view folder
     */
    public void setMediaView(Folder mediaView) {
        this.mediaView = mediaView;
    }

    /**
     * Returns the folder to use for preview.
     *
     * @return preview folder
     */
    public Folder getPreview() {
        return preview;
    }

    /**
     * Sets the folder to use for preview.
     *
     * @param preview
     *         preview folder
     */
    public void setPreview(Folder preview) {
        this.preview = preview;
    }

    /**
     * Sets the preview hover mode.
     *
     * @param previewHoverMode preview hover mode
     */
    public void setPreviewHoverMode(PreviewHoverMode previewHoverMode) {
        this.previewHoverMode = previewHoverMode;
    }

    /**
     * Returns the preview hover mode.
     *
     * @return The preview hover mode
     */
    public PreviewHoverMode getPreviewHoverMode() {
        return previewHoverMode;
    }

    /**
     * Returns the folder to use for audio preview.
     *
     * @return audio preview folder
     */
    public Folder getAudioPreview() {
        return audioPreview;
    }

    /**
     * Sets the folder to use for audio preview.
     *
     * @param audioPreview
     *         audio preview folder
     */
    public void setAudioPreview(Folder audioPreview) {
        this.audioPreview = audioPreview;
    }

    /**
     * Returns the folder to use for the audio media view.
     *
     * @return the audio media view folder
     */
    public Folder getAudioMediaView() {
        return audioMediaView;
    }

    /**
     * Sets the folder to use for the audio media view.
     *
     * @param audioMediaView
     *         audio media view folder
     */
    public void setAudioMediaView(Folder audioMediaView) {
        this.audioMediaView = audioMediaView;
    }

    /**
     * Get the status of the audio media view waveform.
     *
     * @return True if is active
     */
    public boolean isAudioMediaViewWaveform() {
        return audioMediaViewWaveform;
    }

    /**
     * Set the status of the audio media view waveform.
     *
     * @param audioMediaViewWaveform True if is active
     */
    public void setAudioMediaViewWaveform(boolean audioMediaViewWaveform) {
        this.audioMediaViewWaveform = audioMediaViewWaveform;
    }

    /**
     * Returns the folder to use for video preview.
     *
     * @return video preview folder
     */
    public Folder getVideoPreview() {
        return videoPreview;
    }

    /**
     * Sets the folder to use for video preview.
     *
     * @param videoPreview
     *         video preview folder
     */
    public void setVideoPreview(Folder videoPreview) {
        this.videoPreview = videoPreview;
    }

    /**
     * Returns the folder to use for the video media view.
     *
     * @return the video media view folder
     */
    public Folder getVideoMediaView() {
        return videoMediaView;
    }

    /**
     * Sets the folder to use for the video media view.
     *
     * @param videoMediaView
     *         video media view folder
     */
    public void setVideoMediaView(Folder videoMediaView) {
        this.videoMediaView = videoMediaView;
    }

    /**
     * Get defaultImportConfiguration.
     *
     * @return value of defaultImportConfiguration
     */
    public ImportConfiguration getDefaultImportConfiguration() {
        return defaultImportConfiguration;
    }

    /**
     * Set defaultImportConfiguration.
     *
     * @param defaultImportConfiguration as org.kitodo.data.database.beans.ImportConfiguration
     */
    public void setDefaultImportConfiguration(ImportConfiguration defaultImportConfiguration) {
        this.defaultImportConfiguration = defaultImportConfiguration;
    }

    /**
     * Get defaultChildProcessImportConfiguration.
     *
     * @return value of defaultChildProcessImportConfiguration
     */
    public ImportConfiguration getDefaultChildProcessImportConfiguration() {
        return defaultChildProcessImportConfiguration;
    }

    /**
     * Set defaultChildProcessImportConfiguration.
     *
     * @param defaultChildProcessImportConfiguration as org.kitodo.data.database.beans.ImportConfiguration
     */
    public void setDefaultChildProcessImportConfiguration(ImportConfiguration defaultChildProcessImportConfiguration) {
        this.defaultChildProcessImportConfiguration = defaultChildProcessImportConfiguration;
    }

    /**
     * Get filename length.
     * @return filename length
     */
    public Integer getFilenameLength() {
        if (Objects.isNull(filenameLength)) {
            filenameLength = 8;
        }
        return filenameLength;
    }

    /**
     * Set filename length.
     * @param filenameLength as Integer
     */
    public void setFilenameLength(Integer filenameLength) {
        this.filenameLength = filenameLength;
    }

    @Override
    public int compareTo(Project project) {
        return this.getTitle().compareTo(project.getTitle());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Project) {
            Project project = (Project) object;
            return Objects.equals(this.getId(), project.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return this.title == null ? 0 : this.title.hashCode();
    }

    /**
     * Returns the non-deactivated production templates associated with the
     * project.
     *
     * @return the active production templates
     */
    public List<Template> getActiveTemplates() {
        if (Objects.isNull(templates)) {
            return Collections.emptyList();
        }
        return templates.stream().filter(template -> template.isActive()).collect(Collectors.toList());
    }

    /**
     * Sets the active production templates associated with the project. This
     * list is not guaranteed to be in reliable order.
     *
     * @param activeTemplates
     *            the active production templates
     */
    public void setActiveTemplates(List<Template> activeTemplates) {
        if (Objects.isNull(activeTemplates)) {
            activeTemplates = Collections.emptyList();
        }
        Map<Integer, Template> activeTemplatesMap = activeTemplates.stream()
                .collect(Collectors.toMap(Template::getId, Function.identity()));

        if (Objects.isNull(this.templates) && CollectionUtils.isNotEmpty(activeTemplates)) {
            this.templates = new ArrayList<>();
        }
        for (Template assignedTemplate : this.templates) {
            assignedTemplate.setActive(Objects.nonNull(activeTemplatesMap.remove(assignedTemplate.getId())));
        }
        for (Template unassignedTemplate : ((Map<Integer, Template>) activeTemplatesMap).values()) {
            unassignedTemplate.setActive(true);
            this.templates.add(unassignedTemplate);
        }
    }

    /**
     * Returns whether processes exist in the project. A project that contains
     * processes cannot be deleted.
     *
     * @return whether processes exist in the project
     */
    public boolean hasProcesses() {
        try {
            return CollectionUtils.isNotEmpty(processes);
        } catch (LazyInitializationException e) {
            return this.hasProcesses;
        }
    }

    /**
     * Set whether project has processes. The setter can be used when
     * representing data from a third-party source. Internally it depends on,
     * whether there are process objects in the database for a project. Setting
     * this to true cannot insert processes into the database.
     *
     * @param hasProcesses
     *            as boolean
     * @throws UnsupportedOperationException
     *             when trying to set this to true for a project without
     *             processes
     */
    public void setHasProcesses(boolean hasProcesses) {
        if (!hasProcesses && CollectionUtils.isNotEmpty(processes)) {
            processes.clear();
        } else if (hasProcesses && CollectionUtils.isEmpty(processes)) {
            throw new UnsupportedOperationException("cannot add processes");
        }
    }
}
