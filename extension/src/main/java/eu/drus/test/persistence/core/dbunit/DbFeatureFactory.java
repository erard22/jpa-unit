package eu.drus.test.persistence.core.dbunit;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.IColumnFilter;
import org.dbunit.operation.DatabaseOperation;

import eu.drus.test.persistence.JpaUnitException;
import eu.drus.test.persistence.annotation.CleanupStrategy;
import eu.drus.test.persistence.annotation.DataSeedStrategy;
import eu.drus.test.persistence.annotation.ExpectedDataSets;
import eu.drus.test.persistence.core.AssertionErrorCollector;
import eu.drus.test.persistence.core.dbunit.dataset.DataSetLoader;
import eu.drus.test.persistence.core.dbunit.dataset.DataSetLoaderProvider;
import eu.drus.test.persistence.core.metadata.FeatureResolver;
import eu.drus.test.persistence.core.sql.SqlScript;

public class DbFeatureFactory {

    private final FeatureResolver featureResolver;
    private List<IDataSet> initialDataSets;
    private StrategyProviderFactory providerFactory;

    public DbFeatureFactory(final FeatureResolver featureResolver) {
        this.featureResolver = featureResolver;
        providerFactory = new StrategyProviderFactory();
    }

    private static IDataSet mergeDataSets(final List<IDataSet> dataSets) throws DataSetException {
        return new CompositeDataSet(dataSets.toArray(new IDataSet[dataSets.size()]));
    }

    private static List<IDataSet> loadDataSets(final List<String> paths) throws IOException {
        final List<IDataSet> dataSets = new ArrayList<>();
        for (final String path : paths) {
            final DataSetLoader loader = DataSetFormat.inferFromFile(path).select(new DataSetLoaderProvider());
            dataSets.add(loader.load(path));
        }
        return dataSets;
    }

