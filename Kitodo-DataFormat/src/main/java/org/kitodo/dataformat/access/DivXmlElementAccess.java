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

package org.kitodo.dataformat.access;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.tuple.Pair;
import org.kitodo.api.MdSec;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.MetadataGroup;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.kitodo.api.dataformat.mets.KitodoUUID;
import org.kitodo.dataformat.metskitodo.AmdSecType;
import org.kitodo.dataformat.metskitodo.DivType;
import org.kitodo.dataformat.metskitodo.KitodoType;
import org.kitodo.dataformat.metskitodo.MdSecType;
import org.kitodo.dataformat.metskitodo.MdSecType.MdWrap;
import org.kitodo.dataformat.metskitodo.MdSecType.MdWrap.XmlData;
import org.kitodo.dataformat.metskitodo.MetadataGroupType;
import org.kitodo.dataformat.metskitodo.MetadataType;
import org.kitodo.dataformat.metskitodo.Mets;

/**
 * The tree-like outline structure for digital representation. This structuring
 * structure can be subdivided into arbitrary finely granular.
 */
public class DivXmlElementAccess extends LogicalDivision {
    /**
     * The qualified name of the Kitodo metadata format, needed to assemble the
     * metadata entries in METS using JAXB.
     */
    private static final QName KITODO_QNAME = new QName("http://meta.kitodo.org/v1/", "kitodo");

    /**
     * Some magic numbers that are used in the METS XML file representation of
     * this structure to describe relations between XML elements. They need to
     * be stored because some scatty third-party scripts rely on them not being
     * changed anymore once assigned.
     */
    private final String metsReferrerId;

    /**
     * Creates a new DivXmlElementAccess.
     */
    public DivXmlElementAccess() {
        super();
        metsReferrerId = KitodoUUID.randomUUID();
    }

    /**
     * Creates a new DivXmlElementAccess for an existing structure.
     */
    DivXmlElementAccess(LogicalDivision logicalDivision) {
        super(logicalDivision);
        String obtainedReferrerId = null;
        if (logicalDivision instanceof DivXmlElementAccess) {
            obtainedReferrerId = ((DivXmlElementAccess) logicalDivision).metsReferrerId;
        }
        if (Objects.isNull(obtainedReferrerId)) {
            obtainedReferrerId = KitodoUUID.randomUUID();
        }
        metsReferrerId = obtainedReferrerId;
    }

    /**
     * Constructor to read a structure from METS.
     *
     * @param div
     *            METS {@code <div>} element from which the structure is to be
     *            built
     * @param mets
     *            METS data structure from which it is possible to determine
     *            what kind of metadata section is linked
     * @param physicalDivisionsMap
     *            From this map, the physical divisions are read, which must be
     *            referenced here by their ID.
     * @param parentOrder
     *            This represents the value of the parent's {@code ORDER} attribute. It is not required for logical elements by the
     *            mets standard but is used in Kitodo internal data format. It helps to display logical and physical elements in an
     *            advanced combined tree.
     */
    DivXmlElementAccess(DivType div, Mets mets, Map<String, List<FileXmlElementAccess>> physicalDivisionsMap, int parentOrder) {
        super();
        div.getCONTENTIDS().parallelStream().map(URI::create).forEachOrdered(super.getContentIds()::add);
        super.setLabel(div.getLABEL());
        for (Object mdSecType : div.getDMDID()) {
            super.getMetadata().addAll(readMetadata((MdSecType) mdSecType, MdSec.DMD_SEC));
        }
        for (Object mdSecType : div.getADMID()) {
            super.getMetadata().addAll(readMetadata((MdSecType) mdSecType, amdSecTypeOf(mets, (MdSecType) mdSecType)));
        }
        metsReferrerId = div.getID();
        BigInteger order = div.getORDER();
        if (Objects.nonNull(order) && order.intValue() > 0) {
            setOrder(order.intValue());
        } else if (parentOrder > 0) {
            setOrder(parentOrder);
        } else {
            setOrder(1);
        }
        super.setOrderlabel(div.getORDERLABEL());
        for (DivType child : div.getDiv()) {
            getChildren().add(new DivXmlElementAccess(child, mets, physicalDivisionsMap, getOrder()));
        }
        super.setType(div.getTYPE());
        List<FileXmlElementAccess> fileXmlElementAccesses = physicalDivisionsMap.get(div.getID());
        if (Objects.nonNull(fileXmlElementAccesses)) {
            for (FileXmlElementAccess fileXmlElementAccess : fileXmlElementAccesses) {
                if (Objects.nonNull(fileXmlElementAccess)
                    && !fileXmlElementAccessIsLinkedToChildren(fileXmlElementAccess, div.getDiv(), physicalDivisionsMap)) {
                    super.getViews().add(new AreaXmlElementAccess(fileXmlElementAccess).getView());
                    fileXmlElementAccess.getPhysicalDivision().getLogicalDivisions().add(this);
                }
            }
        }
        super.setLink(MptrXmlElementAccess.getLinkFromDiv(div));
    }

