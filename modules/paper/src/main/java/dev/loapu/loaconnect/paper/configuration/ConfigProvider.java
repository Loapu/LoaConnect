package dev.loapu.loaconnect.paper.configuration;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.util.Objects;

public class ConfigProvider
{
	private final GeneralSection general;
	private final IdpSection idp;
	
	public ConfigProvider(LoaConnectPlugin plugin)
	{
		plugin.saveDefaultConfig();
		ConfigurationSection generalSection = plugin.getConfig().getConfigurationSection("general");
		assert generalSection != null;
		general = new GeneralSection(
			generalSection.getString("base-url"),
			generalSection.getInt("port"),
			generalSection.getBoolean("behind-reverse-proxy"),
			"/callback",
			URI.create(generalSection.getString("base-url") + (generalSection.getBoolean("behind-reverse-proxy") ? "" : ":" + generalSection.getInt("port")) + "/callback"),
			generalSection.getInt("login-timeout-in-minutes")
		);
		ConfigurationSection idpSection = plugin.getConfig().getConfigurationSection("idp");
		assert idpSection != null;
		idp = new IdpSection(
			idpSection.getString("name"),
			Issuer.parse(idpSection.getString("issuer")),
			new ClientID(idpSection.getString("client-id")),
			new Secret(Objects.requireNonNull(idpSection.getString("client-secret"))),
			Scope.parse("openid")
		);
		
		if (!general.valid())
		{
			plugin.error("General section not valid, shutting down...");
			plugin.getServer().getPluginManager().disablePlugin(plugin);
			return;
		}
		if (!idp.valid())
		{
			plugin.error("IdP section not valid, shutting down...");
			plugin.getServer().getPluginManager().disablePlugin(plugin);
		}
	}
	
	public GeneralSection general()
	{
		return general;
	}
	
	public IdpSection idp()
	{
		return idp;
	}
}
