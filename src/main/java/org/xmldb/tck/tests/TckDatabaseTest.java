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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.DatabaseInstanceService;
import org.xmldb.api.modules.TransactionService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;
import org.xmldb.api.modules.XUpdateQueryService;
import org.xmldb.api.security.PermissionManagementService;
import org.xmldb.api.security.UserPrincipalLookupService;
import org.xmldb.tck.util.TckTest;

import net.datafaker.Faker;

@TckTest
class TckDatabaseTest {
  @ParameterizedTest
  @MethodSource("serverUrls")
  void getCollection(String serverUrl) throws XMLDBException {
    assertThat(DatabaseManager.getCollection(serverUrl)).isNotNull()
        .satisfies(TckDatabaseTest::assertCollection);
  }

  @ParameterizedTest
  @MethodSource("serverUrls")
  void getCollectionWithCredentials(String serverUrl) throws XMLDBException {
    assertThat(DatabaseManager.getCollection(serverUrl, "guest", "guest")).isNotNull()
        .satisfies(TckDatabaseTest::assertCollection);
  }

  @ParameterizedTest
  @MethodSource("serverUrls")
  void getUnknownCollection(String serverUrl) throws XMLDBException {
    assertThat(DatabaseManager.getCollection(serverUrl + "Blup")).isNull();
    assertThat(DatabaseManager.getCollection(serverUrl).getChildCollection("Blup")).isNull();
  }

  static void assertCollection(Collection collection) throws XMLDBException {
    assertResources(collection, new ResourceData("test1.xml", "<root1/>"),
        new ResourceData("test2.bin", "content2"));
    assertThat(collection.getName()).isEqualTo("/db");
    assertThat(collection.getChildCollectionCount()).isEqualTo(1);
    assertThat(collection.listChildCollections()).containsExactlyInAnyOrder("child");
    assertThat(collection.createId()).isNotNull().isNotBlank();
    assertResourceOperations(collection);
    assertResourceServices(collection);
    assertThat(collection.getChildCollection("child")).isNotNull().satisfies(childCol -> {
      assertThat(childCol.getName()).isEqualTo("/db/child");
      assertResources(childCol, new ResourceData("test3.xml", "<root3/>"),
          new ResourceData("test4.xml", "<root4/>"), new ResourceData("test5.bin", "content5"));
      assertThat(childCol.getChildCollectionCount()).isZero();
      assertThat(childCol.listChildCollections()).isEmpty();
      assertThat(childCol.createId()).isNotNull().isNotBlank();
      assertResourceOperations(childCol);
      assertResourceServices(childCol);
      assertThatNoException().isThrownBy(childCol::close);
    });
    assertThatNoException().isThrownBy(collection::close);
  }

  static void assertResources(Collection collection, ResourceData... expectedResources)
      throws XMLDBException {
    assertThat(collection.getResourceCount()).isEqualTo(expectedResources.length);
    assertThat(collection.listResources()).containsExactlyInAnyOrder(
        Stream.of(expectedResources).map(ResourceData::id).toArray(String[]::new));
    for (ResourceData expectedResource : expectedResources) {
      final String id = expectedResource.id();
      assertThat(collection.getResource(id)).isNotNull().satisfies(res -> {
        assertThat(res.getId()).isEqualTo(id);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        res.getContentAsStream(baos);
        assertThat(baos.toString(UTF_8)).isEqualTo(expectedResource.content());
      });
    }
  }

  static void assertResourceOperations(Collection collection) throws XMLDBException {
    ResourceData xmlResource = assertStoreNewXmlResource(collection);
    assertLoadAndRemoveResource(collection, xmlResource);
    ResourceData binaryResource = assertStoreNewBinaryResource(collection);
    assertLoadAndRemoveResource(collection, binaryResource);
  }

  static void assertResourceServices(Collection collection) {
    for (Class<? extends Service> serviceClass : List.of(CollectionManagementService.class,
        DatabaseInstanceService.class, TransactionService.class, XPathQueryService.class,
        XQueryService.class, XUpdateQueryService.class, PermissionManagementService.class,
        UserPrincipalLookupService.class)) {
      assertThat(collection.hasService(serviceClass)).isTrue();
      assertThat(collection.findService(serviceClass)).isNotEmpty().get().satisfies(service -> {
        assertThat(service.getName()).isEqualTo(serviceClass.getSimpleName());
        assertThat(service.getVersion()).isEqualTo("1.0");
      });
    }
  }

  static ResourceData assertStoreNewXmlResource(Collection collection) throws XMLDBException {
    try (final XMLResource resource = collection.createResource(null, XMLResource.class)) {
      assertThat(resource.getId()).isNotNull().isNotBlank();
      final String content = "<rootContent>%s</rootContent>".formatted(generateData());
      resource.setContent(content);
      assertThat(resource.getContent()).isEqualTo(content);
      collection.storeResource(resource);
      return new ResourceData(resource.getId(), content);
    }
  }

  static ResourceData assertStoreNewBinaryResource(Collection collection) throws XMLDBException {
    try (final BinaryResource resource = collection.createResource(null, BinaryResource.class)) {
      assertThat(resource.getId()).isNotNull().isNotBlank();
      final byte[] content = generateData().getBytes(UTF_8);
      resource.setContent(content);
      assertThat(resource.getContent()).isEqualTo(content);
      collection.storeResource(resource);
      return new ResourceData(resource.getId(), new String(content, UTF_8));
    }
  }

  static void assertLoadAndRemoveResource(Collection collection, ResourceData resource)
      throws XMLDBException {
    assertThat(collection.listResources()).contains(resource.id());
    try (final Resource loadedResource = collection.getResource(resource.id())) {
      assertThat(loadedResource).isNotNull();
      collection.removeResource(loadedResource);
    }
    assertThat(collection.listResources()).doesNotContain(resource.id());
  }

  static String generateData() {
    return new Faker().lorem().fixedString(4096 + 20);
  }

  record ResourceData(String id, String content) {
  }

  static Stream<String> serverUrls() {
    return Stream.of(ConfigProvider.getConfig().getValue("xmldb.tck.root.url", String.class));
  }
}
