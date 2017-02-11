package eu.drus.jpa.unit;

import static eu.drus.jpa.unit.util.Preconditions.checkArgument;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceProperty;
import javax.persistence.PersistenceUnit;

import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.drus.jpa.unit.core.PersistenceUnitDescriptor;
import eu.drus.jpa.unit.core.PersistenceUnitDescriptorLoader;
import eu.drus.jpa.unit.core.metadata.AnnotationInspector;
import eu.drus.jpa.unit.core.metadata.FeatureResolver;
import eu.drus.jpa.unit.core.metadata.MetadataExtractor;
import eu.drus.jpa.unit.rule.ExecutionContext;

class JpaUnitContext implements ExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(JpaUnitContext.class);

    private static final Map<TestClass, JpaUnitContext> CTX_MAP = new HashMap<>();

    private Field persistenceField;
    private Map<String, Object> properties;
    private AtomicInteger count;
    private EntityManagerFactory emf;

    private JpaUnitContext(final TestClass testClass) {
        try {
            final MetadataExtractor extractor = new MetadataExtractor(testClass);
            final AnnotationInspector<PersistenceContext> pcInspector = extractor.persistenceContext();
            final AnnotationInspector<PersistenceUnit> puInspector = extractor.persistenceUnit();
            final List<Field> pcFields = pcInspector.getAnnotatedFields();
            final List<Field> puFields = puInspector.getAnnotatedFields();

            checkArgument(!puFields.isEmpty() || !pcFields.isEmpty(),
                    "JPA test must have either EntityManagerFactory or EntityManager field annotated with @PersistenceUnit, respectively @PersistenceContext");

            checkArgument(puFields.isEmpty() || pcFields.isEmpty(),
                    "Only single field annotated with either @PersistenceUnit or @PersistenceContext is allowed to be present");

            checkArgument(puFields.size() <= 1, "Only single field is allowed to be annotated with @PersistenceUnit");

            checkArgument(pcFields.size() <= 1, "Only single field is allowed to be annotated with @PersistenceContext");

            String unitName;

            if (!puFields.isEmpty()) {
                persistenceField = puFields.get(0);
                checkArgument(persistenceField.getType().equals(EntityManagerFactory.class), String.format(
                        "Field %s annotated with @PersistenceUnit is not of type EntityManagerFactory.", persistenceField.getName()));
                final PersistenceUnit persistenceUnit = puInspector.fetchFromField(persistenceField);
                unitName = persistenceUnit.unitName();
                properties = Collections.emptyMap();
            } else {
                persistenceField = pcFields.get(0);
                checkArgument(persistenceField.getType().equals(EntityManager.class), String
                        .format("Field %s annotated with @PersistenceContext is not of type EntityManager.", persistenceField.getName()));
                final PersistenceContext persistenceContext = pcInspector.fetchFromField(persistenceField);
                unitName = persistenceContext.unitName();
                properties = getPersistenceContextProperties(persistenceContext);
            }

            final List<FrameworkMethod> testMethods = testClass.getAnnotatedMethods(Test.class);

            // The * 2 is because SecondLevelCache rule/statement use create/destroy
            // EntityManagerFactory in addition to PersistenceContext rule/statement
            count = new AtomicInteger(testMethods.size() * 2);
            emf = Persistence.createEntityManagerFactory(unitName, properties);

            final PersistenceUnitDescriptorLoader pudLoader = new PersistenceUnitDescriptorLoader();
            List<PersistenceUnitDescriptor> descriptors = pudLoader.loadPersistenceUnitDescriptors(properties);

            descriptors = descriptors.stream().filter(u -> unitName.equals(u.getUnitName())).collect(Collectors.toList());

            if (descriptors.isEmpty()) {
                throw new JpaUnitException("No Persistence Unit found for given unit name");
            } else if (descriptors.size() > 1) {
                throw new JpaUnitException("Multiple Persistence Units found for given name");
            }

            properties = descriptors.get(0).getProperties();
        } catch (final IOException e) {
            throw new JpaUnitException("Error while loading Persistence Unit descriptors", e);
        }
    }

    static synchronized JpaUnitContext getInstance(final TestClass testClass) {
        JpaUnitContext ctx = CTX_MAP.get(testClass);
        if (ctx == null) {
            ctx = new JpaUnitContext(testClass);
            CTX_MAP.put(testClass, ctx);
        }
        return ctx;
    }

    private static Map<String, Object> getPersistenceContextProperties(final PersistenceContext persistenceContext) {
        final Map<String, Object> properties = new HashMap<>();
        for (final PersistenceProperty property : persistenceContext.properties()) {
            properties.put(property.name(), property.value());
        }
        return properties;
    }

    @Override
    public Field getPersistenceField() {
        return persistenceField;
    }

    @Override
    public Map<String, Object> getDataBaseConnectionProperties() {
        return properties;
    }

    @Override
    public EntityManagerFactory createEntityManagerFactory() {
        return emf;
    }

    @Override
    public void destroyEntityManagerFactory(final EntityManagerFactory emf) {
        if (count.decrementAndGet() == 0) {
            try {
                emf.close();
            } catch (final Exception e) {
                LOG.error("Enexpected error while closing the EntityManagerFactory", e);
            }
        }
    }

    @Override
    public FeatureResolver createFeatureResolver(final Method testMethod, final Class<?> clazz) {
        return new FeatureResolver(testMethod, clazz);
    }
}