    private boolean fileXmlElementAccessIsLinkedToChildren(FileXmlElementAccess fileXmlElementAccess,
                                                           List<DivType> divs,
                                                           Map<String, List<FileXmlElementAccess>> physicalDivisionsMap) {
        if (divs.isEmpty()) {
            return false;
        }
        boolean test = false;
        for (DivType div : divs) {
            List<FileXmlElementAccess> fileXmlElementAccesses = physicalDivisionsMap.get(div.getID());
            if (Objects.nonNull(fileXmlElementAccesses) && fileXmlElementAccesses.contains(fileXmlElementAccess)) {
                return true;
            }
            if (div.getDiv().size() > 0
                    && fileXmlElementAccessIsLinkedToChildren(fileXmlElementAccess, div.getDiv(), physicalDivisionsMap)) {
                test = true;
            }
        }
        return test;
    }

    /**
     * Determines from a METS data structure of which type is a metadata
     * section.
     *
     * <p>
     * Implementation note: This method would be a good candidate for
     * parallelization.
     *
     * @param mets
     *            METS data structure that determines what type of metadata
     *            section is
     * @param mdSec
     *            administrative metadata section whose type is to be
     *            determined
     * @return the type of administrative metadata section
     */
    static final MdSec amdSecTypeOf(Mets mets, MdSecType mdSec) {
        for (AmdSecType amdSec : mets.getAmdSec()) {
            if (amdSec.getSourceMD().contains(mdSec)) {
                return MdSec.SOURCE_MD;
            } else if (amdSec.getDigiprovMD().contains(mdSec)) {
                return MdSec.DIGIPROV_MD;
            } else if (amdSec.getRightsMD().contains(mdSec)) {
                return MdSec.RIGHTS_MD;
            } else if (amdSec.getTechMD().contains(mdSec)) {
                return MdSec.TECH_MD;
            }
        }
        throw new NoSuchElementException();
    }

    /**
     * Reads a metadata section and adds the metadata to the structure.
     *
     * @param mdSecType
     *            type of metadata section
     * @param mdSec
     *            metadata section to be read
     *
     * @return a collection of type Metadata
     */
    static final Collection<Metadata> readMetadata(MdSecType mdSecType, MdSec mdSec) {
        Collection<Metadata> metadata = new HashSet<>();
        if (Objects.nonNull(mdSecType) && Objects.nonNull(mdSecType.getMdWrap())) {
            for (Object object : mdSecType.getMdWrap().getXmlData().getAny()) {
                if (object instanceof JAXBElement) {
                    JAXBElement<?> jaxbElement = (JAXBElement<?>) object;
                    Object value = jaxbElement.getValue();
                    if (value instanceof KitodoType) {
                        KitodoType kitodoType = (KitodoType) value;
                        for (MetadataType metadataEntry : kitodoType.getMetadata()) {
                            if (!metadataEntry.getValue().isEmpty()) {
                                metadata.add(new MetadataXmlElementAccess(mdSec, metadataEntry).getMetadataEntry());
                            }
                        }
                        for (MetadataGroupType metadataGroup : kitodoType.getMetadataGroup()) {
                            metadata.add(new MetadataGroupXmlElementAccess(mdSec, metadataGroup).getMetadataGroup());
                        }
                    }
                }
            }
        }
        return metadata;
    }

