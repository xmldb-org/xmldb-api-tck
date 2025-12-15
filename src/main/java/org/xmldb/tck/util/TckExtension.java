/*
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
 * writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.xmldb.tck.util;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

/**
 * A JUnit 5 extension that prepares and manages the configuration of an XML:DB database for use in
 * tests. This extension registers a specific {@link Database} implementation before each test is
 * run and ensures that the database is deregistered once the test is completed.
 * <p>
 * The database class is determined at runtime using the MicroProfile Config API. The configuration
 * key "xmldb.tck.database.class" specifies the fully qualified class name of the {@link Database}
 * implementation to be loaded.
 */
public class TckExtension implements BeforeEachCallback, AfterEachCallback {
  /**
   * Constructs a new instance of the TckExtension class.
   */
  public TckExtension() {
    super();
  }

  @Override
  public void beforeEach(final ExtensionContext context)
      throws XMLDBException, ReflectiveOperationException {
    final Config config = ConfigProvider.getConfig();
    @SuppressWarnings("unchecked")
    Class<Database> dbCass =
        (Class<Database>) Class.forName(config.getValue("xmldb.tck.database.class", String.class));
    Database db = dbCass.getConstructor().newInstance();
    DatabaseManager.registerDatabase(db);
    context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("db", new DatabaseHandle(db));
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // no action
  }

  record DatabaseHandle(Database db) implements AutoCloseable {
    @Override
    public void close() {
      DatabaseManager.deregisterDatabase(db);
    }
  }
}
