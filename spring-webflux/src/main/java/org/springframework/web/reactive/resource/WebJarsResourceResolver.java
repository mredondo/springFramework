/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.resource;

import java.util.List;

import org.webjars.MultipleMatchesException;
import org.webjars.WebJarAssetLocator;
import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.web.server.ServerWebExchange;

/**
 * A {@code ResourceResolver} that delegates to the chain to locate a resource and then
 * attempts to find a matching versioned resource contained in a WebJar JAR file.
 *
 * <p>This allows WebJars.org users to write version agnostic paths in their templates,
 * like {@code <script src="/jquery/jquery.min.js"/>}.
 * This path will be resolved to the unique version {@code <script src="/jquery/1.2.0/jquery.min.js"/>},
 * which is a better fit for HTTP caching and version management in applications.
 *
 * <p>This also resolves resources for version agnostic HTTP requests {@code "GET /jquery/jquery.min.js"}.
 *
 * <p>This resolver requires the "org.webjars:webjars-locator" library on classpath,
 * and is automatically registered if that library is present.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 5.0
 * @see <a href="http://www.webjars.org">webjars.org</a>
 */
public class WebJarsResourceResolver extends AbstractResourceResolver {

	private final static String WEBJARS_LOCATION = "META-INF/resources/webjars/";

	private final static int WEBJARS_LOCATION_LENGTH = WEBJARS_LOCATION.length();


	private final WebJarAssetLocator webJarAssetLocator;


	/**
	 * Create a {@code WebJarsResourceResolver} with a default {@code WebJarAssetLocator} instance.
	 */
	public WebJarsResourceResolver() {
		this(new WebJarAssetLocator());
	}

	/**
	 * Create a {@code WebJarsResourceResolver} with a custom {@code WebJarAssetLocator} instance,
	 * e.g. with a custom index.
	 */
	public WebJarsResourceResolver(WebJarAssetLocator webJarAssetLocator) {
		this.webJarAssetLocator = webJarAssetLocator;
	}


	@Override
	protected Mono<Resource> resolveResourceInternal(ServerWebExchange exchange, String requestPath,
			List<? extends Resource> locations, ResourceResolverChain chain) {

		return chain.resolveResource(exchange, requestPath, locations)
				.switchIfEmpty(Mono.defer(() -> {
					String webJarsResourcePath = findWebJarResourcePath(requestPath);
					if (webJarsResourcePath != null) {
						return chain.resolveResource(exchange, webJarsResourcePath, locations);
					}
					else {
						return Mono.empty();
					}
				}));
	}

	@Override
	protected Mono<String> resolveUrlPathInternal(String resourceUrlPath,
			List<? extends Resource> locations, ResourceResolverChain chain) {

		return chain.resolveUrlPath(resourceUrlPath, locations)
				.switchIfEmpty(Mono.defer(() -> {
					String webJarResourcePath = findWebJarResourcePath(resourceUrlPath);
					if (webJarResourcePath != null) {
						return chain.resolveUrlPath(webJarResourcePath, locations);
					}
					else {
						return Mono.empty();
					}
				}));
	}

	protected String findWebJarResourcePath(String path) {
		try {
			int startOffset = (path.startsWith("/") ? 1 : 0);
			int endOffset = path.indexOf("/", 1);
			if (endOffset != -1) {
				String webjar = path.substring(startOffset, endOffset);
				String partialPath = path.substring(endOffset);
				String webJarPath = webJarAssetLocator.getFullPath(webjar, partialPath);
				return webJarPath.substring(WEBJARS_LOCATION_LENGTH);
			}
		}
		catch (MultipleMatchesException ex) {
			if (logger.isWarnEnabled()) {
				logger.warn("WebJar version conflict for \"" + path + "\"", ex);
			}
		}
		catch (IllegalArgumentException ex) {
			if (logger.isTraceEnabled()) {
				logger.trace("No WebJar resource found for \"" + path + "\"");
			}
		}
		return null;
	}

}
