package me.cxj.j3dtiles.ifc.deserializer;

import me.cxj.j3dtiles.ifc.model.BasicIfcModel;
import me.cxj.j3dtiles.ifc.model.IfcModel;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.IfcModelInterfaceException;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.models.store.IfcHeader;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.plugins.deserializers.ByteProgressReporter;
import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.utils.FakeClosingInputStream;
import org.eclipse.emf.ecore.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by vipcxj on 2018/11/16.
 */
public class IfcXmlDeserializer {

    private IfcModel model;

    public void init(PackageMetaData packageMetaData) {
        this.model = new BasicIfcModel(packageMetaData, null);
    }

    public IfcModelInterface read(InputStream inputStream, String filename, long fileSize, ByteProgressReporter byteProgressReporter) throws DeserializeException {
        if (filename != null && (filename.toUpperCase().endsWith(".ZIP") || filename.toUpperCase().endsWith(".IFCZIP") || filename.toUpperCase().endsWith(".IFCXMLZIP"))) {
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry nextEntry;
            try {
                nextEntry = zipInputStream.getNextEntry();
                if (nextEntry == null) {
                    throw new DeserializeException("Zip files must contain exactly one IFC-file, this zip-file looks empty");
                }
                if (nextEntry.getName().toUpperCase().endsWith(".IFCXML")) {
                    IfcModelInterface model;
                    FakeClosingInputStream fakeClosingInputStream = new FakeClosingInputStream(zipInputStream);
                    model = read(fakeClosingInputStream);
                    if (model.size() == 0) {
                        throw new DeserializeException("Uploaded file does not seem to be a correct IFC file");
                    }
                    if (zipInputStream.getNextEntry() != null) {
                        zipInputStream.close();
                        throw new DeserializeException("Zip files may only contain one IFC-file, this zip-file contains more files");
                    } else {
                        zipInputStream.close();
                        return model;
                    }
                } else {
                    throw new DeserializeException("Zip files must contain exactly one IFC-file, this zip-file seems to have one or more non-IFC files");
                }
            } catch (IOException e) {
                throw new DeserializeException(e);
            }
        } else {
            return read(inputStream);
        }
    }

