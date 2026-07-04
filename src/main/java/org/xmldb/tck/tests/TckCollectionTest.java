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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.xmldb.tck.util.TckResource;
import org.xmldb.tck.util.TckTest;

import net.datafaker.Faker;

@TckTest
class TckCollectionTest {
  final Config config = ConfigProvider.getConfig();

  @TckResource
  Collection rootCollection;

  Collection testCollection;

  @BeforeEach
  void setUp() throws XMLDBException {
    final String testCollectionName = config.getValue("xmldb.tck.test.collection", String.class);
    testCollection = rootCollection.getChildCollection(testCollectionName);
    assertThat(testCollection)
        .withFailMessage("configured test collection `%s` must exist", testCollectionName)
        .isNotNull();
    cleanTestCollection();
  }

  @AfterEach
  void tearDown() throws XMLDBException {
    cleanTestCollection();
  }

  @ParameterizedTest
  @ValueSource(classes = {CollectionManagementService.class, DatabaseInstanceService.class,
      TransactionService.class, XPathQueryService.class, XQueryService.class,
      XUpdateQueryService.class, PermissionManagementService.class,
      UserPrincipalLookupService.class})
  void testServices(Class<? extends Service> serviceClass) {
    assertThat(testCollection.hasService(serviceClass))
        .withFailMessage("service %s missing", serviceClass).isTrue();
    assertThat(testCollection.findService(serviceClass)).isNotEmpty().get().satisfies(service -> {
      assertThat(service.getName()).isEqualTo(serviceClass.getSimpleName());
      assertThat(service.getVersion()).isEqualTo("1.0");
    });
  }

  @Test
  void binaryResource() throws XMLDBException {
    final ResourceData binaryResource = assertStoreNewBinaryResource(testCollection);
    assertLoadAndRemoveResource(testCollection, binaryResource);
  }

  @Test
  void xmlResource() throws XMLDBException {
    final ResourceData xmlResource = assertStoreNewXmlResource(testCollection);
    assertLoadAndRemoveResource(testCollection, xmlResource);
  }

  @Test
  void childCollectionsAndParent() throws XMLDBException {
    final String childName = "tck-child-" + testCollection.createId();
    final CollectionManagementService service =
        testCollection.getService(CollectionManagementService.class);
    try (Collection childCollection = service.createCollection(childName)) {
      assertThat(childCollection).isNotNull();
      assertThat(testCollection.getChildCollectionCount()).isGreaterThanOrEqualTo(1);
      assertThat(testCollection.listChildCollections()).contains(childName);
      assertThat(testCollection.getChildCollection(childName)).isNotNull();
      assertThat(childCollection.getParentCollection()).isNotNull();
      assertThat(childCollection.getParentCollection().getName())
          .isEqualTo(testCollection.getName());
    }
    assertThatNoException().isThrownBy(() -> service.removeCollection(childName));
    assertThat(testCollection.getChildCollection(childName)).isNull();
  }

  @Test
  void resourcesAndCollectionState() throws XMLDBException {
    assertThat(testCollection.getName()).isNotNull().isNotBlank();
    assertThat(testCollection.isOpen()).isTrue();
    assertThat(testCollection.getCreationTime()).isNotNull().isBeforeOrEqualTo(Instant.now());

    assertResources(testCollection);
    final String createdId = testCollection.createId();
    assertThat(createdId).isNotNull().isNotBlank();

    final ResourceData xmlResource = assertStoreNewXmlResource(testCollection);
    final ResourceData binaryResource = assertStoreNewBinaryResource(testCollection);
    assertResources(testCollection, xmlResource, binaryResource);
    assertThat(testCollection.getResourceCount()).isEqualTo(2);
    assertThat(testCollection.listResources()).containsExactlyInAnyOrder(xmlResource.id(),
        binaryResource.id());
  }

  @Test
  void closeCollectionHandle() throws XMLDBException {
    final String serverUrl = config.getValue("xmldb.tck.root.url", String.class);
    final String testCollectionName = config.getValue("xmldb.tck.test.collection", String.class);
    final String user = config.getValue("xmldb.tck.test.user", String.class);
    final String password =
        config.getOptionalValue("xmldb.tck.test.password", String.class).orElse(null);

    try (Collection collectionHandle =
        DatabaseManager.getCollection(serverUrl + "/" + testCollectionName, user, password)) {
      assertThat(collectionHandle).isNotNull();
      assertThat(collectionHandle.isOpen()).isTrue();
      collectionHandle.close();
      assertThat(collectionHandle.isOpen()).isFalse();
    }
  }

  void cleanTestCollection() throws XMLDBException {
    for (String resourceId : testCollection.listResources()) {
      try (Resource resource = testCollection.getResource(resourceId)) {
        if (resource != null) {
          testCollection.removeResource(resource);
        }
      }
    }

    final CollectionManagementService collectionService =
        testCollection.getService(CollectionManagementService.class);
    for (String childCollectionName : testCollection.listChildCollections()) {
      collectionService.removeCollection(childCollectionName);
    }
    assertThat(testCollection.listResources()).isEmpty();
    assertThat(testCollection.listChildCollections()).isEmpty();
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

  static ResourceData assertStoreNewXmlResource(Collection collection) throws XMLDBException {
    try (final XMLResource resource = collection.createResource(null, XMLResource.class)) {
      assertThat(resource.getId()).isNotNull().isNotBlank();
      final String content = "<rootContent>%s</rootContent>".formatted(generateData());
      resource.setContentAsStream(new ByteArrayInputStream(content.getBytes(UTF_8)));
      collection.storeResource(resource);
      return new ResourceData(resource.getId(), content);
    }
  }

  static ResourceData assertStoreNewBinaryResource(Collection collection) throws XMLDBException {
    try (final BinaryResource resource = collection.createResource(null, BinaryResource.class)) {
      assertThat(resource.getId()).isNotNull().isNotBlank();
      final byte[] content = generateData().getBytes(UTF_8);
      resource.setContentAsStream(new ByteArrayInputStream(content));
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
}
