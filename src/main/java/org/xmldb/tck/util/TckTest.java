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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation to mark a JUnit 5 test class as a TCK (Technology Compatibility Kit) test.
 * <p>
 * This annotation applies the {@link TckExtension}, which prepares and manages the configuration of
 * an XML:DB database for the scope of the test. It ensures that the database is properly set up
 * before each test and correctly deregistered after each test.
 * <p>
 * Classes annotated with this will also be tagged with "tck" in the JUnit 5 test reporting.
 */
@Tag("tck")
@Target(value = {ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TckExtension.class)
public @interface TckTest {
}
