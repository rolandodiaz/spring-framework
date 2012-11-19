/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * A base class for resolving method argument values by reading from the body of
 * a request with {@link HttpMessageConverter}s.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public abstract class AbstractMessageConverterMethodArgumentResolver implements HandlerMethodArgumentResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	protected final List<HttpMessageConverter<?>> messageConverters;

	protected final List<MediaType> allSupportedMediaTypes;

	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "'messageConverters' must not be empty");
		this.messageConverters = messageConverters;
		this.allSupportedMediaTypes = getAllSupportedMediaTypes(messageConverters);
	}

	/**
	 * Return the media types supported by all provided message converters sorted
	 * by specificity via {@link MediaType#sortBySpecificity(List)}.
	 */
	private static List<MediaType> getAllSupportedMediaTypes(List<HttpMessageConverter<?>> messageConverters) {
		Set<MediaType> allSupportedMediaTypes = new LinkedHashSet<MediaType>();
		for (HttpMessageConverter<?> messageConverter : messageConverters) {
			allSupportedMediaTypes.addAll(messageConverter.getSupportedMediaTypes());
		}
		List<MediaType> result = new ArrayList<MediaType>(allSupportedMediaTypes);
		MediaType.sortBySpecificity(result);
		return Collections.unmodifiableList(result);
	}

	/**
	 * Creates the method argument value of the expected parameter type by
	 * reading from the given request.
	 *
	 * @param <T> the expected type of the argument value to be created
	 * @param webRequest the current request
	 * @param methodParam the method argument
	 * @param paramType the type of the argument value to be created
	 * @return the created method argument value
	 * @throws IOException if the reading from the request fails
	 * @throws HttpMediaTypeNotSupportedException if no suitable message converter is found
	 */
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest,
			MethodParameter methodParam, Type paramType) throws IOException, HttpMediaTypeNotSupportedException {

		HttpInputMessage inputMessage = createInputMessage(webRequest);
		return readWithMessageConverters(inputMessage, methodParam, paramType);
	}

	/**
	 * Creates the method argument value of the expected parameter type by reading
	 * from the given HttpInputMessage.
	 *
	 * @param <T> the expected type of the argument value to be created
	 * @param inputMessage the HTTP input message representing the current request
	 * @param methodParam the method argument
	 * @param paramType the type of the argument value to be created
	 * @return the created method argument value
	 * @throws IOException if the reading from the request fails
	 * @throws HttpMediaTypeNotSupportedException if no suitable message converter is found
	 */
	@SuppressWarnings("unchecked")
	protected <T> Object readWithMessageConverters(HttpInputMessage inputMessage,
			MethodParameter methodParam, Type paramType) throws IOException, HttpMediaTypeNotSupportedException {

				MediaType contentType = inputMessage.getHeaders().getContentType();
				if (contentType == null) {
					contentType = MediaType.APPLICATION_OCTET_STREAM;
				}

				Class<T> paramClass = getParamClass(paramType);

				for (HttpMessageConverter<?> messageConverter : this.messageConverters) {
					if (messageConverter instanceof GenericHttpMessageConverter) {
						GenericHttpMessageConverter genericMessageConverter = (GenericHttpMessageConverter) messageConverter;
						if (genericMessageConverter.canRead(paramType, contentType)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Reading [" + paramType + "] as \"" +
										contentType + "\" using [" + messageConverter + "]");
							}
							return (T) genericMessageConverter.read(paramType, inputMessage);
						}
					}
					if (paramClass != null) {
						if (messageConverter.canRead(paramClass, contentType)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Reading [" + paramClass.getName() + "] as \"" + contentType + "\" using [" +
										messageConverter + "]");
							}
							return ((HttpMessageConverter<T>) messageConverter).read(paramClass, inputMessage);
						}
					}
				}

				throw new HttpMediaTypeNotSupportedException(contentType, allSupportedMediaTypes);
			}

	private Class getParamClass(Type paramType) {
		if (paramType instanceof Class) {
			return (Class) paramType;
		}
		else if (paramType instanceof GenericArrayType) {
			Type componentType = ((GenericArrayType) paramType).getGenericComponentType();
			if (componentType instanceof Class) {
				// Surely, there should be a nicer way to determine the array type
				return Array.newInstance((Class<?>) componentType, 0).getClass();
			}
		}
		else if (paramType instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) paramType;
			if (parameterizedType.getRawType() instanceof Class) {
				return (Class) parameterizedType.getRawType();
			}
		}
		return null;
	}

	/**
	 * Creates a new {@link HttpInputMessage} from the given {@link NativeWebRequest}.
	 *
	 * @param webRequest the web request to create an input message from
	 * @return the input message
	 */
	protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		return new ServletServerHttpRequest(servletRequest);
	}

}