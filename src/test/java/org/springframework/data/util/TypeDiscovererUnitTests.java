/*
 * Copyright 2011-2020 the original author or authors.
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
package org.springframework.data.util;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.util.ClassTypeInformation.from;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link TypeDiscoverer}.
 *
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
public class TypeDiscovererUnitTests {

	static final Map<TypeVariable<?>, Type> EMPTY_MAP = Collections.emptyMap();

	@Mock Map<TypeVariable<?>, Type> firstMap;
	@Mock Map<TypeVariable<?>, Type> secondMap;

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullType() {
		new TypeDiscoverer<>(null, null);
	}

	@Test
	public void isNotEqualIfTypesDiffer() {

		TypeDiscoverer<Object> objectTypeInfo = new TypeDiscoverer<>(Object.class, EMPTY_MAP);
		TypeDiscoverer<String> stringTypeInfo = new TypeDiscoverer<>(String.class, EMPTY_MAP);

		assertThat(objectTypeInfo.equals(stringTypeInfo)).isFalse();
	}

	@Test
	public void isNotEqualIfTypeVariableMapsDiffer() {

		assertThat(firstMap.equals(secondMap)).isFalse();

		TypeDiscoverer<Object> first = new TypeDiscoverer<>(Object.class, firstMap);
		TypeDiscoverer<Object> second = new TypeDiscoverer<>(Object.class, secondMap);

		assertThat(first.equals(second)).isFalse();
	}

	@Test
	public void dealsWithTypesReferencingThemselves() {

		TypeInformation<SelfReferencing> information = from(SelfReferencing.class);
		TypeInformation<?> first = information.getProperty("parent").getMapValueType();
		TypeInformation<?> second = first.getProperty("map").getMapValueType();

		assertThat(second).isEqualTo(first);
	}

	@Test
	public void dealsWithTypesReferencingThemselvesInAMap() {

		TypeInformation<SelfReferencingMap> information = from(SelfReferencingMap.class);
		TypeInformation<?> property = information.getProperty("map");

		assertThat(property.getMapValueType()).isEqualTo(information);
	}

	@Test
	public void returnsComponentAndValueTypesForMapExtensions() {

		TypeInformation<?> discoverer = new TypeDiscoverer<>(CustomMap.class, EMPTY_MAP);

		assertThat(discoverer.getMapValueType().getType()).isEqualTo(Locale.class);
		assertThat(discoverer.getComponentType().getType()).isEqualTo(String.class);
	}

	@Test
	public void returnsComponentTypeForCollectionExtension() {

		TypeDiscoverer<CustomCollection> discoverer = new TypeDiscoverer<>(CustomCollection.class, firstMap);

		assertThat(discoverer.getComponentType().getType()).isEqualTo(String.class);
	}

	@Test
	public void returnsComponentTypeForArrays() {

		TypeDiscoverer<String[]> discoverer = new TypeDiscoverer<>(String[].class, EMPTY_MAP);

		assertThat(discoverer.getComponentType().getType()).isEqualTo(String.class);
	}

	@Test // DATACMNS-57
	public void discoveresConstructorParameterTypesCorrectly() throws NoSuchMethodException, SecurityException {

		TypeDiscoverer<GenericConstructors> discoverer = new TypeDiscoverer<>(GenericConstructors.class, firstMap);
		Constructor<GenericConstructors> constructor = GenericConstructors.class.getConstructor(List.class, Locale.class);
		List<TypeInformation<?>> types = discoverer.getParameterTypes(constructor);

		assertThat(types).hasSize(2);
		assertThat(types.get(0).getType()).isEqualTo(List.class);
		assertThat(types.get(0).getComponentType().getType()).isEqualTo(String.class);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void returnsNullForComponentAndValueTypesForRawMaps() {

		TypeDiscoverer<Map> discoverer = new TypeDiscoverer<>(Map.class, EMPTY_MAP);

		assertThat(discoverer.getComponentType()).isNull();
		assertThat(discoverer.getMapValueType()).isNull();
	}

	@Test // DATACMNS-167
	@SuppressWarnings("rawtypes")
	public void doesNotConsiderTypeImplementingIterableACollection() {

		TypeDiscoverer<Person> discoverer = new TypeDiscoverer<>(Person.class, EMPTY_MAP);
		TypeInformation reference = from(Address.class);

		TypeInformation<?> addresses = discoverer.getProperty("addresses");

		assertThat(addresses).satisfies(it -> {
			assertThat(it.isCollectionLike()).isFalse();
			assertThat(it.getComponentType()).isEqualTo(reference);
		});

		TypeInformation<?> adressIterable = discoverer.getProperty("addressIterable");

		assertThat(adressIterable).satisfies(it -> {
			assertThat(it.isCollectionLike()).isTrue();
			assertThat(it.getComponentType()).isEqualTo(reference);
		});
	}

	@Test // DATACMNS-1342
	public void considersStreamableToBeCollectionLike() {

		TypeInformation<SomeStreamable> type = from(SomeStreamable.class);

		assertThat(type.isCollectionLike()).isFalse();
		assertThat(type.getRequiredProperty("streamable").isCollectionLike()).isTrue();
	}

	@Test // GH-3500
	public void superclassFieldsDoNotHideSubclassFields() {

		TypeDiscoverer<TheOne> discoverer = new TypeDiscoverer<>(TheOne.class, EMPTY_MAP);

		assertThat(discoverer.getProperty("theOne").getType()).isEqualTo(String.class);
		assertThat(discoverer.getProperty("theOther").getType()).isEqualTo(String.class);
	}

	@Test // GH-3500
	public void doesNotRetainLookupsForUnknownProperties() {

		TypeDiscoverer<Person> discoverer = new TypeDiscoverer<>(Person.class, EMPTY_MAP);

		assertThat(discoverer.getProperty("addresses")).isNotNull();

		Map<String, Integer> sizes = getSizesOfMapsHeldBy(discoverer);

		for (int i = 0; i < 10000; i++) {
			assertThat(discoverer.getProperty("unknown" + i)).isNull();
		}

		assertThat(getSizesOfMapsHeldBy(discoverer)).isEqualTo(sizes);
	}

	/**
	 * Returns the sizes of all {@link Map}s (potentially wrapped into a {@link Lazy}) held in instance fields of the given
	 * object.
	 */
	public static Map<String, Integer> getSizesOfMapsHeldBy(Object target) {

		Map<String, Integer> sizes = new HashMap<>();

		org.springframework.util.ReflectionUtils.doWithFields(target.getClass(), field -> {

			org.springframework.util.ReflectionUtils.makeAccessible(field);
			Object value = field.get(target);

			if (value instanceof Lazy) {
				value = ((Lazy<?>) value).orElse(null);
			}

			if (value instanceof Map) {
				sizes.put(field.getName(), ((Map<?, ?>) value).size());
			}

		}, field -> !Modifier.isStatic(field.getModifiers()));

		return sizes;
	}

	@Test // GH-3500
	public void resolvesPropertiesFromAccessorsAndInterfaces() {

		TypeInformation<AccessorsOnly> type = from(AccessorsOnly.class);

		assertThat(type.getRequiredProperty("firstname").getType()).isEqualTo(String.class);
		assertThat(type.getRequiredProperty("Firstname").getType()).isEqualTo(String.class);
		assertThat(type.getRequiredProperty("lastname").getType()).isEqualTo(Locale.class);
		assertThat(type.getProperty("Unknown")).isNull();

		TypeInformation<ExtendedNamed> extended = from(ExtendedNamed.class);

		assertThat(extended.getRequiredProperty("name").getType()).isEqualTo(String.class);
		assertThat(extended.getRequiredProperty("nickname").getType()).isEqualTo(String.class);
	}

	class Person {

		Addresses addresses;
		Iterable<Address> addressIterable;
	}

	abstract class Addresses implements Iterable<Address> {

	}

	class Address {

	}

	class SelfReferencing {

		Map<String, SelfReferencingMap> parent;
	}

	class SelfReferencingMap {
		Map<String, SelfReferencingMap> map;
	}

	interface CustomMap extends Map<String, Locale> {

	}

	interface CustomCollection extends Collection<String> {

	}

	public static class GenericConstructors {

		public GenericConstructors(List<String> first, Locale second) {

		}
	}

	// DATACMNS-1342

	static class SomeStreamable implements Streamable<String> {

		Streamable<String> streamable;

		@Override
		public Iterator<String> iterator() {
			return Collections.emptyIterator();
		}
	}

	// GH-3500

	static class SuperclassWithProperties {
		private CharSequence theOne;
		private String theOther;
	}

	class TheOne extends SuperclassWithProperties {
		private String theOne;
	}

	static abstract class AccessorsOnly {

		public abstract String getFirstname();

		public abstract void setLastname(Locale lastname);
	}

	interface Named {
		String getName();
	}

	interface ExtendedNamed extends Named {
		String getNickname();
	}
}
