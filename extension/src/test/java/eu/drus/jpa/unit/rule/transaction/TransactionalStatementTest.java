package eu.drus.jpa.unit.rule.transaction;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import eu.drus.jpa.unit.annotation.TransactionMode;
import eu.drus.jpa.unit.core.metadata.FeatureResolver;
import eu.drus.jpa.unit.rule.ExecutionContext;

@RunWith(MockitoJUnitRunner.class)
public class TransactionalStatementTest {

    @Mock
    private Statement base;

    @Mock
    private FrameworkMethod method;

    @Mock
    private EntityManagerFactory emf;

    @Mock
    private EntityManager em;

    @Mock
    private ExecutionContext ctx;

    @Mock
    private FeatureResolver resolver;

    private Field emfField;

    private Field emField;

    @Before
    public void setUp() throws Exception {
        emfField = getClass().getDeclaredField("emf");
        emField = getClass().getDeclaredField("em");

        when(ctx.createFeatureResolver(any(Method.class), any(Class.class))).thenReturn(resolver);
        when(resolver.getTransactionMode()).thenReturn(TransactionMode.DISABLED);
    }

    @Test
    public void testNoTransactionStrategyIsExecutedForEntityManagerFactory() throws Throwable {
        // GIVEN
        when(ctx.getPersistenceField()).thenReturn(emfField);
        final TransactionalStatement stmt = new TransactionalStatement(ctx, base, method, this);

        // WHEN
        stmt.evaluate();

        // THEN
        verify(base).evaluate();
        verify(resolver, times(0)).getTransactionMode();
        verify(em, times(0)).clear();
    }

    @Test
    public void testTransactionStrategyIsExecutedForEntityManager() throws Throwable {
        // GIVEN
        when(ctx.getPersistenceField()).thenReturn(emField);
        final TransactionalStatement stmt = new TransactionalStatement(ctx, base, method, this);

        // WHEN
        stmt.evaluate();

        // THEN
        verify(base).evaluate();
        verify(resolver).getTransactionMode();
        verify(em).clear();
    }
}