    private IfcModel read(InputStream inputStream) throws DeserializeException {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream, "UTF-8");
            parseDocument(reader);
            return model;
        } catch (XMLStreamException e) {
            throw new DeserializeException(e);
        }
    }

    private void parseDocument(XMLStreamReader reader) throws DeserializeException {
        try {
            while (reader.hasNext()) {
                reader.next();
                if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    if (reader.getLocalName().equalsIgnoreCase("iso_10303_28")) {
                        parseIso_10303_28(reader);
                    } else if (reader.getLocalName().equalsIgnoreCase("ifcxml")) {
                        parseIfc4(reader);
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new DeserializeException(e);
        }
    }

    private void parseIfc4(XMLStreamReader reader) throws DeserializeException {
        try {
            while (reader.hasNext()) {
                reader.next();
                if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    if (reader.getLocalName().equalsIgnoreCase("header")) {
                        parseHeader(reader);
                    } else {
                        parseObject(reader);
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new DeserializeException(e);
        }
    }

    /**
     * <name>Lise-Meitner-Str.1, 10589 Berlin</name>
     <time_stamp>2015-10-05T10:20:47.3587387+02:00</time_stamp>
     <author>Lellonek</author>
     <organization>eTASK Service-Management GmbH</organization>
     <preprocessor_version>.NET API etask.ifc</preprocessor_version>
     <originating_system>.NET API etask.ifc</originating_system>
     <authorization>file created with .NET API etask.ifc</authorization>
     <documentation>ViewDefinition [notYetAssigned]</documentation>
     */
    @SuppressWarnings("StatementWithEmptyBody")
    private void parseHeader(XMLStreamReader reader) throws XMLStreamException {
        IfcHeader ifcHeader = model.getModelMetaData().getIfcHeader();
        if (ifcHeader == null) {
            ifcHeader = StoreFactory.eINSTANCE.createIfcHeader();
            model.getModelMetaData().setIfcHeader(ifcHeader);
        }

        while (reader.hasNext()) {
            reader.next();
            if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                String localname = reader.getLocalName();
                if (localname.equalsIgnoreCase("name")) {
                    reader.next();
                    ifcHeader.setFilename(reader.getText());
                } else if (localname.equals("time_stamp")) {
                    reader.next();
                    try {
                        ifcHeader.setTimeStamp(DatatypeFactory
                                .newInstance()
                                .newXMLGregorianCalendar(reader.getText()).toGregorianCalendar().getTime());
                    } catch (DatatypeConfigurationException e1) {
                        e1.printStackTrace();
                    }
                } else if (localname.equals("author")) {
                    reader.next();
                    ifcHeader.getAuthor().add(reader.getText());
                } else if (localname.equals("organization")) {
                    reader.next();
                    ifcHeader.getOrganization().add(reader.getText());
                } else if (localname.equals("preprocessor_version")) {
                    reader.next();
                    ifcHeader.setPreProcessorVersion(reader.getText());
                } else if (localname.equals("originating_system")) {
                    reader.next();
                    ifcHeader.setOriginatingSystem(reader.getText());
                } else if (localname.equals("authorization")) {
                    reader.next();
                    ifcHeader.setAuthorization(reader.getText());
                } else if (localname.equals("documentation")) {
                    // ignore
                }
            } else if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                if (reader.getLocalName().equals("header")) {
                    return;
                }
            }
        }
    }

    private void parseIso_10303_28(XMLStreamReader reader) throws DeserializeException {
        try {
            while (reader.hasNext()) {
                reader.next();
                if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    if (reader.getLocalName().equalsIgnoreCase("uos")) {
                        parseUos(reader);
                    }
                } else if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                    if (reader.getLocalName().equals("iso_10303_28")) {
                        return;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new DeserializeException(e);
        }
    }

    private void parseUos(XMLStreamReader reader) throws DeserializeException {
        try {
            while (reader.hasNext()) {
                reader.next();
                if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    parseObject(reader);
                } else if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                    if (reader.getLocalName().equalsIgnoreCase("uos")) {
                        return;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new DeserializeException(e);
        }
    }

    private IdEObject parseObject(XMLStreamReader reader) throws DeserializeException {
        String className = reader.getLocalName();
        EClassifier eClassifier = model.getPackageMetaData().getEClassifier(className);
        if (eClassifier == null || !(eClassifier instanceof EClass)) {
            throw new DeserializeException("No class with name " + className + " was found");
        }
        String id = reader.getAttributeValue("", "id");
        if (id == null) {
            throw new DeserializeException("No id attribute found on " + className);
        }
        if (!id.startsWith("i")) {
            throw new DeserializeException("Id " + id + " is not starting with the letter 'i'");
        }
        EClass eClass = (EClass) eClassifier;
        long oid = Long.parseLong(id.substring(1));
        IdEObject object;
        if (model.contains(oid)) {
            object = model.get(oid);
        } else {
            object = model.getPackageMetaData().create(eClass);
            try {
                model.add(oid, object);
            } catch (IfcModelInterfaceException e) {
                throw new DeserializeException(e);
            }
        }
        try {
            while (reader.hasNext()) {
                reader.next();
                if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    parseField(object, reader);
                } else if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                    if (reader.getLocalName().equalsIgnoreCase(className)) {
                        return object;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new DeserializeException(e);
        }
        return object;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "StatementWithEmptyBody"})
    private void parseField(IdEObject object, XMLStreamReader reader) throws DeserializeException {
        String fieldName = reader.getLocalName();
        EStructuralFeature eStructuralFeature = object.eClass().getEStructuralFeature(fieldName);
        if (eStructuralFeature == null) {
            throw new DeserializeException("Field " + fieldName + " not found on class " + object.eClass().getName());
        }
        EClassifier realType = null;
        try {
            while (reader.hasNext()) {
                reader.next();
                if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    if (reader.getAttributeValue("", "id") != null) {
                        IdEObject reference = parseObject(reader);
                        if (eStructuralFeature.isMany()) {
                            ((List) object.eGet(eStructuralFeature)).add(reference);
                        } else {
                            object.eSet(eStructuralFeature, reference);
                        }
                    } else if (reader.getAttributeValue("", "ref") != null) {
                        String ref = reader.getAttributeValue("", "ref");
                        if (!ref.startsWith("i")) {
                            throw new DeserializeException("Reference id " + ref + " should start with an 'i'");
                        }
                        Long refId = Long.parseLong(ref.substring(1));
                        IdEObject reference;
                        if (!model.contains(refId)) {
                            String referenceType = reader.getLocalName();
                            reference = model.getPackageMetaData().create((EClass) model.getPackageMetaData().getEClassifier(referenceType));
                            try {
                                model.add(refId, reference);
                            } catch (IfcModelInterfaceException e) {
                                throw new DeserializeException(e);
                            }
                        } else {
                            reference = model.get(refId);
                        }
                        if (eStructuralFeature.isMany()) {
                            List list = (List) object.eGet(eStructuralFeature);
                            String posString = reader.getAttributeValue("urn:iso.org:standard:10303:part(28):version(2):xmlschema:common", "pos");
                            if (posString == null) {
                                list.add(reference);
                            } else {
                                int pos = Integer.parseInt(posString);
                                if (list.size() > pos) {
                                    list.set(pos, reference);
                                } else {
                                    for (int i = list.size() - 1; i < pos - 1; i++) {
                                        list.add(reference.eClass().getEPackage().getEFactoryInstance().create(reference.eClass()));
                                    }
                                    list.add(reference);
                                }
                            }
                        } else {
                            object.eSet(eStructuralFeature, reference);
                        }
                    } else {
//						// TODO
//						String embeddedFieldName = reader.getLocalName();
//						if (embeddedFieldName.equals("double-wrapper")) {
//							List<Double> list = (List<Double>) object.eGet(eStructuralFeature);
//							list.add(Double.valueOf(reader.getText()));
//						} else {
//							EClass eClass = model.getPackageMetaData().getEClass(embeddedFieldName);
//							if (eClass == null) {
//								throw new DeserializeException("No type found " + embeddedFieldName);
//							}
//							IdEObject embedded = (IdEObject) model.getPackageMetaData().create(eClass);
//							object.eSet(eStructuralFeature, embedded);
//						}
                    }
                } else if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                    if (reader.getLocalName().equalsIgnoreCase(fieldName)) {
                        return;
                    }
                    if (realType != null && reader.getLocalName().equalsIgnoreCase(realType.getName())) {
                        realType = null;
                    }
                } else if (reader.getEventType() == XMLStreamReader.CHARACTERS) {
                    if (!reader.isWhiteSpace()) {
                        String text = reader.getText();
                        if (eStructuralFeature.getEType() instanceof EDataType) {
                            if (eStructuralFeature.isMany()) {
                                String[] split = text.split(" ");
                                List list = (List) object.eGet(eStructuralFeature);
                                for (String s : split) {
                                    list.add(parsePrimitive(eStructuralFeature.getEType(), s));
                                }
                            } else {
                                object.eSet(eStructuralFeature, parsePrimitive(eStructuralFeature.getEType(), text));
                            }
                        } else {
                            if (realType == null) {
                                realType = eStructuralFeature.getEType();
                            }
                            if (realType instanceof EClass) {
                                EClass eClass = (EClass) realType;
                                if (eClass.getEAnnotation("wrapped") != null) {
                                    IdEObject wrappedObject = model.getPackageMetaData().create(eClass);
                                    // model.add(wrappedObject);
                                    EStructuralFeature wrappedValueFeature = eClass.getEStructuralFeature("wrappedValue");
                                    wrappedObject.eSet(wrappedValueFeature, parsePrimitive(wrappedValueFeature.getEType(), text));
                                    if (wrappedValueFeature.getEType() == EcorePackage.eINSTANCE.getEDouble()) {
                                        EStructuralFeature doubleStringFeature = eClass.getEStructuralFeature("wrappedValueAsString");
                                        wrappedObject.eSet(doubleStringFeature, text);
                                    }
                                    List list = (List) object.eGet(eStructuralFeature);
                                    if (eStructuralFeature.isMany()) {
                                        list.add(wrappedObject);
                                    } else {
                                        object.eSet(eStructuralFeature, wrappedObject);
                                    }
                                }
                            } else {
                                if (eStructuralFeature.isMany()) {
                                    String[] split = text.split(" ");
                                    List list = (List) object.eGet(eStructuralFeature);
                                    for (String s : split) {
                                        list.add(parsePrimitive(realType, s));
                                    }
                                } else {
                                    object.eSet(eStructuralFeature, parsePrimitive(realType, text));
                                }
                            }
                        }
                    }
                }
            }
        } catch (NumberFormatException | XMLStreamException e) {
            throw new DeserializeException(e);
        }
    }

    private Object parsePrimitive(EClassifier eType, String text) throws DeserializeException {
        if (eType == EcorePackage.eINSTANCE.getEString()) {
            return text;
        } else if (eType == EcorePackage.eINSTANCE.getEInt()) {
            return Integer.parseInt(text);
        } else if (eType == EcorePackage.eINSTANCE.getELong()) {
            return Long.parseLong(text);
        } else if (eType == EcorePackage.eINSTANCE.getEDouble()) {
            return Double.parseDouble(text);
        } else if (eType == EcorePackage.eINSTANCE.getEBoolean()) {
            return Boolean.parseBoolean(text);
        } else if (eType instanceof EEnum) {
            EEnumLiteral eEnumLiteral = ((EEnum) eType).getEEnumLiteral(text.toUpperCase());
            if (eEnumLiteral == null) {
                if (text.equals("unknown")) {
                    return null;
                } else {
                    throw new DeserializeException("Unknown enum literal " + text + " in enum " + eType.getName());
                }
            }
            return eEnumLiteral.getInstance();
        } else {
            throw new DeserializeException("Unimplemented primitive type: " + eType.getName());
        }
    }
}
