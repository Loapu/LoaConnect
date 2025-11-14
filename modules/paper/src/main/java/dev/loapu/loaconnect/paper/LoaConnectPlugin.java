package dev.loapu.loaconnect.paper;

import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import dev.loapu.loaconnect.paper.authentication.TokenUtil;
import dev.loapu.loaconnect.paper.configuration.ConfigProvider;
import dev.loapu.loaconnect.paper.hooks.LuckPermsHook;
import dev.loapu.loaconnect.paper.listeners.ConnectionListener;
import dev.loapu.loaconnect.paper.listeners.DialogListener;
import dev.loapu.loaconnect.paper.storage.UserStorage;
import dev.loapu.loaconnect.paper.transactions.Transaction;
import dev.loapu.loaconnect.paper.webserver.ServerProvider;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LoaConnectPlugin extends JavaPlugin implements Listener
{
	private static LoaConnectPlugin instance;
	
	private OIDCProviderMetadata providerMetadata;
	private ConfigProvider configProvider;
	private ConnectionListener connectionListener;
	private int providerRetries = 0;
	
	public static LoaConnectPlugin instance()
	{
		return instance;
	}
	
	@Override
	public void onLoad()
	{
		instance = this;
	}
	
	@Override
	public void onEnable()
	{
		configProvider = new ConfigProvider(this);
		
		LuckPermsHook.instance().init();
		
		connectionListener = new ConnectionListener(this);
		new DialogListener(this);
		ServerProvider.instance().start();
		getServer().getAsyncScheduler().runNow(this, task -> fetchProviderMetadata(new Transaction()));
	}
	
	private void fetchProviderMetadata(Transaction transaction)
	{
		transaction.info("Fetching provider metadata for issuer {}...", configProvider.idp().issuer());
		InetAddress issurerIp = null;
		try
		{
			issurerIp = InetAddress.getByName(URI.create(configProvider.idp().issuer().getValue()).getHost());
			providerMetadata = OIDCProviderMetadata.resolve(configProvider.idp().issuer(), 1000, 1000);
			transaction.info("Success!");
			transaction.end();
			providerRetries = -1;
		}
		catch (IOException | GeneralException e)
		{
			transaction.warn("Provider metadata could not be resolved: {}", e.getMessage());
			transaction.warn("Issuer: {}", issurerIp);
			if (providerRetries == 5)
			{
				transaction.error("Maximum amount of retries exceeded, shutting down...");
				transaction.end();
				getServer().getPluginManager().disablePlugin(this);
				return;
			}
			providerRetries++;
			transaction.warn("Trying again in 60 seconds...");
			getServer().getAsyncScheduler().runDelayed(this, task -> fetchProviderMetadata(transaction), 60, TimeUnit.SECONDS);
		}
	}
	
	public InetAddress issuerIp()
	{
		InetAddress issurerIp = null;
		try
		{
			issurerIp = InetAddress.getByName(URI.create(configProvider.idp().issuer().getValue()).getHost());
		}
		catch (IOException e)
		{
			// ignored
		}
		return issurerIp;
	}
	
	@Override
	public void onDisable()
	{
		ServerProvider.instance().stop();
		UserStorage.save();
	}
	
	@SuppressWarnings("UnstableApiUsage")
	public Dialog createDialog(UUID uniqueId, Transaction transaction)
	{
		State state = new State();
		Nonce nonce = new Nonce();
		
		UserStorage.add(uniqueId, state, nonce);
		transaction.linkState(state);
		
		AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
			new ResponseType(ResponseType.Value.CODE),
			configProvider.idp().scope(),
			configProvider.idp().clientId(),
			configProvider.general().callbackUri()
		)
												.endpointURI(providerMetadata.getAuthorizationEndpointURI())
												.state(state)
												.nonce(nonce)
												.codeChallenge(TokenUtil.instance().codeVerifier(), CodeChallengeMethod.S256)
												.build();
		
		return Dialog.create(builder ->
								 builder.empty()
									 .base(DialogBase.builder(Component.text("Anmeldung"))
											   .canCloseWithEscape(false)
											   .body(List.of(
												   DialogBody.plainMessage(Component.text("Bitte melde dich via " + configProvider.idp().name() + " an, um auf diesem Server zu spielen.")),
												   DialogBody.plainMessage(Component.text("Nach " + configProvider.general().loginTimeoutInMinutes() + " Minuten Inaktivität wird deine Verbindung getrennt."))
											   ))
											   .build())
									 .type(DialogType.confirmation(
										 ActionButton.builder(Component.text("OK", NamedTextColor.GREEN)).tooltip(Component.text("Mit Klick auf OK wirst du zu " + configProvider.idp().name() + " weitergeleitet.")).action(DialogAction.staticAction(ClickEvent.openUrl(authRequest.toURI().toString()))).build(),
										 ActionButton.builder(Component.text("Abbrechen", NamedTextColor.RED)).tooltip(Component.text("Wenn du die Anmeldung via " + configProvider.idp().name() + " ablehnst, wird deine Verbindung mit dem Server getrennt.")).action(DialogAction.customClick(Key.key("loaconnect:auth/deny"), null)).build()
									 ))
		);
	}
	
	public OIDCProviderMetadata providerMetadata()
	{
		return providerMetadata;
	}
	
	public ConfigProvider configProvider()
	{
		return configProvider;
	}
	
	public ConnectionListener connectionListener()
	{
		return connectionListener;
	}
}