    // for tests
    void setProviderFactory(final StrategyProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    private List<IDataSet> getInitialDataSets() {
        if (initialDataSets == null) {
            try {
                initialDataSets = loadDataSets(featureResolver.getSeedData());
            } catch (final IOException e) {
                throw new JpaUnitException("Could not load initial data sets", e);
            }
        }
        return initialDataSets;
    }

    public DbFeature getCleanUpBeforeFeature() {
        if (featureResolver.shouldCleanupBefore()) {
            return new CleanupFeature(providerFactory.createCleanupStrategyProvider(), featureResolver.getCleanupStrategy(),
                    getInitialDataSets());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getCleanUpAfterFeature() {
        if (featureResolver.shouldCleanupAfter()) {
            return new CleanupFeature(providerFactory.createCleanupStrategyProvider(), featureResolver.getCleanupStrategy(),
                    getInitialDataSets());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getCleanupUsingScriptBeforeFeature() {
        if (featureResolver.shouldCleanupUsingScriptBefore()) {
            return new ApplyCustomScriptFeature(featureResolver.getCleanupScripts());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getCleanupUsingScriptAfterFeature() {
        if (featureResolver.shouldCleanupUsingScriptAfter()) {
            return new ApplyCustomScriptFeature(featureResolver.getCleanupScripts());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getApplyCustomScriptBeforeFeature() {
        if (featureResolver.shouldApplyCustomScriptBefore()) {
            return new ApplyCustomScriptFeature(featureResolver.getPreExecutionScripts());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getApplyCustomScriptAfterFeature() {
        if (featureResolver.shouldApplyCustomScriptAfter()) {
            return new ApplyCustomScriptFeature(featureResolver.getPostExecutionScripts());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getSeedDataFeature() {
        if (featureResolver.shouldSeedData()) {
            return new SeedDataFeature(providerFactory.createDataSeedStrategyProvider(), featureResolver.getDataSeedStrategy(),
                    getInitialDataSets());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    public DbFeature getVerifyDataAfterFeature() {
        if (featureResolver.shouldVerifyDataAfter()) {
            return new VerifyDataAfterFeature(featureResolver.getExpectedDataSets(), featureResolver.getCustomColumnFilter());
        } else {
            return NopFeature.INSTANCE;
        }
    }

    private static class NopFeature implements DbFeature {

        private static final NopFeature INSTANCE = new NopFeature();

        @Override
        public void execute(final IDatabaseConnection connection) throws DbFeatureException {
            // does nothing like the name implies
        }
    }

    private static class CleanupFeature implements DbFeature {

        private final CleanupStrategy cleanupStrategy;
        private final List<IDataSet> initialDataSets;
        private final CleanupStrategyProvider provider;

        private CleanupFeature(final CleanupStrategyProvider provider, final CleanupStrategy cleanupStrategy,
                final List<IDataSet> initialDataSets) {
            this.provider = provider;
            this.cleanupStrategy = cleanupStrategy;
            this.initialDataSets = initialDataSets;
        }

        @Override
        public void execute(final IDatabaseConnection connection) throws DbFeatureException {
            final CleanupStrategyExecutor executor = cleanupStrategy.provide(provider);
            executor.execute(connection, initialDataSets);
        }
    }

    private static class ApplyCustomScriptFeature implements DbFeature {

        private final List<String> scriptPaths;

        private ApplyCustomScriptFeature(final List<String> scriptPaths) {
            this.scriptPaths = scriptPaths;
        }

        @Override
        public void execute(final IDatabaseConnection connection) throws DbFeatureException {
            try {
                for (final String script : scriptPaths) {
                    executeScript(script, connection.getConnection());
                }
            } catch (final SQLException e) {
                throw new DbFeatureException("Could not apply custom scripts feature", e);
            }
        }

        private void executeScript(final String script, final Connection connection) throws SQLException {
            for (final String sqlStatement : new SqlScript(script)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sqlStatement);
                }
            }
        }
    }

    private static class SeedDataFeature implements DbFeature {

        private final DataSeedStrategy dataSeedStrategy;
        private final List<IDataSet> initialDataSets;
        private final DataSeedStrategyProvider provider;

        public SeedDataFeature(final DataSeedStrategyProvider provider, final DataSeedStrategy dataSeedStrategy,
                final List<IDataSet> initialDataSets) {
            this.dataSeedStrategy = dataSeedStrategy;
            this.initialDataSets = initialDataSets;
            this.provider = provider;
        }

        @Override
        public void execute(final IDatabaseConnection connection) throws DbFeatureException {
            try {
                final DatabaseOperation operation = dataSeedStrategy.provide(provider);
                operation.execute(connection, mergeDataSets(initialDataSets));
            } catch (DatabaseUnitException | SQLException e) {
                throw new DbFeatureException("Could not execute DB seed feature", e);
            }
        }
    }

    private static class VerifyDataAfterFeature implements DbFeature {

        private ExpectedDataSets expectedDataSets;
        private Set<Class<? extends IColumnFilter>> customColumnFilter;

        public VerifyDataAfterFeature(final ExpectedDataSets expectedDataSets,
                final Set<Class<? extends IColumnFilter>> customColumnFilter) {
            this.expectedDataSets = expectedDataSets;
            this.customColumnFilter = customColumnFilter;
        }

        @Override
        public void execute(final IDatabaseConnection connection) throws DbFeatureException {
            try {
                final IDataSet currentDataSet = connection.createDataSet();
                final IDataSet expectedDataSet = mergeDataSets(loadDataSets(Arrays.asList(expectedDataSets.value())));

                final DataSetComparator dataSetComparator = new DataSetComparator(expectedDataSets.orderBy(),
                        expectedDataSets.excludeColumns(), expectedDataSets.strict(), customColumnFilter);

                final AssertionErrorCollector errorCollector = new AssertionErrorCollector();
                dataSetComparator.compare(currentDataSet, expectedDataSet, errorCollector);

                errorCollector.report();
            } catch (final SQLException | DatabaseUnitException | IOException e) {
                throw new DbFeatureException("Could not execute DB contents verification feature", e);
            }
        }
    }
}