/*
 * Copyright 2021-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.r2dbc.testing;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.awaitility.Awaitility;
import org.springframework.data.r2dbc.testing.ExternalDatabase.ProvidedDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.ClassUtils;
import org.testcontainers.oracle.OracleContainer;

/**
 * Utility class for testing against Oracle.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 */
public class OracleTestSupport {

	private static ExternalDatabase testContainerDatabase;

	public static String CREATE_TABLE_LEGOSET = "CREATE TABLE legoset (\n" //
			+ "    id          INTEGER PRIMARY KEY,\n" //
			+ "    version     INTEGER NULL,\n" //
			+ "    name        VARCHAR2(255) NOT NULL,\n" //
			+ "    manual      INTEGER NULL,\n" //
			+ "    cert        RAW(255) NULL\n" //
			+ ")";

	public static String CREATE_TABLE_LEGOSET_WITH_ID_GENERATION = "CREATE TABLE legoset (\n" //
			+ "    id          INTEGER GENERATED by default on null as IDENTITY PRIMARY KEY,\n" //
			+ "    version     INTEGER NULL,\n" //
			+ "    name        VARCHAR2(255) NOT NULL,\n" //
			+ "    flag        INTEGER NULL,\n" //
			+ "    manual      INTEGER NULL\n" //
			+ ")";

	public static final String CREATE_TABLE_LEGOSET_WITH_MIXED_CASE_NAMES = "CREATE TABLE \"LegoSet\" (\n" //
			+ "    \"Id\"          INTEGER GENERATED by default on null as IDENTITY PRIMARY KEY,\n" //
			+ "    \"Name\"        VARCHAR2(255) NOT NULL,\n" //
			+ "    \"Manual\"      INTEGER NULL\n" //
			+ ")";
	public static final String DROP_TABLE_LEGOSET_WITH_MIXED_CASE_NAMES = "DROP TABLE \"LegoSet\"";

	/**
	 * Returns a database either hosted locally or running inside Docker.
	 *
	 * @return information about the database. Guaranteed to be not {@literal null}.
	 */
	public static ExternalDatabase database() {

		// Disable Oracle support as there's no M1 support yet.
		if (ConnectionUtils.AARCH64.equals(System.getProperty("os.arch"))) {
			return ExternalDatabase.unavailable();
		}

		if (!ClassUtils.isPresent("oracle.r2dbc.impl.OracleConnectionFactoryProviderImpl",
				OracleTestSupport.class.getClassLoader())) {
			return ExternalDatabase.unavailable();
		}

		if (Boolean.getBoolean("spring.data.r2dbc.test.preferLocalDatabase")) {

			return getFirstWorkingDatabase( //
					OracleTestSupport::local, //
					OracleTestSupport::testContainer //
			);
		} else {

			return getFirstWorkingDatabase( //
					OracleTestSupport::testContainer, //
					OracleTestSupport::local //
			);
		}
	}

	@SafeVarargs
	private static ExternalDatabase getFirstWorkingDatabase(Supplier<ExternalDatabase>... suppliers) {

		return Stream.of(suppliers).map(Supplier::get) //
				.filter(ExternalDatabase::checkValidity) //
				.findFirst() //
				.orElse(ExternalDatabase.unavailable());
	}

	/**
	 * Returns a locally provided database.
	 */
	private static ExternalDatabase local() {

		return ProvidedDatabase.builder() //
				.hostname("localhost") //
				.port(1521) //
				.database("XEPDB1") //
				.username("system") //
				.password("oracle") //
				.jdbcUrl("jdbc:oracle:thin:system/oracle@localhost:1521:XEPDB1") //
				.build();
	}

	/**
	 * Returns a database provided via Testcontainers.
	 */
	private static ExternalDatabase testContainer() {

		if (testContainerDatabase == null) {

			try {
				OracleContainer container = new OracleContainer("23.3-slim") //
						.withReuse(true) //
						.withStartupTimeoutSeconds(200); // the default of 60s isn't sufficient
				container.start();

				testContainerDatabase = ProvidedDatabase.builder(container) //
						.database(container.getDatabaseName()).build();

				DataSource dataSource = createDataSource(testContainerDatabase);

				Awaitility.await().ignoreExceptions().until(() -> {
					new JdbcTemplate(dataSource).queryForList("SELECT 'Hello, Oracle' FROM sys.dual");
					return true;
				});
			} catch (IllegalStateException ise) {
				// docker not available.
				testContainerDatabase = ExternalDatabase.unavailable();
			}
		}

		return testContainerDatabase;
	}

	/**
	 * Creates a new Oracle {@link ConnectionFactory} configured from the {@link ExternalDatabase}.
	 */
	public static ConnectionFactory createConnectionFactory(ExternalDatabase database) {

		ConnectionFactoryOptions options = ConnectionUtils.createOptions("oracle", database);
		return ConnectionFactories.get(options);
	}

	/**
	 * Creates a new {@link DataSource} configured from the {@link ExternalDatabase}.
	 */
	public static DataSource createDataSource(ExternalDatabase database) {

		DriverManagerDataSource dataSource = new DriverManagerDataSource();

		dataSource.setUrl(database.getJdbcUrl().replace(":xe", "/XEPDB1"));
		dataSource.setUsername(database.getUsername());
		dataSource.setPassword(database.getPassword());

		return dataSource;
	}
}
