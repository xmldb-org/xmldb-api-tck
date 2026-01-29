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

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
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
public class TckExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
  private static final String DB = "db";
  private static final String ROOT_COLLECTION = "rootCollection";
  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(TckExtension.class);

  /**
   * Constructs a new instance of the TckExtension class.
   */
  public TckExtension() {
    super();
  }

  @Override
  public void beforeAll(@NonNull ExtensionContext context) throws Exception {
    final ExtensionContext.Store store = context.getRoot().getStore(NAMESPACE);
    store.computeIfAbsent(DB, this::registerTestDatabase);
    store.computeIfAbsent(ROOT_COLLECTION, this::registerTestCollection);
  }

  @Override
  public void beforeEach(@NonNull ExtensionContext context) throws Exception {
    // Get store to retrieve resources
    final ExtensionContext.Store store = context.getRoot().getStore(NAMESPACE);
    for (Object testInstance : context.getRequiredTestInstances().getAllInstances()) {
      // Search for fields annotated with @TckResource
      final Class<?> testClass = testInstance.getClass();
      for (Field field : testClass.getDeclaredFields()) {
        if (field.isAnnotationPresent(TckResource.class)) {
          field.trySetAccessible();
          if (field.getType().isAssignableFrom(Database.class)) {
            field.set(testInstance, store.get(DB, DatabaseHandle.class).db());
          } else if (field.getType().isAssignableFrom(Collection.class)) {
            field.set(testInstance, store.get(ROOT_COLLECTION, Collection.class));
          }
        }
      }
    }
  }


  @Override
  public boolean supportsParameter(@NonNull ParameterContext parameterContext,
      @NonNull ExtensionContext extensionContext) throws ParameterResolutionException {
    final Parameter parameter = parameterContext.getParameter();
    if (parameter.isAnnotationPresent(TckResource.class)) {
      return parameter.getType().isAssignableFrom(Database.class)
          || parameter.getType().isAssignableFrom(Collection.class);
    }
    return false;
  }

  @Override
  @Nullable
  public Object resolveParameter(@NonNull ParameterContext parameterContext,
      @NonNull ExtensionContext extensionContext) throws ParameterResolutionException {
    final Parameter parameter = parameterContext.getParameter();
    if (parameter.isAnnotationPresent(TckResource.class)) {
      final ExtensionContext.Store store = extensionContext.getRoot().getStore(NAMESPACE);
      final Class<?> parameterType = parameter.getType();
      if (parameterType.isAssignableFrom(Database.class)) {
        return store.get(DB, DatabaseHandle.class).db();
      } else if (parameterType.isAssignableFrom(Collection.class)) {
        return store.get(ROOT_COLLECTION, Collection.class);
      }
    }
    return null;
  }

  private DatabaseHandle registerTestDatabase(String key) {
    try {
      final Config config = ConfigProvider.getConfig();
      final String databaseClassName = config.getValue("xmldb.tck.database.class", String.class);
      @SuppressWarnings("unchecked")
      Class<Database> dbCass = (Class<Database>) Class.forName(databaseClassName);
      Database db = dbCass.getConstructor().newInstance();
      DatabaseManager.registerDatabase(db);
      return new DatabaseHandle(db);
    } catch (ReflectiveOperationException | XMLDBException e) {
      throw new IllegalStateException(e);
    }
  }

  private Collection registerTestCollection(String key) {
    try {
      final Config config = ConfigProvider.getConfig();
      final String rootCollectionUrl = config.getValue("xmldb.tck.root.url", String.class);
      final String user = config.getValue("xmldb.tck.test.user", String.class);
      final String password =
          config.getOptionalValue("xmldb.tck.test.password", String.class).orElse("");
      return DatabaseManager.getCollection(rootCollectionUrl, user, password);
    } catch (XMLDBException e) {
      throw new IllegalStateException(e);
    }
  }

  record DatabaseHandle(@NonNull Database db) implements AutoCloseable {
    @Override
    public void close() {
      DatabaseManager.deregisterDatabase(db);
    }
  }
}
