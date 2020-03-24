/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.core.io;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * ResourceLoader resourceLoader = new DefaultResourceLoader();
 *
 * Resource fileResource1 = resourceLoader.getResource("D:/Users/chenming673/Documents/spark.txt");
 * System.out.println("fileResource1 is FileSystemResource:" + (fileResource1 instanceof FileSystemResource));
 *
 * Resource fileResource2 = resourceLoader.getResource("/Users/chenming673/Documents/spark.txt");
 * System.out.println("fileResource2 is ClassPathResource:" + (fileResource2 instanceof ClassPathResource));
 *
 * Resource urlResource1 = resourceLoader.getResource("file:/Users/chenming673/Documents/spark.txt");
 * System.out.println("urlResource1 is UrlResource:" + (urlResource1 instanceof UrlResource));
 *
 * Resource urlResource2 = resourceLoader.getResource("http://www.baidu.com");
 * System.out.println("urlResource1 is urlResource:" + (urlResource2 instanceof  UrlResource));
 */
/**运行结果
 * fileResource1 is FileSystemResource:false
 * fileResource2 is ClassPathResource:true
 * urlResource1 is UrlResource:true
 * urlResource1 is urlResource:true
 */
/**
 * 其实对于 fileResource1 ，我们更加希望是 FileSystemResource 资源类型。但是，事与愿违，
 * 它是 ClassPathResource 类型。为什么呢？在 DefaultResourceLoader#getResource() 方法的资源加载策略中，
 * 我们知道 "D:/Users/chenming673/Documents/spark.txt" 地址，其实在该方法中没有相应的资源类型，
 * 那么它就会在抛出 MalformedURLException 异常时，通过 DefaultResourceLoader#getResourceByPath(...) 方法，
 * 构造一个 ClassPathResource 类型的资源。
 * 而 urlResource1 和 urlResource2 ，指定有协议前缀的资源路径，则通过 URL 就可以定义，所以返回的都是 UrlResource 类型
 *
 *
 * 从上面的示例，我们看到，其实 DefaultResourceLoader 对#getResourceByPath(String) 方法处理其实不是很恰当，
 * 这个时候我们可以使用 org.springframework.core.io.FileSystemResourceLoader 。
 * 它继承 DefaultResourceLoader ，且覆写了 #getResourceByPath(String) 方法，
 * 使之从文件系统加载资源并以 FileSystemResource 类型返回，这样我们就可以得到想要的资源类型。
 */

/**
 * Default implementation of the {@link ResourceLoader} interface.
 * Used by {@link ResourceEditor}, and serves as base class for
 * {@link org.springframework.context.support.AbstractApplicationContext}.
 * Can also be used standalone.
 *
 * <p>Will return a {@link UrlResource} if the location value is a URL,
 * and a {@link ClassPathResource} if it is a non-URL path or a
 * "classpath:" pseudo-URL.
 *
 * @author Juergen Hoeller
 * @since 10.03.2004
 * @see FileSystemResourceLoader
 * @see org.springframework.context.support.ClassPathXmlApplicationContext
 * 与 AbstractResource 相似，org.springframework.core.io.DefaultResourceLoader 是 ResourceLoader 的默认实现。
 */
public class DefaultResourceLoader implements ResourceLoader {

	@Nullable
	private ClassLoader classLoader;

	/**
	 * org.springframework.core.io.ProtocolResolver ，用户自定义协议资源解决策略，
	 * 作为 DefaultResourceLoader 的 SPI：它允许用户自定义资源加载协议，而不需要继承 ResourceLoader 的子类。
	 * 在介绍 Resource 时，提到如果要实现自定义 Resource，我们只需要继承 AbstractResource 即可，但
	 * 是有了 ProtocolResolver 后，我们不需要直接继承 DefaultResourceLoader，
	 * 改为实现 ProtocolResolver 接口也可以实现自定义的 ResourceLoader。
	 *
	 * ProtocolResolver 接口，仅有一个方法 Resource resolve(String location, ResourceLoader resourceLoader)
	 */
	private final Set<ProtocolResolver> protocolResolvers = new LinkedHashSet<>(4);

	private final Map<Class<?>, Map<Resource, ?>> resourceCaches = new ConcurrentHashMap<>(4);


