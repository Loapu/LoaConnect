package dev.loapu.loaconnect.paper.configuration;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.transactions.Transaction;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.util.Objects;

public class ConfigProvider
{
	private final GeneralSection general;
	private final IdpSection idp;
	
	public ConfigProvider(LoaConnectPlugin plugin)
	{
		Transaction transaction = new Transaction();
		transaction.info("Loading plugin configuration...");
		plugin.saveDefaultConfig();
		ConfigurationSection generalSection = plugin.getConfig().getConfigurationSection("general");
		assert generalSection != null;
		general = new GeneralSection(
			generalSection.getString("base-url"),
			generalSection.getInt("port"),
			generalSection.getBoolean("behind-reverse-proxy"),
			URI.create(generalSection.getString("base-url") + (generalSection.getBoolean("behind-reverse-proxy") ? "" : ":" + generalSection.getInt("port")) + "/callback"),
			generalSection.getInt("login-timeout-in-minutes"),
			generalSection.getBoolean("sync-groups"),
			generalSection.getString("group-prefix")
		);
		ConfigurationSection idpSection = plugin.getConfig().getConfigurationSection("idp");
		assert idpSection != null;
		idp = new IdpSection(
			idpSection.getString("name"),
			Issuer.parse(idpSection.getString("issuer")),
			new ClientID(idpSection.getString("client-id")),
			new Secret(Objects.requireNonNull(idpSection.getString("client-secret"))),
			Scope.parse(general.syncGroups() ? "openid groups" : "openid")
		);
		
		if (!general.valid())
		{
			transaction.error("General section not valid, shutting down...");
			plugin.getServer().getPluginManager().disablePlugin(plugin);
			return;
		}
		if (!idp.valid())
		{
			transaction.error("IdP section not valid, shutting down...");
			plugin.getServer().getPluginManager().disablePlugin(plugin);
		}
		transaction.info("Success!");
		transaction.end();
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
