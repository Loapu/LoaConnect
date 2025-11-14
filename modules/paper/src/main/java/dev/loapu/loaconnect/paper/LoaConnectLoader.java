package dev.loapu.loaconnect.paper;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

@SuppressWarnings("UnstableApiUsage")
public class LoaConnectLoader implements PluginLoader
{
	@Override
	public void classloader(PluginClasspathBuilder classpathBuilder)
	{
		MavenLibraryResolver resolver = new MavenLibraryResolver();
		resolver.addRepository(new RemoteRepository.Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
		resolver.addDependency(new Dependency(new DefaultArtifact("com.nimbusds:oauth2-oidc-sdk:11.30.1"), null));
		classpathBuilder.addLibrary(resolver);
	}
}