	/**
	 * Create a new DefaultResourceLoader.
	 * <p>ClassLoader access will happen using the thread context class loader
	 * at the time of this ResourceLoader's initialization.
	 * @see java.lang.Thread#getContextClassLoader()
	 * 它接收 ClassLoader 作为构造函数的参数，或者使用不带参数的构造函数。
	 *
	 * 在使用不带参数的构造函数时，使用的 ClassLoader 为默认的 ClassLoader（一般 Thread.currentThread()#getContextClassLoader() ）。
	 * 在使用带参数的构造函数时，可以通过 ClassUtils#getDefaultClassLoader()获取。
	 * 在 Spring 中你会发现该接口并没有实现类，它需要用户自定义，
	 * 自定义的 Resolver 如何加入 Spring 体系呢？调用 DefaultResourceLoader#addProtocolResolver(ProtocolResolver) 方法即可。
	 */
	public DefaultResourceLoader() {
		this.classLoader = ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Create a new DefaultResourceLoader.
	 * @param classLoader the ClassLoader to load class path resources with, or {@code null}
	 * for using the thread context class loader at the time of actual resource access
	 */
	public DefaultResourceLoader(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
	}


	/**
	 * Specify the ClassLoader to load class path resources with, or {@code null}
	 * for using the thread context class loader at the time of actual resource access.
	 * <p>The default is that ClassLoader access will happen using the thread context
	 * class loader at the time of this ResourceLoader's initialization.
	 */
	public void setClassLoader(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	/**
	 * Return the ClassLoader to load class path resources with.
	 * <p>Will get passed to ClassPathResource's constructor for all
	 * ClassPathResource objects created by this resource loader.
	 * @see ClassPathResource
	 */
	@Override
	@Nullable
	public ClassLoader getClassLoader() {
		return (this.classLoader != null ? this.classLoader : ClassUtils.getDefaultClassLoader());
	}

	/**
	 * Register the given resolver with this resource loader, allowing for
	 * additional protocols to be handled.
	 * <p>Any such resolver will be invoked ahead of this loader's standard
	 * resolution rules. It may therefore also override any default rules.
	 * @since 4.3
	 * @see #getProtocolResolvers()
	 */
	public void addProtocolResolver(ProtocolResolver resolver) {
		Assert.notNull(resolver, "ProtocolResolver must not be null");
		this.protocolResolvers.add(resolver);
	}

	/**
	 * Return the collection of currently registered protocol resolvers,
	 * allowing for introspection as well as modification.
	 * @since 4.3
	 */
	public Collection<ProtocolResolver> getProtocolResolvers() {
		return this.protocolResolvers;
	}

	/**
	 * Obtain a cache for the given value type, keyed by {@link Resource}.
	 * @param valueType the value type, e.g. an ASM {@code MetadataReader}
	 * @return the cache {@link Map}, shared at the {@code ResourceLoader} level
	 * @since 5.0
	 */
	@SuppressWarnings("unchecked")
	public <T> Map<Resource, T> getResourceCache(Class<T> valueType) {
		return (Map<Resource, T>) this.resourceCaches.computeIfAbsent(valueType, key -> new ConcurrentHashMap<>());
	}

	/**
	 * Clear all resource caches in this resource loader.
	 * @since 5.0
	 * @see #getResourceCache
	 */
	public void clearResourceCaches() {
		this.resourceCaches.clear();
	}


	/**
	 * ResourceLoader 中最核心的方法为 #getResource(String location) ，
	 * 它根据提供的 location 返回相应的 Resource 。
	 * 而 DefaultResourceLoader 对该方法提供了核心实现
	 * （因为，它的两个子类都没有提供覆盖该方法，所以可以断定 ResourceLoader 的资源加载策略就封装在 DefaultResourceLoader 中)
	 * @param location the resource location
	 * @return
	 */
	@Override
	public Resource getResource(String location) {
		Assert.notNull(location, "Location must not be null");

		// 首先，通过 ProtocolResolver 来加载资源，用户自定义实现
		for (ProtocolResolver protocolResolver : getProtocolResolvers()) {
			Resource resource = protocolResolver.resolve(location, this);
			if (resource != null) {
				return resource;
			}
		}
      // 其次，以 / 开头，返回 ClassPathContextResource 类型的资源，
		if (location.startsWith("/")) {
			return getResourceByPath(location);
		}
		//再次，以 classpath: 开头，返回 ClassPathResource 类型的资源
		else if (location.startsWith(CLASSPATH_URL_PREFIX)) {
			return new ClassPathResource(location.substring(CLASSPATH_URL_PREFIX.length()), getClassLoader());
		}
		// 然后，根据是否为文件 URL ，是则返回 FileUrlResource 类型的资源，否则返回 UrlResource 类型的资源
		else {
			try {
				// Try to parse the location as a URL...
				URL url = new URL(location);
				return (ResourceUtils.isFileURL(url) ? new FileUrlResource(url) : new UrlResource(url));
			}
			catch (MalformedURLException ex) {
				// 最后，返回 ClassPathContextResource 类型的资源
				// No URL -> resolve as resource path.
				return getResourceByPath(location);
			}
		}
	}

	/**
	 * Return a Resource handle for the resource at the given path.
	 * <p>The default implementation supports class path locations. This should
	 * be appropriate for standalone implementations but can be overridden,
	 * e.g. for implementations targeted at a Servlet container.
	 * @param path the path to the resource
	 * @return the corresponding Resource handle
	 * @see ClassPathResource
	 * @see org.springframework.context.support.FileSystemXmlApplicationContext#getResourceByPath
	 * @see org.springframework.web.context.support.XmlWebApplicationContext#getResourceByPath
	 */
	protected Resource getResourceByPath(String path) {
		return new ClassPathContextResource(path, getClassLoader());
	}


	/**
	 * ClassPathResource that explicitly expresses a context-relative path
	 * through implementing the ContextResource interface.
	 * 显式地表示上下文相关相对路径的ClassPathResource
	 */
	protected static class ClassPathContextResource extends ClassPathResource implements ContextResource {

		public ClassPathContextResource(String path, @Nullable ClassLoader classLoader) {
			super(path, classLoader);
		}

		@Override
		public String getPathWithinContext() {
			return getPath();
		}

		@Override
		public Resource createRelative(String relativePath) {
			String pathToUse = StringUtils.applyRelativePath(getPath(), relativePath);
			return new ClassPathContextResource(pathToUse, getClassLoader());
		}
	}

}
