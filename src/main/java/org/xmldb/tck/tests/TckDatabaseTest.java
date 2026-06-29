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
package org.xmldb.tck.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import java.util.UUID;

import org.assertj.core.api.ThrowingConsumer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.tck.util.TckTest;

@TckTest
class TckDatabaseTest {
  final Config config = ConfigProvider.getConfig();

  String serverUrl;

  @BeforeEach
  void setUp() {
    serverUrl = config.getValue("xmldb.tck.root.url", String.class);
  }

  @Test
  void getCollection() throws XMLDBException {
    assertThat(DatabaseManager.getCollection(serverUrl)).isNotNull()
        .satisfies(rootCollectionAssertions());
  }

  @Test
  void getCollectionWithCredentials() throws XMLDBException {
    final String user = config.getValue("xmldb.tck.test.user", String.class);
    final String password =
        config.getOptionalValue("xmldb.tck.test.password", String.class).orElse(null);
    assertThat(DatabaseManager.getCollection(serverUrl, user, password)).isNotNull()
        .satisfies(rootCollectionAssertions());
  }

  @Test
  void getUnknownCollection() throws XMLDBException {
    String collectionRoot = "/someCollection" + UUID.randomUUID();
    assertThat(DatabaseManager.getCollection(serverUrl + collectionRoot)).isNull();
    assertThat(DatabaseManager.getCollection(serverUrl).getChildCollection(collectionRoot))
        .isNull();
  }

  ThrowingConsumer<Object>[] rootCollectionAssertions() {
    String rootCollectionName = config.getValue("xmldb.tck.root.collection", String.class);
    assertThat(rootCollectionName).isNotNull();
    String testCollectionName = config.getValue("xmldb.tck.test.collection", String.class);
    assertThat(testCollectionName).isNotNull();
    List<ThrowingConsumer<Collection>> assertions = List.of(
        collection -> assertThat(collection.getName()).isEqualTo(rootCollectionName),
        collection -> assertThat(collection.createId()).isNotNull().isNotBlank(),
        collection -> assertThat(collection.listChildCollections()).contains(testCollectionName),
        collection -> assertThatNoException().isThrownBy(collection::close));
    return assertions.toArray(ThrowingConsumer[]::new);
  }
}