    /**
     * Creates a METS {@code <div>} element from this structure.
     *
     * @param physicalDivisionIDs
     *            the assigned identifier for each physical division so that the link
     *            pairs of the struct link section can be formed later
     * @param smLinkData
     *            the link pairs of the struct link section are added to this
     *            list
     * @param mets
     *            the METS structure in which the metadata is added
     * @return a METS {@code <div>} element
     */
    DivType toDiv(Map<PhysicalDivision, String> physicalDivisionIDs, LinkedList<Pair<String, String>> smLinkData, Mets mets) {
        DivType div = new DivType();
        div.setID(metsReferrerId);
        if (!super.getContentIds().isEmpty()) {
            super.getContentIds().parallelStream().map(URI::toString).forEachOrdered(div.getCONTENTIDS()::add);
        }
        div.setLABEL(super.getLabel());
        if (getOrder() > 0) {
            div.setORDER(BigInteger.valueOf(getOrder()));
        }
        div.setORDERLABEL(super.getOrderlabel());
        div.setTYPE(super.getType());
        smLinkData.addAll(super.getViews().stream().map(View::getPhysicalDivision)
            .sorted(Comparator.comparing(PhysicalDivision::getOrder)).map(physicalDivisionIDs::get)
            .map(physicalDivisionId -> Pair.of(metsReferrerId, physicalDivisionId)).collect(Collectors.toList()));

        Optional<MdSecType> optionalDmdSec = createMdSec(super.getMetadata(), MdSec.DMD_SEC);
        if (optionalDmdSec.isPresent()) {
            MdSecType dmdSec = optionalDmdSec.get();
            String name = metsReferrerId + ':' + MdSec.DMD_SEC;
            dmdSec.setID(KitodoUUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
            mets.getDmdSec().add(dmdSec);
            div.getDMDID().add(dmdSec);
        }
        Optional<AmdSecType> optionalAmdSec = createAmdSec(super.getMetadata(), metsReferrerId, div);
        if (optionalAmdSec.isPresent()) {
            AmdSecType admSec = optionalAmdSec.get();
            mets.getAmdSec().add(admSec);
        }
        if (Objects.nonNull(super.getLink())) {
            MptrXmlElementAccess.addMptrToDiv(super.getLink(), div);
        }
        for (LogicalDivision subLogicalDivision : super.getChildren()) {
            div.getDiv().add(new DivXmlElementAccess(subLogicalDivision).toDiv(physicalDivisionIDs, smLinkData, mets));
        }
        return div;
    }

    /**
     * Creates a metadata section of the specified domain of the Kitodo type
     * and returns it with its connection to the METS if there is data for it.
     *
     * @param domain
     *            Domain for which a metadata section is to be generated
     * @return a metadata section, if there is data for it
     */
    static Optional<MdSecType> createMdSec(Iterable<Metadata> metadata, MdSec domain) {
        if (StreamSupport.stream(metadata.spliterator(), false)
                .noneMatch(piece -> Objects.equals(piece.getDomain(), domain))) {
            return Optional.empty();
        }

        KitodoType kitodoType = new KitodoType();
        for (Metadata entry : metadata) {
            if (domain.equals(entry.getDomain())) {
                if (entry instanceof MetadataEntry) {
                    kitodoType.getMetadata().add(new MetadataXmlElementAccess((MetadataEntry) entry).toMetadata());
                } else if (entry instanceof MetadataGroup) {
                    kitodoType.getMetadataGroup()
                            .add(new MetadataGroupXmlElementAccess((MetadataGroup) entry).toXMLMetadataGroup());
                }
            }
        }
        XmlData xmlData = new XmlData();
        xmlData.getAny().add(new JAXBElement<>(KITODO_QNAME, KitodoType.class, kitodoType));
        MdWrap mdWrap = new MdWrap();
        mdWrap.setXmlData(xmlData);
        MdSecType dmdSec = new MdSecType();
        dmdSec.setMdWrap(mdWrap);
        return Optional.of(dmdSec);
    }

    /**
     * Generates an {@code <amdSec>} if administrative metadata exists on this
     * structure.
     *
     * @param div
     *            div where ADMID references must be added to the generated
     *            metadata sections
     * @return an {@code <amdSec>}, if necessary
     */
    static Optional<AmdSecType> createAmdSec(Iterable<Metadata> metadata, String metsReferrerId, DivType div) {
        AmdSecType amdSec = new AmdSecType();
        boolean source = addMdSec(createMdSec(metadata, MdSec.SOURCE_MD), metsReferrerId, MdSec.SOURCE_MD,
            AmdSecType::getSourceMD, amdSec, div);
        boolean digiprov = addMdSec(createMdSec(metadata, MdSec.DIGIPROV_MD), metsReferrerId, MdSec.DIGIPROV_MD,
            AmdSecType::getDigiprovMD, amdSec, div);
        boolean rights = addMdSec(createMdSec(metadata, MdSec.RIGHTS_MD), metsReferrerId, MdSec.RIGHTS_MD,
            AmdSecType::getRightsMD, amdSec, div);
        boolean tech = addMdSec(createMdSec(metadata, MdSec.TECH_MD), metsReferrerId, MdSec.TECH_MD,
            AmdSecType::getTechMD, amdSec, div);
        return source || digiprov || rights || tech ? Optional.of(amdSec) : Optional.empty();
    }

    /**
     * Adds a metadata section to an administrative metadata section, if there
     * is one. This function deduplicates fourfold existing function for four
     * different metadata sections.
     *
     * @param optionalMdSec
     *            perhaps existing metadata section to be added if it exists
     * @param mdSecType
     *            the type of the mdSec, used in ID generation
     * @param mdSecTypeGetter
     *            the getter via which the metadata section can be added to the
     *            administrative metadata section
     * @param amdSec
     *            administrative metadata section to which the metadata
     *            section should be added, if any
     * @param div
     *            div where ADMID references must be added to the generated
     *            metadata sections
     * @return whether something has been added to the administrative metadata
     *         section
     */
    private static boolean addMdSec(Optional<MdSecType> optionalMdSec, String metsReferrerId, MdSec mdSecType,
            Function<AmdSecType, List<MdSecType>> mdSecTypeGetter, AmdSecType amdSec, DivType div) {

        if (!optionalMdSec.isPresent()) {
            return false;
        } else {
            MdSecType mdSec = optionalMdSec.get();
            String name = metsReferrerId + ':' + mdSecType.toString();
            mdSec.setID(KitodoUUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
            mdSecTypeGetter.apply(amdSec).add(mdSec);
            div.getADMID().add(mdSec);
            return true;
        }
    }
}
