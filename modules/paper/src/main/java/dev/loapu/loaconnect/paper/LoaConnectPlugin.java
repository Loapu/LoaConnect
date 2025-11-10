package dev.loapu.loaconnect.paper;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.ResponseType.Value;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.loapu.loaconnect.paper.configuration.ConfigProvider;
import dev.loapu.loaconnect.paper.configuration.GeneralSection;
import dev.loapu.loaconnect.paper.configuration.IdpSection;
import dev.loapu.loaconnect.paper.storage.UserStorage;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoaConnectPlugin extends JavaPlugin implements Listener
{
	private final Map<UUID, State> pendingStates = new ConcurrentHashMap<>();
	private final Map<UUID, CompletableFuture<Boolean>> awaitingResponse = new ConcurrentHashMap<>();
	
	private OIDCProviderMetadata providerMetadata;
	private final CodeVerifier codeVerifier = new CodeVerifier();
	private HttpServer server;
	private ConfigProvider configProvider;
	
	
	@Override
	public void onLoad()
	{
		getComponentLogger().info("LoaConnect loaded!");
	}
	
	@Override
	public void onEnable()
	{
		getComponentLogger().info("Loading settings...");
		configProvider = new ConfigProvider(this);
		getServer().getPluginManager().registerEvents(this, this);
		try
		{
			server = HttpServer.create(new InetSocketAddress(configProvider.general().port()), 0);
			server.createContext(configProvider.general().callbackEndpoint(), this::handleCallback);
			server.setExecutor(Executors.newSingleThreadExecutor());
			server.start();
			getComponentLogger().info("Callback server started on port {}", configProvider.general().port());
		}
		catch (Exception e)
		{
			getComponentLogger().warn("Callback server could not be started: {}", e.getMessage());
		}
		getServer().getAsyncScheduler().runNow(this, this::fetchProviderMetadata);
	}
	
	private void fetchProviderMetadata(ScheduledTask task)
	{
		try
		{
			providerMetadata = OIDCProviderMetadata.resolve(configProvider.idp().issuer(), 1000, 1000);
			getComponentLogger().info("Provider metadata resolved for issuer {}", providerMetadata.getIssuer());
		}
		catch (IOException | GeneralException e)
		{
			getComponentLogger().warn("Provider metadata could not be resolved: {}", e.getMessage());
			getComponentLogger().warn("Trying again in 60 seconds...");
			getServer().getAsyncScheduler().runDelayed(this, this::fetchProviderMetadata, 60, TimeUnit.SECONDS);
		}
	}
	
	@Override
	public void onDisable()
	{
		if (server != null) server.stop(0);
	}
	
	@EventHandler
	public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event)
	{
		PlayerConfigurationConnection connection = event.getConnection();
		UUID uniqueId = connection.getProfile().getId();
		
		if (uniqueId == null) return;
		UserStorage user = UserStorage.get(uniqueId);
		if (user != null)
		{
			OIDCTokens tokens = user.tokens();
			if (tokens != null)
			{
				Nonce nonce = user.nonce();
				if (validateIDToken(tokens.getIDToken(), nonce)) return;
				AuthorizationGrant refreshGrant = new RefreshTokenGrant(tokens.getRefreshToken());
				TokenRequest request = new TokenRequest(
					providerMetadata.getTokenEndpointURI(),
					new ClientSecretBasic(configProvider.idp().clientId(), configProvider.idp().clientSecret()),
					refreshGrant,
					configProvider.idp().scope()
				);
				try
				{
					TokenResponse response = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());
					
					if (!response.indicatesSuccess()) {
						TokenErrorResponse errorResponse = response.toErrorResponse();
						throw new RuntimeException("Refresh Token request failed: " + errorResponse.getErrorObject().getDescription());
					}
					
					OIDCTokenResponse successResponse = (OIDCTokenResponse) response.toSuccessResponse();
					OIDCTokens newTokens = successResponse.getOIDCTokens();
					
					JWT idToken = newTokens.getIDToken();
					
					if (validateIDToken(idToken, null))
					{
						user.updateTokens(newTokens);
					}
					else
					{
						error("Ungültiger ID-Token.");
					}
					return;
				}
				catch (IOException | ParseException e)
				{
					error("Es gab einen Fehler bei der erneuerung durch einen Refresh-Token für User " + uniqueId);
				}
			}
		}
		
		CompletableFuture<Boolean> response = new CompletableFuture<>();
		response.completeOnTimeout(false, configProvider.general().loginTimeoutInMinutes(), TimeUnit.MINUTES);
		
		awaitingResponse.put(uniqueId, response);
		
		Audience audience = connection.getAudience();
		audience.showDialog(createDialog(uniqueId));
		
		if (!response.join())
		{
			audience.closeDialog();
			connection.disconnect(Component.text("Deine Verbindung zum Server wurde getrennt.", NamedTextColor.RED));
		}
		
		awaitingResponse.remove(uniqueId);
	}
	
	public void removeResponse(UUID uniqueId)
	{
		awaitingResponse.remove(uniqueId);
	}
	
	public void removeState(UUID uniqueId)
	{
		pendingStates.remove(uniqueId);
	}
	
	public void setConnectionJoinResult(UUID uniqueId, boolean value)
	{
		CompletableFuture<Boolean> future = awaitingResponse.get(uniqueId);
		if (future != null) future.complete(value);
	}
	
	private Dialog createDialog(UUID uniqueId)
	{
		State state = new State();
		pendingStates.put(uniqueId, state);
		
		Nonce nonce = new Nonce();
		UserStorage.add(uniqueId, null, nonce);
		
		AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
			new ResponseType(ResponseType.Value.CODE),
			configProvider.idp().scope(),
			configProvider.idp().clientId(),
			configProvider.general().callbackUri()
		)
												.endpointURI(providerMetadata.getAuthorizationEndpointURI())
												.state(state)
												.nonce(nonce)
												.codeChallenge(codeVerifier, CodeChallengeMethod.S256)
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
	
	private void handleCallback(HttpExchange exchange) throws IOException
	{
		try
		{
			String path = exchange.getRequestURI().toString();
			URI requestURI = URI.create(configProvider.general().baseUrl() + path);
			getComponentLogger().info("Handling callback URL: {}", requestURI);
			AuthenticationResponse authResp = AuthenticationResponseParser.parse(requestURI);
			
			if (authResp instanceof AuthenticationErrorResponse authErrorResponse)
			{
				error(exchange, "Bei der Authentifizierungsanfrage ist ein Fehler aufgetreten.");
				return;
			}
			
			AuthenticationSuccessResponse successResp = authResp.toSuccessResponse();
			State state = successResp.getState();
			AuthorizationCode code = successResp.getAuthorizationCode();
			
			UUID uniqueId = pendingStates.entrySet().stream().filter(entry -> entry.getValue().equals(state)).map(Map.Entry::getKey).findFirst().orElse(null);
			
			if (uniqueId == null)
			{
				error(exchange, "Auf diese Anfrage wird nicht gewartet.");
				return;
			}
			
			pendingStates.remove(uniqueId);
			getComponentLogger().info("Getting token for {}", uniqueId);
			TokenResponse tokenResponse = requestToken(code);
			
			if (!tokenResponse.indicatesSuccess())
			{
				error(exchange, "Es ist ein Fehler bei der Token-Abfrage aufgetreten.");
				setConnectionJoinResult(uniqueId, false);
				return;
			}
			
			OIDCTokenResponse oidcTokenResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
			OIDCTokens tokens = oidcTokenResponse.getOIDCTokens();
			JWT idToken = tokens.getIDToken();
			UserStorage user = UserStorage.get(uniqueId);
			
			if (validateIDToken(idToken, user.nonce()))
			{
				user.updateTokens(tokens);
				sendResponse(exchange, "Authentifizierung erfolgreich! Du kannst den Server jetzt betreten und dieses Fenster schließen.");
				setConnectionJoinResult(uniqueId, true);
			}
			else
			{
				error(exchange, "Ungültiger ID-Token.");
				setConnectionJoinResult(uniqueId, false);
			}
		}
		catch (Exception e)
		{
			error("Bei der Behandlung einer Callback-Anfrage ist ein schwerwiegender Fehler aufgetreten.");
			e.printStackTrace();
		}
	}
	
	private TokenResponse requestToken(AuthorizationCode code) throws Exception
	{
		TokenRequest tokenRequest = new TokenRequest(providerMetadata.getTokenEndpointURI(), new ClientSecretBasic(configProvider.idp().clientId(), configProvider.idp().clientSecret()), new AuthorizationCodeGrant(code, configProvider.general().callbackUri(), codeVerifier), configProvider.idp().scope());
		
		HTTPResponse httpResponse = tokenRequest.toHTTPRequest().send();
		
		return OIDCTokenResponseParser.parse(httpResponse);
	}
	
	private boolean validateIDToken(JWT idToken, Nonce nonce)
	{
		try
		{
			IDTokenValidator validator = new IDTokenValidator(configProvider.idp().issuer(), configProvider.idp().clientId(), JWSAlgorithm.RS256, providerMetadata.getJWKSetURI().toURL());
			
			validator.validate(idToken, nonce);
			
			return true;
		}
		catch (JOSEException | BadJOSEException | MalformedURLException e)
		{
			error("Validierung eines ID-Tokens ist fehlgeschlagen.");
			return false;
		}
	}
	
	private void sendResponse(HttpExchange exchange, String msg) throws IOException
	{
		exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
		byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream os = exchange.getResponseBody())
		{
			os.write(bytes);
		}
	}
	
	public void error(String message)
	{
		getComponentLogger().error(message);
	}
	
	public void error(HttpExchange exchange, String message)
	{
		UUID uuid = UUID.randomUUID();
		String spacer = "========== [ERROR: " + uuid + "] ==========";
		try
		{
			sendResponse(exchange, "Es ist ein Fehler aufgetreten. Fehler-Code: " + uuid);
		}
		catch (IOException e)
		{
			// Ignored
		}
		error(spacer);
		error(message);
		error(spacer);
	}
}
