package eu.drus.test.persistence.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import eu.drus.test.persistence.JpaTestException;

public class PersistenceUnitDescriptorLoader {

    private DocumentBuilderFactory documentBuilderFactory;

    public List<PersistenceUnitDescriptor> loadPersistenceUnitDescriptors(final Map<String, Object> properties) throws IOException {
        final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources("META-INF/persistence.xml");

        final List<PersistenceUnitDescriptor> units = new ArrayList<>();
        while (resources.hasMoreElements()) {
            final Document doc = loadDocument(resources.nextElement());
            final Element top = doc.getDocumentElement();

            final NodeList children = top.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    final Element element = (Element) children.item(i);
                    final String tag = element.getTagName();
                    if ("persistence-unit".equals(tag)) {
                        units.add(new PersistenceUnitDescriptor(element, properties));
                    }
                }
            }
        }

        return units;

    }

    private Document loadDocument(final URL url) throws IOException {
        final URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        try (InputStream in = conn.getInputStream()) {
            return getDocumentBuilderFactory().newDocumentBuilder().parse(new InputSource(in));
        } catch (final SAXException | ParserConfigurationException e) {
            throw new JpaTestException("Error parsing [" + url.toExternalForm() + "]", e);
        }
    }

    private DocumentBuilderFactory getDocumentBuilderFactory() {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
        }
        return documentBuilderFactory;
    }
}
