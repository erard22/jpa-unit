package eu.drus.test.persistence.rule.context;

import java.lang.reflect.Field;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.runners.model.Statement;

public class PersistenceContextStatement extends Statement {

    private final EntityManagerFactory entityManagerFactory;
    private final Field persistenceField;
    private final Statement base;
    private final Object target;

    public PersistenceContextStatement(final EntityManagerFactory entityManagerFactory, final Field persistenceField, final Statement base,
            final Object target) {
        this.entityManagerFactory = entityManagerFactory;
        this.persistenceField = persistenceField;
        this.base = base;
        this.target = target;
    }

    @Override
    public void evaluate() throws Throwable {
        EntityManager entityManager = null;

        final Class<?> persistenceContextFieldType = persistenceField.getType();
        final boolean isAccessible = persistenceField.isAccessible();
        persistenceField.setAccessible(true);
        try {
            if (persistenceContextFieldType.equals(EntityManagerFactory.class)) {
                // just inject the factory
                persistenceField.set(target, entityManagerFactory);
            } else if (persistenceContextFieldType.equals(EntityManager.class)) {
                // create EntityManager and inject it
                entityManager = entityManagerFactory.createEntityManager();
                persistenceField.set(target, entityManager);
            } else {
                throw new IllegalArgumentException("Unexpected field type: " + persistenceContextFieldType.getName());
            }
        } finally {
            persistenceField.setAccessible(isAccessible);
        }

        try {
            base.evaluate();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

}
