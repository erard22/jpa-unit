package eu.drus.test.persistence.core;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PersistenceUnitDescriptor {

    private String unitName;
    private Map<String, Object> properties;
    private String providerClassName;

    public PersistenceUnitDescriptor(final Element element, final Map<String, Object> properties) {
        this.properties = properties;
        parse(element);
    }

    private void parse(final Element persistenceUnitElement) {
        final String name = persistenceUnitElement.getAttribute("name");
        if (StringUtils.isNotEmpty(name)) {
            unitName = name;
        }

        final NodeList children = persistenceUnitElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                final Element element = (Element) children.item(i);
                final String tag = element.getTagName();
                if (tag.equals("provider")) {
                    providerClassName = extractContent(element);
                } else if (tag.equals("properties")) {
                    final NodeList props = element.getChildNodes();
                    for (int j = 0; j < props.getLength(); j++) {
                        if (props.item(j).getNodeType() == Node.ELEMENT_NODE) {
                            final Element propElement = (Element) props.item(j);
                            if (!"property".equals(propElement.getTagName())) {
                                continue;
                            }
                            final String propName = propElement.getAttribute("name").trim();
                            final String propValue = propElement.getAttribute("value").trim();
                            if (!properties.containsKey(propName)) {
                                properties.put(propName, propValue);
                            }
                        }
                    }
                }
            }
        }
    }

    private static String extractContent(final Element element) {
        if (element == null) {
            return null;
        }

        final NodeList children = element.getChildNodes();
        final StringBuilder result = new StringBuilder("");
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                result.append(node.getNodeValue());
            }
        }
        return result.toString().trim();
    }

    public String getUnitName() {
        return unitName;
    }

    public String getProviderClassName() {
        return providerClassName;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
